package io.coodoo.framework.listing.control;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listing configuration
 * 
 * @author coodoo GmbH (coodoo.io)
 */
@ApplicationScoped
public class ListingConfig {

    private static final String BLANC = " ";

    private static Logger log = LoggerFactory.getLogger(ListingConfig.class);

    /**
     * Default index for pagination
     */
    public static int DEFAULT_INDEX = 0;

    /**
     * Default current page number for pagination
     */
    public static int DEFAULT_PAGE = 1;

    /**
     * Default limit of results per page for pagination
     */
    public static int DEFAULT_LIMIT = 10;

    /**
     * If this key is present in filterAttributes map, the attributes gets disjuncted (default is conjunction)
     */
    public static String FILTER_TYPE_DISJUNCTION = "Filter-Type-Disjunction";

    /**
     * Limit on OR operator separated predicated to handle it in an IN statement
     */
    public static int OR_LIMIT = 10;

    /**
     * NOT operator
     */
    public static String OPERATOR_NOT = "!";

    /**
     * NOT operator as word
     */
    private static String OPERATOR_NOT_WORD_BLANK = "NOT";
    public static String OPERATOR_NOT_WORD = OPERATOR_NOT_WORD_BLANK + BLANC;

    /**
     * OR operator
     */
    public static String OPERATOR_OR = "|";

    /**
     * OR operator as word
     */
    private static String OPERATOR_OR_WORD_BLANK = "OR";
    public static String OPERATOR_OR_WORD = BLANC + OPERATOR_OR_WORD_BLANK + BLANC;

    /**
     * AND operator
     */
    public static String OPERATOR_AND = "&";

    /**
     * AND operator as word
     */
    private static String OPERATOR_AND_WORD_BLANK = "AND";
    public static String OPERATOR_AND_WORD = BLANC + OPERATOR_AND_WORD_BLANK + BLANC;

    /**
     * LIKE operator
     */
    public static String OPERATOR_LIKE = "~";

    /**
     * LIKE operator as word
     */
    private static String OPERATOR_LIKE_WORD_BLANK = "LIKE";
    public static String OPERATOR_LIKE_WORD = OPERATOR_LIKE_WORD_BLANK + BLANC;

    /**
     * LESS THAN operator
     */
    public static String OPERATOR_LT = "<";

    /**
     * LESS THAN operator as word
     */
    private static String OPERATOR_LT_WORD_BLANK = "LT";
    public static String OPERATOR_LT_WORD = OPERATOR_LT_WORD_BLANK + BLANC;

    /**
     * GREATER THAN operator
     */
    public static String OPERATOR_GT = ">";

    /**
     * GREATER THAN operator as word
     */
    private static String OPERATOR_GT_WORD_BLANK = "GT";
    public static String OPERATOR_GT_WORD = OPERATOR_GT_WORD_BLANK + BLANC;

    /**
     * TO operator
     */
    public static String OPERATOR_TO = "-";

    /**
     * TO operator as word
     */
    private static String OPERATOR_TO_WORD_BLANK = "TO";
    public static String OPERATOR_TO_WORD = BLANC + OPERATOR_TO_WORD_BLANK + BLANC;

    /**
     * NULL operator
     */
    public static String OPERATOR_NULL = "NULL";

    /**
     * Wildcard many
     */
    public static String WILDCARD_MANY = "*";

    /**
     * Wildcard one
     */
    public static String WILDCARD_ONE = "?";

    /**
     * ASC Sort direction operator
     */
    public static String SORT_ASC = "+";

    /**
     * DESC Sort direction operator
     */
    public static String SORT_DESC = "-";

    /**
     * Filter value to match boolean true
     */
    public static String BOOLEAN_TRUE = "true";

    /**
     * Filter value to match boolean false
     */
    public static String BOOLEAN_FALSE = "false";

    /**
     * Decode URL encoding
     */
    public static boolean URI_DECODE = false;

    /**
     * URI character encoding
     */
    public static String URI_CHARACTER_ENCODING = "UTF8";

    /**
     * ZoneId Object time zone for LocalDateTime instance creation. Default is UTC
     */
    public static String TIME_ZONE = "UTC";
    public static ZoneId ZONE_ID = ZoneId.of(TIME_ZONE);

    /**
     * The fetch size or better the option <code>org.hibernate.fetchSize</code> tells the JDBC driver how many rows to return in one chunk, for large
     * queries.<br>
     * If this is set to <code>0</code> (default) it will not be applied. <br>
     * <br>
     * <i><code>org.hibernate.fetchSize</code> will do nothing if your driver does not support it!</i> <br>
     * <br>
     * Say you want 1000 rows. If you set the fetch size to 100, the database will return 100, then another 100 when you want more, and so on. <br>
     * <br>
     * You can also set this globally by adding <code>&lt;property name="hibernate.jdbc.fetch_size" value="100"/&gt; </code> to your options in
     * <code>persitence.xml</code>
     */
    public static int FETCHSIZE = 0;

