package io.coodoo.framework.listing.boundary;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;

import io.coodoo.framework.listing.boundary.annotation.ListingFilterIgnore;
import io.coodoo.framework.listing.control.ListingConfig;

/**
 * Listing query parameters and settings
 * 
 * @author coodoo GmbH (coodoo.io)
 */
public class ListingParameters {

    @QueryParam("index")
    private Integer index;

    @QueryParam("page")
    private Integer page;

    @QueryParam("limit")
    private Integer limit;

    @QueryParam("sort")
    private String sortAttribute;

    @QueryParam("filter")
    private String filter;

    private Map<String, String> filterAttributes = new HashMap<>();

    private ListingPredicate predicate;

    @Context
    private UriInfo uriInfo;

    public ListingParameters() {}

    /**
     * @param page number for pagination
     * @param limit of results per page for pagination
     * @param sortAttribute name of the attribute the result list gets sorted by
     */
    public ListingParameters(Integer page, Integer limit, String sortAttribute) {
        super();
        this.page = page;
        this.limit = limit;
        this.sortAttribute = sortAttribute;
    }

    /**
     * @return index for pagination (position in whole list where current pagination page starts)
     */
    public Integer getIndex() {
        // the index can be calculated if page and limit are given
        if (index == null && page != null) {
            return (page - 1) * getLimit(); // getLimit() finds the given limit or takes the default limit as fallback
        }
        // could not calculate the index -> use default
        if (index == null || index < 0) {
            return ListingConfig.DEFAULT_INDEX;
        }
        return index;
    }

    /**
     * @param index for pagination (position in whole list where current pagination page starts)
     */
    public void setIndex(Integer index) {
        this.index = index;
    }

    /**
     * @return page number for pagination
     */
    public Integer getPage() {
        // current page can be calculated if limit and index are given
        if (page == null && limit != null && index != null && index > 0 && limit > 0) {
            return index % limit == 0 ? index / limit : (index / limit) + 1;
        }
        // no valid page number given -> use default
        if (page == null || page < 1) {
            return ListingConfig.DEFAULT_PAGE;
        }
        return page;
    }

    /**
     * @param page number for pagination
     */
    public void setPage(Integer page) {
        this.page = page;
    }

    /**
     * @return limit of results per page for pagination
     */
    public Integer getLimit() {
        // no limit given -> use default
        if (limit == null) {
            return ListingConfig.DEFAULT_LIMIT;
        }
        return limit;
    }

    /**
     * @param limit of results per page for pagination (use 0 to get the whole list)
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * @return name of the attribute the result list gets sorted by (prefix '+' for ascending (default) or '-' for descending order. E.g. '-creationDate')
     */
    public String getSortAttribute() {
        return sortAttribute;
    }

    /**
     * @param sortAttribute name of the attribute the result list gets sorted by (prefix '+' for ascending (default) or '-' for descending order. E.g.
     *        '-creationDate')
     */
    public void setSortAttribute(String sortAttribute) {
        this.sortAttribute = sortAttribute;
    }

    /**
     * @return global filter string that is applied to all attributes
     */
    public String getFilter() {
        return StringUtils.trimToNull(filter);
    }

    /**
     * @param filter global filter string that is applied to all attributes (use {@link ListingFilterIgnore} on an attribute in the target entity to spare it
     *        out)
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * Adds a filter to a specific attribute
     * 
     * @param attribute attribute name
     * @param value filter value
     */
    public void addFilterAttributes(String attribute, String value) {
        try {
            filterAttributes.put(attribute, URLDecoder.decode(value, ListingConfig.URI_CHARACTER_ENCODING));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Map of attribute specific filters
     */
    public Map<String, String> getFilterAttributes() {

        // collects filter from URI if given
        if (uriInfo != null) {

            MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters(true);

            for (Map.Entry<String, List<String>> queryParameter : queryParameters.entrySet()) {

                String filterAttribute = queryParameter.getKey();
                if (StringUtils.isBlank(filterAttribute) || !filterAttribute.startsWith("filter-")) {
                    continue;
                }
                filterAttribute = filterAttribute.substring("filter-".length(), filterAttribute.length());

                String filterValue = StringUtils.trimToNull(queryParameter.getValue().get(0));
                if (filterValue != null) {
                    try {
                        filterAttributes.put(filterAttribute, URLDecoder.decode(filterValue, ListingConfig.URI_CHARACTER_ENCODING));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return filterAttributes;
    }

    /**
     * @return root of custom filter condition tree
     */
    public ListingPredicate getPredicate() {
        return predicate;
    }

    /**
     * @param predicate root of custom filter condition tree
     */
    public void setPredicate(ListingPredicate predicate) {
        this.predicate = predicate;
    }

    public UriInfo getUriInfo() {
        return uriInfo;
    }

    public void setUriInfo(UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

}
