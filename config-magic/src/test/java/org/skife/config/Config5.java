package org.skife.config;

public interface Config5
{
    @Config("foo")
    String getFoo();

    @Config("bar")
    int getBar();
}
