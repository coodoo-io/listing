package io.coodoo.framework.listing.boundary.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables LIKE comparison an numerical values
 * 
 * @author coodoo GmbH (coodoo.io)
 * @deprecated use {@link ListingFilterAsString}
 */
@Deprecated
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ListingLikeOnNumber {
}
