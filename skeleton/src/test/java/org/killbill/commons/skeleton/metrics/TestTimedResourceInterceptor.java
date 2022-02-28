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

package org.killbill.commons.skeleton.metrics;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;

import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.aopalliance.intercept.MethodInterceptor;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.jersey.spi.ExceptionMappers;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.annotation.MetricTag;
import org.killbill.commons.metrics.api.annotation.TimedResource;
import org.killbill.commons.metrics.api.Timer;
import org.killbill.commons.metrics.dropwizard.KillBillCodahaleMetricRegistry;
import org.killbill.commons.skeleton.modules.TimedInterceptionService;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

@Test(groups = "fast")
public class TestTimedResourceInterceptor {

    private MetricRegistry registry;
    private TestResource interceptedResource;

    @BeforeMethod
    public void setup() throws Exception {
        final Injector injector = Guice.createInjector(new TestResourceModule());
        registry = injector.getInstance(MetricRegistry.class);
        interceptedResource = injector.getInstance(TestResource.class);
    }

    public void testResourceWithResponse() {
        final Response response = interceptedResource.createOk();
        Assert.assertEquals(200, response.getStatus());

        final Timer timer = registry.getTimers().get("kb_resource.path.createOk.POST.2xx.200");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceSimpleTag() {
        final Response response = interceptedResource.createOk("AUTHORIZE");
        Assert.assertEquals(200, response.getStatus());

        final Timer timer = registry.getTimers().get("kb_resource.path.createOk.POST.AUTHORIZE.2xx.200");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceWithPropertyTag() {
        final Response response = interceptedResource.createOk(new Payment("PURCHASE"));
        Assert.assertEquals(201, response.getStatus());

        final Timer timer = registry.getTimers().get("kb_resource.path.createOk.POST.PURCHASE.2xx.201");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceNullTag() {
        final Response response = interceptedResource.createOk((String) null);
        Assert.assertEquals(200, response.getStatus());

        final Timer timer = registry.getTimers().get("kb_resource.path.createOk.POST.null.2xx.200");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceNullPropertyTag() {
        final Response response = interceptedResource.createOk((Payment) null);
        Assert.assertEquals(201, response.getStatus());

        final Timer timer = registry.getTimers().get("kb_resource.path.createOk.POST.null.2xx.201");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceWithNullResponse() {
        final Response response = interceptedResource.createNullResponse();
        Assert.assertNull(response);

        final Timer timer = registry.getTimers().get("kb_resource.path.createNullResponse.PUT.2xx.204");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceWithVoidResponse() {
        interceptedResource.createNullResponse();

        final Timer timer = registry.getTimers().get("kb_resource.path.createNullResponse.PUT.2xx.204");
        Assert.assertNotNull(timer);
        Assert.assertEquals(1, timer.getCount());
    }

    public void testResourceWithWebApplicationException() {
        try {
            interceptedResource.createWebApplicationException();
            Assert.fail();
        } catch (final WebApplicationException e) {
            final Timer timer = registry.getTimers().get("kb_resource.path.createWebApplicationException.POST.4xx.404");
            Assert.assertNotNull(timer);
            Assert.assertEquals(1, timer.getCount());
        }
    }

    public static class Payment {

        private final String type;

        public Payment(final String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    @Path("path")
    public static class TestResource {

        @TimedResource
        @POST
        public Response createOk() {
            return Response.ok().build();
        }

        @TimedResource
        @POST
        public Response createOk(@MetricTag(tag = "transactionType") final String type) {
            return Response.ok().build();
        }

        @TimedResource
        @POST
        public Response createOk(@MetricTag(tag = "transactionType", property = "type") final Payment payment) {
            return Response.created(URI.create("about:blank")).build();
        }

        @TimedResource
        @PUT
        public Response createNullResponse() {
            return null;
        }

        @TimedResource
        @PUT
        public void createVoidResponse() {
        }

        @TimedResource
        @POST
        public void createWebApplicationException() {
            throw new WebApplicationException(404);
        }
    }

    // In practice, this is done by HK2, not Guice, but for convenience, we use Guice here
    public static class TestResourceModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(TestResource.class).asEagerSingleton();
            final MetricRegistry metricRegistry = new KillBillCodahaleMetricRegistry();
            bind(MetricRegistry.class).toInstance(metricRegistry);

            final InterceptionService timedInterceptionService = new TimedInterceptionService(ImmutableSet.<String>of(this.getClass().getPackage().getName()),
                                                                                              Mockito.mock(ExceptionMappers.class),
                                                                                              metricRegistry);
            bindListener(Matchers.any(), new TypeListener() {
                @Override
                public <I> void hear(final TypeLiteral<I> literal, final TypeEncounter<I> encounter) {
                    for (final Method method : literal.getRawType().getMethods()) {
                        final List<MethodInterceptor> methodInterceptors = timedInterceptionService.getMethodInterceptors(method);
                        if (methodInterceptors != null) {
                            encounter.bindInterceptor(Matchers.only(method), methodInterceptors.get(0));
                        }
                    }
                }
            });
        }
    }
}
