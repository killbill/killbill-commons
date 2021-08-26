package org.skife.config;

public interface EnumeratedConfig1
{
    @Config("option.${type}")
    @Default("default")
    public String getStringOption(@Param("type") ConfigEnum type);

    @Config("another-option.${type}.${s}")
    @Default("default")
    public String getStringOption2Types(@Param("type") ConfigEnum type, @Param("s") String selector);
}
