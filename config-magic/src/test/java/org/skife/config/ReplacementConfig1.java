package org.skife.config;

public interface ReplacementConfig1
{
    @Config("option.${type}")
    @Default("default")
    public String getStringOption();

    @Config("another-option.${type}.${s}")
    @Default("default")
    public String getStringOption2Types();
}