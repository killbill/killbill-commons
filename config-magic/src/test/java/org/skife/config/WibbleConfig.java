package org.skife.config;


interface WibbleConfig
{
    @Config("the-url")
    Wibble getWibble();
}
