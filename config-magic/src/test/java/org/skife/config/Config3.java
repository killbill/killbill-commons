package org.skife.config;

interface Config3
{
    // required
    @Config("option")
    public String getOption();

    public abstract String getOption2();
}