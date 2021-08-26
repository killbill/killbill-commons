package org.skife.config;

interface BadConfig
{
    @Config({})
    String getBadOption();
}
