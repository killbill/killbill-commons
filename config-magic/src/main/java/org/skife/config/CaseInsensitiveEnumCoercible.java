package org.skife.config;

import java.util.Arrays;

/**
 * Do case insensitive string comparisons for determination of enum value matches.
 */
public class CaseInsensitiveEnumCoercible implements Coercible<Object>
{
    public Coercer<Object> accept(final Class<?> clazz)
    {
        if (!clazz.isEnum()) {
            return null;
        }

        final Enum<?>[] values;
        try {
            values = (Enum[]) clazz.getMethod("values").invoke(null);
        }
        catch (Exception e) {
            throw new IllegalStateException("World seems to be broken, unable to access <EnumType>.values() static method", e);
        }

        return new Coercer<Object>()
        {
            public Object coerce(String value)
            {
                if (value == null) {
                    return null;
                }
                for (Object o : values) {
                    if (value.equalsIgnoreCase(o.toString())) {
                        return o;
                    }
                }
                throw new IllegalStateException("No enum value of " + Arrays.toString(values) + " matches [" + value + "]");
            }
        };
    }
}