    /**
     * Name of the (optional) listing property file
     */
    private static final String listingPropertiesFilename = "coodoo.listing.properties";

    static Properties properties = new Properties();

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        loadProperties();
    }

    private static void loadProperties() {
        InputStream inputStream = null;
        try {
            inputStream = ListingConfig.class.getClassLoader().getResourceAsStream(listingPropertiesFilename);

            if (inputStream != null) {

                properties.load(inputStream);
                log.info("Reading {}", listingPropertiesFilename);

                DEFAULT_INDEX = loadProperty(DEFAULT_INDEX, "coodoo.listing.default.index");
                DEFAULT_PAGE = loadProperty(DEFAULT_PAGE, "coodoo.listing.default.page");
                DEFAULT_LIMIT = loadProperty(DEFAULT_LIMIT, "coodoo.listing.default.limit");

                FILTER_TYPE_DISJUNCTION = loadProperty(FILTER_TYPE_DISJUNCTION, "coodoo.listing.filter.type.disjunction");
                OR_LIMIT = loadProperty(OR_LIMIT, "coodoo.listing.or.limit");

                OPERATOR_NOT = loadProperty(OPERATOR_NOT, "coodoo.listing.operator.not");
                OPERATOR_NOT_WORD_BLANK = loadProperty(OPERATOR_NOT_WORD_BLANK, "coodoo.listing.operator.not.word");
                OPERATOR_OR = loadProperty(OPERATOR_OR, "coodoo.listing.operator.or");
                OPERATOR_OR_WORD_BLANK = loadProperty(OPERATOR_OR_WORD_BLANK, "coodoo.listing.operator.or.word");
                OPERATOR_LIKE = loadProperty(OPERATOR_LIKE, "coodoo.listing.operator.like");
                OPERATOR_LIKE_WORD_BLANK = loadProperty(OPERATOR_LIKE_WORD_BLANK, "coodoo.listing.operator.like.word");
                OPERATOR_LT = loadProperty(OPERATOR_LT, "coodoo.listing.operator.lt");
                OPERATOR_LT_WORD_BLANK = loadProperty(OPERATOR_LT_WORD_BLANK, "coodoo.listing.operator.lt.word");
                OPERATOR_GT = loadProperty(OPERATOR_GT, "coodoo.listing.operator.gt");
                OPERATOR_GT_WORD_BLANK = loadProperty(OPERATOR_GT_WORD_BLANK, "coodoo.listing.operator.gt.word");
                OPERATOR_TO = loadProperty(OPERATOR_TO, "coodoo.listing.operator.to");
                OPERATOR_TO_WORD_BLANK = loadProperty(OPERATOR_TO_WORD_BLANK, "coodoo.listing.operator.to.word");
                OPERATOR_NULL = loadProperty(OPERATOR_NULL, "coodoo.listing.operator.null");

                WILDCARD_MANY = loadProperty(WILDCARD_MANY, "coodoo.listing.wildcard.many");
                WILDCARD_ONE = loadProperty(WILDCARD_ONE, "coodoo.listing.wildcard.one");

                SORT_ASC = loadProperty(SORT_ASC, "coodoo.listing.sort.asc");
                SORT_DESC = loadProperty(SORT_DESC, "coodoo.listing.sort.desc");

                BOOLEAN_TRUE = loadProperty(BOOLEAN_TRUE, "coodoo.listing.boolean.true");
                BOOLEAN_FALSE = loadProperty(BOOLEAN_FALSE, "coodoo.listing.boolean.false");

                URI_DECODE = loadProperty(URI_DECODE, "coodoo.listing.uri.decode");
                URI_CHARACTER_ENCODING = loadProperty(URI_CHARACTER_ENCODING, "coodoo.listing.uricharacterencoding");

                FETCHSIZE = loadProperty(FETCHSIZE, "coodoo.listing.fetchSize");
            }
        } catch (IOException e) {
            log.info("Couldn't read {}!", listingPropertiesFilename, e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                log.warn("Couldn't close {}!", listingPropertiesFilename, e);
            }
        }
    }

    private static String loadProperty(String value, String key) {

        String property = properties.getProperty(key);
        if (property == null) {
            return value;
        }
        log.info("Listing Property {} loaded: {}", key, property);
        return property;
    }

    private static int loadProperty(int value, String key) {
        String property = properties.getProperty(key);
        if (property != null) {
            try {
                log.info("Listing Property {} loaded: {}", key, property);
                return Integer.valueOf(property).intValue();
            } catch (NumberFormatException e) {
                log.warn("Listing Property {} value invalid: {}", key, property);
            }
        }
        return value;
    }

    private static boolean loadProperty(boolean value, String key) {
        String property = properties.getProperty(key);
        if (property != null) {
            log.info("Listing Property {} loaded: {}", key, property);
            Boolean booleanValue = Boolean.valueOf(property);
            if (booleanValue != null) {
                return booleanValue;
            }
        }
        return value;
    }
}
