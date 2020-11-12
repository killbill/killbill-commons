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

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.joda.time.LocalDate;
import org.killbill.commons.metrics.TimedResource;
import org.testng.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;

@Path("hello")
@Singleton
public class HelloResource {

    private static boolean initialized = false;

    @Inject
    public HelloResource(final SomeGuiceyDependency someGuiceyDependency, final Injector guiceInjector) {
        // Verify it's indeed a Singleton
        Assert.assertFalse(initialized);
        initialized = true;

        // HelloResource is being created by HK2 but these injections come from Guice
        // (my understanding is that HK2 won't do JIT binding by default: https://javaee.github.io/hk2/getting-started.html#automatic-service-population)
        Assert.assertEquals(someGuiceyDependency, guiceInjector.getInstance(SomeGuiceyDependency.class));
    }

    @TimedResource
    @GET
    @Path("/{name}")
    public String hello(@PathParam("name") final String name) {
        return "Hello " + name;
    }

    @TimedResource
    @POST
    @Produces("application/json")
    public Map<String, ?> hello() {
        return ImmutableMap.<String, Object>of("key", "hello", "date", new LocalDate("2010-01-01"));
    }
}
