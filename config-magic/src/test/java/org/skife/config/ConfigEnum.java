package org.skife.config;

public enum ConfigEnum
{
    ONE,
    TWO,
    THREE;

    public String toString()
    {
        return name().toLowerCase();
    }
}
