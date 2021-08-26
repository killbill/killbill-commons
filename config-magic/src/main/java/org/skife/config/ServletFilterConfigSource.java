package org.skife.config;

import javax.servlet.FilterConfig;

import org.skife.config.ConfigSource;

/**
 * A Filter configuration based config for Config Magic.
 */
public class ServletFilterConfigSource implements ConfigSource
{
    private final FilterConfig filterConfig;

    public ServletFilterConfigSource(final FilterConfig filterConfig)
    {
        this.filterConfig = filterConfig;
    }

    public String getString(final String propertyName)
    {
        return filterConfig.getInitParameter(propertyName);
    }
}
