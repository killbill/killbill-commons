package org.skife.config;

import java.lang.reflect.Method;

public class ExactMatchEnumCoercible implements Coercible<Object>
{
    public Coercer<Object> accept(Class<?> clazz)
    {
        if (!clazz.isEnum()) {
            return null;
        }
        try {
            final Method m = clazz.getMethod("valueOf", String.class);
            return new Coercer<Object>()
            {
                public Object coerce(String value)
                {
                    if (value == null) {
                        return null;
                    }
                    try {
                        return m.invoke(null, value);
                    }
                    catch (Exception e) {
                        throw DefaultCoercibles.convertException(e);
                    }
                }
            };
        }
        catch (NoSuchMethodException e) {
            throw new IllegalStateException("<EnumType>.valueOf(String) missing! World broken!", e);
        }

    }
}
