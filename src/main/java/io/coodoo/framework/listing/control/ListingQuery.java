package io.coodoo.framework.listing.control;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;

import io.coodoo.framework.listing.boundary.ListingPredicate;
import io.coodoo.framework.listing.boundary.Stats;
import io.coodoo.framework.listing.boundary.Term;
import io.coodoo.framework.listing.boundary.annotation.ListingFilterAsString;

/**
 * Creates a dynamic JPA query using Criteria API considering optional fields, e.g. a filter for attributes, sorting and result limit.
 * 
 * @param <T> The target entity
 * 
 * @author coodoo GmbH (coodoo.io)
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ListingQuery<T> {

    private EntityManager entityManager;
    private CriteriaBuilder criteriaBuilder;
    private CriteriaQuery query;
    private Root<T> root;
    private Class<T> domainClass;
    private List<Predicate> whereConstraints;

    public ListingQuery(EntityManager entityManager, Class<T> domainClass) {
        this.entityManager = entityManager;
        this.domainClass = domainClass;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
        this.query = criteriaBuilder.createQuery();
        this.root = query.from(domainClass);
        this.whereConstraints = new ArrayList<>();
    }

    public ListingQuery<T> filterAllAttributes(String filter) {
        if (!StringUtils.isBlank(filter)) {

            Map<String, String> filterAttributes = new HashMap<>();
            filterAttributes.put(ListingConfig.FILTER_TYPE_DISJUNCTION, "this just enables an OR-statement for all the fields");

            ListingUtil.getFields(domainClass).forEach(field -> filterAttributes.put(field.getName(), filter));
            return filterByAttributes(filterAttributes);
        }
        return this;
    }

    public ListingQuery<T> filterByAttributes(Map<String, String> filterAttributes) {
        if (filterAttributes != null && !filterAttributes.isEmpty()) {

            ListingPredicate listingPredicate = new ListingPredicate().and();

            for (String attribute : filterAttributes.keySet()) {

                if (StringUtils.equals(ListingConfig.FILTER_TYPE_DISJUNCTION, attribute)) {
                    listingPredicate = listingPredicate.or(); // changing filter to disjunctive
                }
                String filter = filterAttributes.get(attribute);

                if (StringUtils.contains(attribute, ListingConfig.OPERATOR_OR)) {
                    // a filter can be applied on many fields, joined by a "|" (OPERATOR_OR), those get conjuncted
                    List<String> orAttributes = ListingUtil.splitOr(attribute);

                    // in order to filter conjunctive in different fields with different filters its got to be nasty...
                    if (StringUtils.contains(filter, ListingConfig.OPERATOR_AND) || StringUtils.contains(filter, ListingConfig.OPERATOR_AND_WORD)) {
                        List<String> andfilters = ListingUtil
                                        .splitAnd(filter.replaceAll(ListingUtil.escape(ListingConfig.OPERATOR_AND_WORD), ListingConfig.OPERATOR_AND));
                        // ...ok, just see it like this: WHERE (fieldA = filter1 OR fieldB = filter1) AND (fieldA = filter2 OR fieldB = filter2)
                        listingPredicate.addPredicate(new ListingPredicate().and()
                                        .predicates(andfilters.stream().map(andfilter -> new ListingPredicate().or().predicates(orAttributes.stream()
                                                        .map(orAttribute -> createListingPredicate(orAttribute, andfilter)).collect(Collectors.toList())))
                                                        .collect(Collectors.toList())));
                    } else {
                        listingPredicate.addPredicate(new ListingPredicate().or().predicates(
                                        orAttributes.stream().map(orAttribute -> createListingPredicate(orAttribute, filter)).collect(Collectors.toList())));
                    }
                } else {
                    // just one attribute for one filter
                    listingPredicate.addPredicate(createListingPredicate(attribute, filter));
                }
            }
            addToWhereConstraint(listingPredicate);
        }
        return this;
    }

    public ListingQuery<T> filterByPredicate(ListingPredicate listingPredicate) {

        addToWhereConstraint(listingPredicate);
        return this;
    }

    private void addToWhereConstraint(ListingPredicate listingPredicate) {
        if (listingPredicate != null) {

            Predicate predicate = null;
            List<ListingPredicate> filters = new ArrayList<>();
            Map<String, Field> fieldMap = ListingUtil.getFields(domainClass, true).stream().collect(Collectors.toMap(field -> field.getName(), field -> field));

            if (listingPredicate.hasPredicates()) {
                filters.addAll(listingPredicate.getPredicates());
            } else {
                filters.add(new ListingPredicate().filter(listingPredicate.getAttribute(), listingPredicate.getFilter()));
            }
            predicate = filterByPredicateTree(listingPredicate.isDisjunctive(), listingPredicate.isNegation(), filters, fieldMap);

            if (predicate != null) {
                if (listingPredicate.isNegation()) {
                    predicate = criteriaBuilder.not(predicate);
                }
                whereConstraints.add(predicate);
            }
        }
    }

    private ListingPredicate createListingPredicate(String attribute, String filter) {

        // the AND is mightier than the OR, so we check it first and in case there are some, they get put into use, replaced and this method is called
        // recursively to proceed with the OR
        if (StringUtils.contains(filter, ListingConfig.OPERATOR_AND) || StringUtils.contains(filter, ListingConfig.OPERATOR_AND_WORD)) {

            List<String> andFilters = ListingUtil.splitAnd(filter.replaceAll(ListingUtil.escape(ListingConfig.OPERATOR_AND_WORD), ListingConfig.OPERATOR_AND));
            // we don't expect as many AND-Predicates as we do for OR-Predicates, so we don't need a special case here
            return new ListingPredicate().and()
                            .predicates(andFilters.stream().map(andfilter -> createListingPredicate(attribute, andfilter)).collect(Collectors.toList()));
        }
        if (StringUtils.contains(filter, ListingConfig.OPERATOR_OR) || StringUtils.contains(filter, ListingConfig.OPERATOR_OR_WORD)) {

            List<String> orFilters = ListingUtil.splitOr(filter.replaceAll(ListingUtil.escape(ListingConfig.OPERATOR_OR_WORD), ListingConfig.OPERATOR_OR));
            if (orFilters.size() > ListingConfig.OR_LIMIT) {
                // Too many OR-Predicates can cause a stack overflow, so higher numbers get processed in an IN statement
                return new ListingPredicate().in().filter(attribute,
                                filter.replaceAll(ListingUtil.escape(ListingConfig.OPERATOR_OR_WORD), ListingConfig.OPERATOR_OR));
            }
            return new ListingPredicate().or()
                            .predicates(orFilters.stream().map(orfilter -> createListingPredicateFilter(attribute, orfilter)).collect(Collectors.toList()));
        }
        return createListingPredicateFilter(attribute, filter);
    }

    private ListingPredicate createListingPredicateFilter(String attribute, String filter) {
        if (filter.startsWith(ListingConfig.OPERATOR_NOT)) {
            return new ListingPredicate().not().filter(attribute, filter.replaceFirst(ListingConfig.OPERATOR_NOT, ""));
        }
        if (filter.startsWith(ListingConfig.OPERATOR_NOT_WORD)) {
            return new ListingPredicate().not().filter(attribute, filter.replaceFirst(ListingConfig.OPERATOR_NOT_WORD, ""));
        }
        return new ListingPredicate().filter(attribute, filter);
    }

    private Predicate filterByPredicateTree(boolean disjunctive, boolean negation, List<ListingPredicate> listingPredicates, Map<String, Field> fieldMap) {

        if (listingPredicates != null && !listingPredicates.isEmpty()) {

            List<Predicate> predicates = new ArrayList<>();
            for (ListingPredicate listingPredicate : listingPredicates) {

                Predicate predicate = null;
                if (listingPredicate.hasPredicates()) {

                    // process child predicates
                    predicate = filterByPredicateTree(listingPredicate.isDisjunctive(), listingPredicate.isNegation(), listingPredicate.getPredicates(),
                                    fieldMap);

                } else if (StringUtils.isNoneEmpty(listingPredicate.getAttribute()) && StringUtils.isNoneEmpty(listingPredicate.getFilter())) {

                    if (!fieldMap.containsKey(listingPredicate.getAttribute())) {
                        continue; // given fieldName does not exist in domainClass
                    }
                    // add predicate
                    if (listingPredicate.isIn()) {
                        predicate = createInPredicate(ListingUtil.splitOr(listingPredicate.getFilter()), fieldMap.get(listingPredicate.getAttribute()));
                    } else {
                        predicate = createPredicate(listingPredicate.getFilter(), fieldMap.get(listingPredicate.getAttribute()));
                    }
                }
                if (predicate != null) {

                    if (listingPredicate.isNegation()) {
                        predicate = criteriaBuilder.not(predicate);
                    }
                    predicates.add(predicate);
                }
            }
            if (!predicates.isEmpty()) {
                Predicate predicate = null;
                if (disjunctive) {
                    predicate = criteriaBuilder.or(predicates.toArray(new Predicate[predicates.size()]));
                } else {
                    predicate = criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
                }
                if (negation) {
                    return criteriaBuilder.not(predicate);
                }
                return predicate;
            }
        }
        return null;
    }

    private Predicate createPredicate(String filter, Field field) {

        final String fieldName = field.getName();
        String simpleName = field.getType().getSimpleName();

        // Nulls
        if (ListingUtil.matches(filter, ListingConfig.OPERATOR_NULL)) {
            return criteriaBuilder.isNull(root.get(fieldName));
        }

        if (field.isAnnotationPresent(ListingFilterAsString.class)) {
            simpleName = String.class.getSimpleName();
        }

        switch (simpleName) {

            case "String":

                // quoted values needs an exact match
                if (ListingUtil.isQuoted(filter)) {
                    return criteriaBuilder.equal(root.get(fieldName).as(String.class), ListingUtil.removeQuotes(filter));
                }
                return criteriaBuilder.like(criteriaBuilder.lower(root.get(fieldName).as(String.class)), ListingUtil.likeValue(filter));

            // case "LocalDate": Doesn't work in JPA 2.0...
            case "LocalDateTime":

                if (ListingUtil.validDate(filter)) {
                    return criteriaBuilder.between(root.get(fieldName), ListingUtil.parseDateTime(filter, false), ListingUtil.parseDateTime(filter, true));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validDate(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), ListingUtil.parseDateTime(ltFilter, false));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validDate(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), ListingUtil.parseDateTime(gtFilter, true));
                    }
                }
                Matcher dateTimeRange = Pattern.compile(ListingUtil.rangePatternDate()).matcher(filter);
                if (dateTimeRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), ListingUtil.parseDateTime(dateTimeRange.group(1), false),
                                    ListingUtil.parseDateTime(dateTimeRange.group(8), true));
                }
                break;

            case "Date":

                if (ListingUtil.validDate(filter)) {
                    return criteriaBuilder.between(root.get(fieldName), ListingUtil.parseDate(filter, false), ListingUtil.parseDate(filter, true));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validDate(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), ListingUtil.parseDate(ltFilter, false));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validDate(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), ListingUtil.parseDate(gtFilter, true));
                    }
                }
                Matcher dateRange = Pattern.compile(ListingUtil.rangePatternDate()).matcher(filter);
                if (dateRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), ListingUtil.parseDate(dateRange.group(1), false),
                                    ListingUtil.parseDate(dateRange.group(8), true));
                }
                break;

            case "Long":
            case "long":

                if (ListingUtil.validLong(filter)) {
                    return criteriaBuilder.equal(root.get(fieldName), Long.valueOf(filter));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LIKE) || filter.startsWith(ListingConfig.OPERATOR_LIKE_WORD)) {
                    String likeFilter = filter.replace(ListingConfig.OPERATOR_LIKE, "").replace(ListingConfig.OPERATOR_LIKE_WORD, "");
                    if (ListingUtil.validLong(likeFilter)) {
                        return criteriaBuilder.like(root.get(fieldName).as(String.class), ListingUtil.likeValue(likeFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validLong(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), Long.valueOf(ltFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validLong(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), Long.valueOf(gtFilter));
                    }
                }
                Matcher longRange = Pattern.compile(ListingUtil.rangePatternLong()).matcher(filter);
                if (longRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), Long.valueOf(longRange.group(1)), Long.valueOf(longRange.group(3)));
                }
                break;

            case "Integer":
            case "int":

                if (ListingUtil.validInt(filter)) {
                    return criteriaBuilder.equal(root.get(fieldName), Integer.valueOf(filter));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LIKE) || filter.startsWith(ListingConfig.OPERATOR_LIKE_WORD)) {
                    String likeFilter = filter.replace(ListingConfig.OPERATOR_LIKE, "").replace(ListingConfig.OPERATOR_LIKE_WORD, "");
                    if (ListingUtil.validInt(likeFilter)) {
                        return criteriaBuilder.like(root.get(fieldName).as(String.class), ListingUtil.likeValue(likeFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validInt(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), Integer.valueOf(ltFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validInt(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), Integer.valueOf(gtFilter));
                    }
                }
                Matcher intRange = Pattern.compile(ListingUtil.rangePatternInt()).matcher(filter);
                if (intRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), Integer.valueOf(intRange.group(1)), Integer.valueOf(intRange.group(3)));
                }
                break;

            case "Short":
            case "short":

                if (ListingUtil.validShort(filter)) {
                    return criteriaBuilder.equal(root.get(fieldName), Short.valueOf(filter));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LIKE) || filter.startsWith(ListingConfig.OPERATOR_LIKE_WORD)) {
                    String likeFilter = filter.replace(ListingConfig.OPERATOR_LIKE, "").replace(ListingConfig.OPERATOR_LIKE_WORD, "");
                    if (ListingUtil.validShort(likeFilter)) {
                        return criteriaBuilder.like(root.get(fieldName).as(String.class), ListingUtil.likeValue(likeFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validShort(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), Short.valueOf(ltFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validShort(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), Short.valueOf(gtFilter));
                    }
                }
                Matcher shortRange = Pattern.compile(ListingUtil.rangePatternShort()).matcher(filter);
                if (shortRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), Short.valueOf(shortRange.group(1)), Short.valueOf(shortRange.group(3)));
                }
                break;

            case "Float":
            case "float":

                if (ListingUtil.validFloat(filter)) {
                    return criteriaBuilder.equal(root.get(fieldName), toFloat(filter));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LIKE) || filter.startsWith(ListingConfig.OPERATOR_LIKE_WORD)) {
                    String likeFilter = filter.replace(ListingConfig.OPERATOR_LIKE, "").replace(ListingConfig.OPERATOR_LIKE_WORD, "");
                    if (ListingUtil.validFloat(likeFilter)) {
                        return criteriaBuilder.like(root.get(fieldName).as(String.class), ListingUtil.likeValue(likeFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validFloat(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), toFloat(ltFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validFloat(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), toFloat(gtFilter));
                    }
                }
                Matcher floatRange = Pattern.compile(ListingUtil.rangePatternFloat()).matcher(filter);
                if (floatRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), toFloat(floatRange.group(1)), toFloat(floatRange.group(3)));
                }
                break;

            case "Double":
            case "double":

                if (ListingUtil.validDouble(filter)) {
                    return criteriaBuilder.equal(root.get(fieldName), toDouble(filter));
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LIKE) || filter.startsWith(ListingConfig.OPERATOR_LIKE_WORD)) {
                    String likeFilter = filter.replace(ListingConfig.OPERATOR_LIKE, "").replace(ListingConfig.OPERATOR_LIKE_WORD, "");
                    if (ListingUtil.validDouble(likeFilter)) {
                        return criteriaBuilder.like(root.get(fieldName).as(String.class), ListingUtil.likeValue(likeFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_LT) || filter.startsWith(ListingConfig.OPERATOR_LT_WORD)) {
                    String ltFilter = filter.replace(ListingConfig.OPERATOR_LT, "").replace(ListingConfig.OPERATOR_LT_WORD, "");
                    if (ListingUtil.validDouble(ltFilter)) {
                        return criteriaBuilder.lessThan(root.get(fieldName), toDouble(ltFilter));
                    }
                }
                if (filter.startsWith(ListingConfig.OPERATOR_GT) || filter.startsWith(ListingConfig.OPERATOR_GT_WORD)) {
                    String gtFilter = filter.replace(ListingConfig.OPERATOR_GT, "").replace(ListingConfig.OPERATOR_GT_WORD, "");
                    if (ListingUtil.validDouble(gtFilter)) {
                        return criteriaBuilder.greaterThan(root.get(fieldName), toDouble(gtFilter));
                    }
                }
                Matcher doubleRange = Pattern.compile(ListingUtil.rangePatternDouble()).matcher(filter);
                if (doubleRange.find()) {
                    return criteriaBuilder.between(root.get(fieldName), toDouble(doubleRange.group(1)), toDouble(doubleRange.group(3)));
                }
                break;

            case "Boolean":
            case "boolean":

                if (ListingConfig.BOOLEAN_TRUE.equalsIgnoreCase(filter) || ListingConfig.BOOLEAN_FALSE.equalsIgnoreCase(filter)) {

                    Boolean booleanValue = ListingConfig.BOOLEAN_TRUE.equalsIgnoreCase(filter);
                    return criteriaBuilder.equal(root.get(fieldName), booleanValue);
                }
                break;

            default:

                // Enum
                if (field.getType().isEnum()) {

                    // quoted values needs an exact match
                    if (ListingUtil.isQuoted(filter)) {
                        try {
                            Enum enumValue = Enum.valueOf((Class<Enum>) field.getType(), ListingUtil.removeQuotes(filter));
                            return criteriaBuilder.equal(root.get(fieldName), enumValue);
                        } catch (IllegalArgumentException e) {
                        }
                    }

                    Predicate possibleEnumValues = criteriaBuilder.disjunction();
                    for (Object enumValue : field.getType().getEnumConstants()) {
                        if (enumValue.toString().toUpperCase().contains(((String) filter).toUpperCase())) {
                            Predicate possibleEnumValue = criteriaBuilder.equal(root.get(fieldName), enumValue);
                            possibleEnumValues = criteriaBuilder.or(possibleEnumValues, possibleEnumValue);
                        }
                    }
                    return criteriaBuilder.and(possibleEnumValues);
                }
                break;
        }
        return null;
    }

    private Predicate createInPredicate(List<String> inList, Field field) {

        List<?> list = null;

        switch (field.getType().getSimpleName()) {

            case "String":
                list = inList;
                break;
            case "Long":
            case "long":
                list = inList.stream().filter(x -> ListingUtil.validLong(x)).map(Long::valueOf).collect(Collectors.toList());
                break;
            case "Integer":
            case "int":
                list = inList.stream().filter(x -> ListingUtil.validInt(x)).map(Integer::valueOf).collect(Collectors.toList());
                break;
            case "Short":
            case "short":
                list = inList.stream().filter(x -> ListingUtil.validShort(x)).map(Short::valueOf).collect(Collectors.toList());
                break;
            case "Float":
            case "float":
                list = inList.stream().filter(x -> ListingUtil.validFloat(x)).map(x -> toFloat(x)).collect(Collectors.toList());
                break;
            case "Double":
            case "double":
                list = inList.stream().filter(x -> ListingUtil.validDouble(x)).map(x -> toDouble(x)).collect(Collectors.toList());
                break;
            default:
                // Enum
                if (field.getType().isEnum()) {
                    List<Enum> inListEnum = new ArrayList<Enum>();
                    for (String enumString : inList) {
                        if (ListingUtil.isQuoted(enumString)) {
                            enumString = ListingUtil.removeQuotes(enumString);
                        }
                        try {
                            inListEnum.add(Enum.valueOf((Class<Enum>) field.getType(), enumString));
                        } catch (IllegalArgumentException e) {
                        }
                    }
                    list = inListEnum;
                }
                break;
        }
        if (list != null && !list.isEmpty()) {
            return criteriaBuilder.isTrue(root.get(field.getName()).in(list));
        }
        return null;
    }

    private Double toDouble(String value) {
        return Double.valueOf(value.replace(",", "."));
    }

    private Float toFloat(String value) {
        return Float.valueOf(value.replace(",", "."));
    }

    public ListingQuery<T> filter(String filter, String... attributes) {
        if (!StringUtils.isBlank(filter)) {
            filter = ListingUtil.likeValue(filter);
            List<Predicate> predicates = new ArrayList<>();
            for (String attribute : attributes) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(attribute)), filter));
            }
            Predicate filterConstraint = criteriaBuilder.or(predicates.toArray(new Predicate[predicates.size()]));
            whereConstraints.add(filterConstraint);
        }
        return this;
    }

    public ListingQuery<T> addIsNullConstraint(String attribute) {
        whereConstraints.add(criteriaBuilder.isNull(root.get(attribute)));
        return this;
    }

    public ListingQuery<T> addEqualsConstraint(String attribute, Enum value) {
        whereConstraints.add(criteriaBuilder.equal(root.get(attribute), value));
        return this;
    }

    public ListingQuery<T> addEqualsNotConstraint(String attribute, Enum value) {
        whereConstraints.add(criteriaBuilder.notEqual(root.get(attribute), value));
        return this;
    }

    public ListingQuery<T> sort(String attribute) {

        if (attribute != null && !attribute.isEmpty()) {

            String[] attributes = attribute.trim().split(";");

            if (attributes.length == 1) {
                query.orderBy(getOrder(attribute));
            } else {
                Order[] orders = new Order[attributes.length];
                for (int i = 0; i < attributes.length; i++) {
                    orders[i] = getOrder(attributes[i]);
                }
                query.orderBy(orders);
            }
        }
        return this;
    }

    private Order getOrder(String attribute) {

        String sort = attribute.trim();

        if (sort.startsWith(ListingConfig.SORT_DESC)) {
            sort = sort.substring(ListingConfig.SORT_DESC.length());
        }
        if (sort.startsWith(ListingConfig.SORT_ASC)) {
            sort = sort.substring(ListingConfig.SORT_ASC.length());
        }
        if (attribute.startsWith(ListingConfig.SORT_DESC)) {
            return criteriaBuilder.desc(root.get(sort));
        }
        return criteriaBuilder.asc(root.get(sort));
    }

    public CriteriaQuery<T> getQuery() {
        query.select(root);
        query.where(criteriaBuilder.and(whereConstraints.toArray(new Predicate[whereConstraints.size()])));
        return query;
    }

    public CriteriaQuery<Long> getQueryForCount() {
        query.select(criteriaBuilder.count(root));
        query.where(criteriaBuilder.and(whereConstraints.toArray(new Predicate[whereConstraints.size()])));
        return query;
    }

    public List<T> list() {
        return list(null, null);
    }

    public List<T> list(Integer startPosition, Integer limit) {
        TypedQuery<T> typedQuery = entityManager.createQuery(this.getQuery());
        if (startPosition != null) {
            typedQuery.setFirstResult(startPosition);
        }
        if (limit != null && limit > 0) {
            typedQuery.setMaxResults(limit);
        }
        if (ListingConfig.FETCHSIZE != 0) {
            typedQuery.setHint("org.hibernate.fetchSize", ListingConfig.FETCHSIZE);
        }
        return typedQuery.getResultList();
    }

    public Long count() {
        TypedQuery<Long> typedQuery = this.entityManager.createQuery(this.getQueryForCount());
        return typedQuery.getSingleResult();
    }

    public Stats getStats(String attribute, String operation) {

        List<Expression> operations = new ArrayList<>();
        switch (operation) {
            case "count":
                operations.add(criteriaBuilder.count(root.get(attribute)));
                break;
            case "min":
                operations.add(criteriaBuilder.min(root.get(attribute)));
                break;
            case "max":
                operations.add(criteriaBuilder.max(root.get(attribute)));
                break;
            case "avg":
                operations.add(criteriaBuilder.avg(root.get(attribute)));
                break;
            case "sum":
                operations.add(criteriaBuilder.sum(root.get(attribute)));
                break;
            default:
                operations.add(criteriaBuilder.count(root.get(attribute)));
                operations.add(criteriaBuilder.min(root.get(attribute)));
                operations.add(criteriaBuilder.max(root.get(attribute)));
                operations.add(criteriaBuilder.avg(root.get(attribute)));
                operations.add(criteriaBuilder.sum(root.get(attribute)));
                break;
        }
        query.multiselect(operations);
        query.where(criteriaBuilder.and(whereConstraints.toArray(new Predicate[whereConstraints.size()])));

        TypedQuery typedQuery = this.entityManager.createQuery(query);
        typedQuery.setMaxResults(1);

        Stats stats = new Stats();
        switch (operation) {
            case "count":
                stats.setCount((Long) typedQuery.getSingleResult());
                break;
            case "min":
                stats.setMin((Number) typedQuery.getSingleResult());
                break;
            case "max":
                stats.setMax((Number) typedQuery.getSingleResult());
                break;
            case "avg":
                stats.setAvg((Double) typedQuery.getSingleResult());
                break;
            case "sum":
                stats.setSum((Number) typedQuery.getSingleResult());
                break;
            default:
                Object[] result = (Object[]) typedQuery.getSingleResult();
                stats.setCount((Long) result[0]);
                stats.setMin((Number) result[1]);
                stats.setMax((Number) result[2]);
                stats.setAvg((Double) result[3]);
                stats.setSum((Number) result[4]);
                break;
        }
        return stats;
    }

    public Map<String, Stats> getStatsMap(Map<String, String> statsAttributes) {

        Map<String, Stats> stats = new HashMap<>();
        if (statsAttributes != null && statsAttributes.size() > 0) {

            Map<String, Field> fields = ListingUtil.getFields(domainClass).stream().collect(Collectors.toMap(f -> f.getName(), f -> f));
            for (Map.Entry<String, String> statsAttribute : statsAttributes.entrySet()) {

                String attribute = statsAttribute.getKey();
                Field field = fields.get(attribute);

                if (field != null && field.getType().getSuperclass() == Number.class) {
                    stats.put(attribute, getStats(attribute, statsAttribute.getValue()));
                }
            }
        }
        return stats;
    }

    public List<Term> getTerms(String attribute, int maxResults) {

        Expression<Long> countExpression = criteriaBuilder.count(root.get(attribute));

        query.multiselect(root.get(attribute), countExpression);
        query.where(criteriaBuilder.and(whereConstraints.toArray(new Predicate[whereConstraints.size()])));
        query.groupBy(root.get(attribute));
        query.orderBy(criteriaBuilder.desc(countExpression));

        TypedQuery typedQuery = this.entityManager.createQuery(query);
        typedQuery.setMaxResults(maxResults);

        return ((List<Object[]>) typedQuery.getResultList()).stream().map(r -> new Term(r[0], (long) r[1])).collect(Collectors.toList());
    }

    public Map<String, List<Term>> getTermsMap(Map<String, String> termsAttributes) {

        Map<String, List<Term>> terms = new HashMap<>();
        if (termsAttributes != null && termsAttributes.size() > 0) {

            Map<String, Field> fields = ListingUtil.getFields(domainClass).stream().collect(Collectors.toMap(f -> f.getName(), f -> f));
            for (Map.Entry<String, String> termsAttribute : termsAttributes.entrySet()) {
                String attribute = termsAttribute.getKey();
                if (!fields.containsKey(attribute)) {
                    continue;
                }
                String value = termsAttribute.getValue();
                int maxResults = ListingConfig.DEFAULT_LIMIT;
                if (value != null && ListingUtil.validInt(value)) {
                    maxResults = Integer.valueOf(value);
                }
                terms.put(attribute, getTerms(attribute, maxResults));
            }
        }
        return terms;
    }

}
