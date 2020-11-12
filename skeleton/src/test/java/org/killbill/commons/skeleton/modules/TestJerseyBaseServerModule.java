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
import java.util.concurrent.Future;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class TestJerseyBaseServerModule extends AbstractBaseServerModuleTest {

    @Test(groups = "slow")
    public void testJerseyIntegration() throws Exception {
        final BaseServerModuleBuilder builder = new BaseServerModuleBuilder();
        builder.addJerseyResourcePackage("org.killbill.commons.skeleton.modules");
        builder.addJerseyResourceClass(HelloFilter.class.getName());
        builder.addJerseyResourceClass(JacksonJsonProvider.class.getName());
        builder.setJaxrsUriPattern("/hello|/hello/.*");

        final Injector injector = Guice.createInjector(builder.build(),
                                                       new StatsModule(),
                                                       new AbstractModule() {
                                                           @Override
                                                           protected void configure() {
                                                               final ObjectMapper objectMapper = new ObjectMapper();
                                                               objectMapper.registerModule(new JodaModule());
                                                               objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                                                               binder().bind(ObjectMapper.class).toInstance(objectMapper);
                                                           }
                                                       });
        final Server server = startServer(injector);

        final AsyncHttpClient client = new DefaultAsyncHttpClient();
        Future<Response> responseFuture = client.prepareGet("http://127.0.0.1:" + ((NetworkConnector) server.getConnectors()[0]).getPort() + "/1.0/ping").execute();
        String body = responseFuture.get().getResponseBody();
        Assert.assertEquals(body, "pong\n");

        final MetricRegistry metricRegistry = injector.getInstance(MetricRegistry.class);
        Assert.assertEquals(metricRegistry.getMetrics().size(), 0);

        Assert.assertEquals(HelloFilter.invocations, 0);

        // Do multiple passes to verify Singleton pattern
        for (int i = 0; i < 5; i++) {
            responseFuture = client.prepareGet("http://127.0.0.1:" + ((NetworkConnector) server.getConnectors()[0]).getPort() + "/hello/alhuile/").execute();
            body = responseFuture.get().getResponseBody();
            Assert.assertEquals(body, "Hello alhuile");
            Assert.assertEquals(HelloFilter.invocations, i + 1);
        }

        responseFuture = client.preparePost("http://127.0.0.1:" + ((NetworkConnector) server.getConnectors()[0]).getPort() + "/hello").execute();
        body = responseFuture.get().getResponseBody();
        Assert.assertEquals(body, "{\"key\":\"hello\",\"date\":\"2010-01-01\"}");

        Assert.assertEquals(metricRegistry.getMetrics().size(), 2);
        Assert.assertNotNull(metricRegistry.getMetrics().get("kb_resource.hello.hello.POST.2xx.200"));
        Assert.assertNotNull(metricRegistry.getMetrics().get("kb_resource.hello.hello.GET.2xx.200"));
        server.stop();
    }

    @Test(groups = "fast")
    public void testJerseyParams() throws Exception {
        final BaseServerModuleBuilder builder1 = new BaseServerModuleBuilder();
        final JerseyBaseServerModule module1 = (JerseyBaseServerModule) builder1.build();
        final Map<String, String> jerseyParams1 = module1.getJerseyParams().build();
        Assert.assertEquals(jerseyParams1.size(), 2);
        Assert.assertEquals(jerseyParams1.get(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY), "HEADERS_ONLY");
        Assert.assertEquals(jerseyParams1.get(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL), "INFO");

        final BaseServerModuleBuilder builder2 = new BaseServerModuleBuilder();
        builder2.addJerseyParam(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY, "PAYLOAD_TEXT")
                .addJerseyParam(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL, "FINE")
                .addJerseyParam("foo", "qux");
        final JerseyBaseServerModule module2 = (JerseyBaseServerModule) builder2.build();
        final Map<String, String> jerseyParams2 = module2.getJerseyParams().build();
        Assert.assertEquals(jerseyParams2.size(), 3);
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY), "PAYLOAD_TEXT");
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL), "FINE");
        Assert.assertEquals(jerseyParams2.get("foo"), "qux");
    }
}
