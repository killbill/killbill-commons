package org.skife.config;

import java.util.Properties;

public class Props
{
    public static Properties of(String key, String value)
    {
        Properties props = new Properties();
        props.put(key, value);
        return props;
    }
}
