package org.skife.config;

import java.net.URI;

interface CoercionConfig
{
    @Config("the-url")
    URI getURI();
}
