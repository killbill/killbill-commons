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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.AbstractModule;

public class TestJerseyBaseServerModule extends AbstractBaseServerModuleTest {

    @Test(groups = "slow")
    public void testJerseyIntegration() throws Exception {
        final BaseServerModuleBuilder builder = new BaseServerModuleBuilder();
        builder.addJaxrsResource("org.killbill.commons.skeleton.modules");
        builder.addJerseyFilter(HelloFilter.class.getName());
        builder.addJerseyFilter(JacksonJsonProvider.class.getName());
        final Server server = startServer(builder.build(), new AbstractModule() {
            @Override
            protected void configure() {
                final ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JodaModule());
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                binder().bind(ObjectMapper.class).toInstance(objectMapper);
            }
        });

        Assert.assertEquals(HelloFilter.invocations, 0);

        // Do multiple passes to verify Singleton pattern
        for (int i = 0; i < 5; i++) {
            final AsyncHttpClient client = new DefaultAsyncHttpClient();
            final Future<Response> responseFuture = client.prepareGet("http://127.0.0.1:" + ((NetworkConnector) server.getConnectors()[0]).getPort() + "/hello/alhuile/").execute();
            final String body = responseFuture.get().getResponseBody();
            Assert.assertEquals(body, "Hello alhuile");
            Assert.assertEquals(HelloFilter.invocations, i + 1);
        }

        final AsyncHttpClient client = new DefaultAsyncHttpClient();
        final Future<Response> responseFuture = client.preparePost("http://127.0.0.1:" + ((NetworkConnector) server.getConnectors()[0]).getPort() + "/hello").execute();
        final String body = responseFuture.get().getResponseBody();
        Assert.assertEquals(body, "{\"key\":\"hello\",\"date\":\"2010-01-01\"}");

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
        builder2.addJerseyFilter("filter1").addJerseyFilter("filter2").addJerseyFilter("filter3");
        final JerseyBaseServerModule module2 = (JerseyBaseServerModule) builder2.build();
        final Map<String, String> jerseyParams2 = module2.getJerseyParams().build();
        Assert.assertEquals(jerseyParams2.size(), 3);
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_PROVIDER_CLASSNAMES), "filter1;filter2;filter3");
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY), "HEADERS_ONLY");
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL), "INFO");

        final BaseServerModuleBuilder builder3 = new BaseServerModuleBuilder();
        builder3.addJerseyFilter("filter1").addJerseyFilter("filter2").addJerseyFilter("filter3");
        builder3.addJerseyParam(JerseyBaseServerModule.JERSEY_PROVIDER_CLASSNAMES, "bar").addJerseyParam("foo", "qux");
        final JerseyBaseServerModule module3 = (JerseyBaseServerModule) builder3.build();
        final Map<String, String> jerseyParams3 = module3.getJerseyParams().build();
        Assert.assertEquals(jerseyParams3.size(), 4);
        Assert.assertEquals(jerseyParams3.get(JerseyBaseServerModule.JERSEY_PROVIDER_CLASSNAMES), "bar;filter1;filter2;filter3");
        Assert.assertEquals(jerseyParams3.get(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY), "HEADERS_ONLY");
        Assert.assertEquals(jerseyParams3.get(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL), "INFO");
        Assert.assertEquals(jerseyParams3.get("foo"), "qux");

        final BaseServerModuleBuilder builder4 = new BaseServerModuleBuilder();
        builder4.addJerseyParam(JerseyBaseServerModule.JERSEY_PROVIDER_CLASSNAMES, "bar")
                .addJerseyParam(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY, "PAYLOAD_TEXT")
                .addJerseyParam(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL, "FINE")
                .addJerseyParam("foo", "qux");
        builder4.addJerseyFilter("filter1").addJerseyFilter("filter2").addJerseyFilter("filter3");
        final JerseyBaseServerModule module4 = (JerseyBaseServerModule) builder4.build();
        final Map<String, String> jerseyParams4 = module4.getJerseyParams().build();
        Assert.assertEquals(jerseyParams4.size(), 4);
        Assert.assertEquals(jerseyParams4.get(JerseyBaseServerModule.JERSEY_PROVIDER_CLASSNAMES), "bar;filter1;filter2;filter3");
        Assert.assertEquals(jerseyParams4.get(JerseyBaseServerModule.JERSEY_LOGGING_VERBOSITY), "PAYLOAD_TEXT");
        Assert.assertEquals(jerseyParams4.get(JerseyBaseServerModule.JERSEY_LOGGING_LEVEL), "FINE");
        Assert.assertEquals(jerseyParams4.get("foo"), "qux");
    }
}
