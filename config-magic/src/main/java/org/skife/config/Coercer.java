package org.skife.config;

/**
 * Coerces a given string value to a type.
 */
public interface Coercer<T>
{
    T coerce(String value);
}
