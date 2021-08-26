package org.skife.config;

class Config2
{
    public int invocationCount = 0;

    public int getInvocationCount()
    {
        return invocationCount;
    }

    // optional w/ default value
    @Config("option")
    public String getOption()
    {
        ++invocationCount;
        return "default";
    }
}