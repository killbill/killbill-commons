package org.skife.config;

import java.util.Properties;

public class SimplePropertyConfigSource implements ConfigSource
{
    private final Properties props;

    public SimplePropertyConfigSource(Properties props)
    {
        this.props = props;
    }

    public String getString(String propertyName)
    {
        return props.getProperty(propertyName);
    }
}
