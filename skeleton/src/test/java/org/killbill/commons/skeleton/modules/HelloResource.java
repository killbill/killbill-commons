/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.commons.skeleton.modules;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.testng.Assert;

import com.google.inject.Injector;

@Path("hello")
public class HelloResource {

    @Inject
    public HelloResource(final SomeGuiceyDependency someGuiceyDependency, final Injector guiceInjector) {
        // HelloResource is being created by HK2 but these injections come from Guice
        // (my understanding is that HK2 won't do JIT binding by default: https://javaee.github.io/hk2/getting-started.html#automatic-service-population)
        Assert.assertEquals(someGuiceyDependency, guiceInjector.getInstance(SomeGuiceyDependency.class));
    }

    @GET
    @Path("/{name}")
    public String hello(@PathParam("name") final String name) {
        return "Hello " + name;
    }
}
