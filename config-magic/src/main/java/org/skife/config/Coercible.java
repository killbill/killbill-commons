package org.skife.config;

/**
 * Returns a Coercer to convert String values into
 * the given type. The interface accepts Class&lt;?&gt; because
 * the type can be {@link java.lang.Object}.
 */
public interface Coercible<T>
{
    Coercer<T> accept(Class<?> clazz);
}
