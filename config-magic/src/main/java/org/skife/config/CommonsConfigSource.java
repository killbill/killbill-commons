package org.skife.config;

import org.apache.commons.configuration.Configuration;

public class CommonsConfigSource implements ConfigSource
{
    private final Configuration config;

    public CommonsConfigSource(Configuration config) {
        this.config = config;
    }

    public String getString(String propertyName)
    {
        final String [] strings = config.getStringArray(propertyName);
        if (strings == null || strings.length == 0) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            sb.append(strings[i]);
            if (i < strings.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }
}
