package org.skife.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If a configuration bean is created with mapped replacement values via
 * {@link ConfigurationObjectFactory#buildWithReplacements(Class, java.util.Map)},
 * this annotation designates a method which should present the provided Map.
 * The map may not be changed and is not necessarily the same instance as the original.
 * If a key is provided, the return is instead the value for that key.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConfigReplacements
{
    static final String DEFAULT_VALUE = "__%%%noValue%%%__";

    /**
     * The key to look up in the replacement map, if any.
     */
    String value() default DEFAULT_VALUE;
}
