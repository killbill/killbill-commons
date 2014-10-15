/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

import org.eclipse.jetty.server.Server;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

public class TestJerseyBaseServerModule extends AbstractBaseServerModuleTest {

    @Test(groups = "slow", enabled = false, description = "https://github.com/killbill/killbill-commons/issues/5")
    public void testJerseyIntegration() throws Exception {
        final BaseServerModuleBuilder builder = new BaseServerModuleBuilder();
        builder.addJaxrsResource("org.killbill.commons.skeleton.modules");
        final Server server = startServer(builder.build(), new HelloModule());

        final AsyncHttpClient client = new AsyncHttpClient();
        final Future<Response> responseFuture = client.prepareGet("http://127.0.0.1:" + server.getConnectors()[0].getPort() + "/hello/alhuile/").execute();
        final String body = responseFuture.get().getResponseBody();
        Assert.assertEquals(body, "Hello alhuile");

        server.stop();
    }

    @Test(groups = "fast")
    public void testJerseyParams() throws Exception {
        final BaseServerModuleBuilder builder1 = new BaseServerModuleBuilder();
        final JerseyBaseServerModule module1 = (JerseyBaseServerModule) builder1.build();
        final Map<String, String> jerseyParams1 = module1.getJerseyParams().build();
        Assert.assertEquals(jerseyParams1.size(), 1);
        Assert.assertEquals(jerseyParams1.get(JerseyBaseServerModule.JERSEY_DISABLE_ENTITYLOGGING), "true");

        final BaseServerModuleBuilder builder2 = new BaseServerModuleBuilder();
        builder2.addJerseyFilter("filter1").addJerseyFilter("filter2").addJerseyFilter("filter3");
        final JerseyBaseServerModule module2 = (JerseyBaseServerModule) builder2.build();
        final Map<String, String> jerseyParams2 = module2.getJerseyParams().build();
        Assert.assertEquals(jerseyParams2.size(), 3);
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_CONTAINER_REQUEST_FILTERS), "filter1;filter2;filter3");
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_CONTAINER_RESPONSE_FILTERS), "filter3;filter2;filter1");
        Assert.assertEquals(jerseyParams2.get(JerseyBaseServerModule.JERSEY_DISABLE_ENTITYLOGGING), "true");

        final BaseServerModuleBuilder builder3 = new BaseServerModuleBuilder();
        builder3.addJerseyFilter("filter1").addJerseyFilter("filter2").addJerseyFilter("filter3");
        builder3.addJerseyParam(JerseyBaseServerModule.JERSEY_CONTAINER_REQUEST_FILTERS, "bar").addJerseyParam("foo", "qux");
        final JerseyBaseServerModule module3 = (JerseyBaseServerModule) builder3.build();
        final Map<String, String> jerseyParams3 = module3.getJerseyParams().build();
        Assert.assertEquals(jerseyParams3.size(), 4);
        Assert.assertEquals(jerseyParams3.get(JerseyBaseServerModule.JERSEY_CONTAINER_REQUEST_FILTERS), "bar;filter1;filter2;filter3");
        Assert.assertEquals(jerseyParams3.get(JerseyBaseServerModule.JERSEY_CONTAINER_RESPONSE_FILTERS), "filter3;filter2;filter1");
        Assert.assertEquals(jerseyParams3.get(JerseyBaseServerModule.JERSEY_DISABLE_ENTITYLOGGING), "true");
        Assert.assertEquals(jerseyParams3.get("foo"), "qux");

        final BaseServerModuleBuilder builder4 = new BaseServerModuleBuilder();
        builder4.addJerseyParam(JerseyBaseServerModule.JERSEY_CONTAINER_REQUEST_FILTERS, "bar")
                .addJerseyParam(JerseyBaseServerModule.JERSEY_CONTAINER_RESPONSE_FILTERS, "bar2")
                .addJerseyParam(JerseyBaseServerModule.JERSEY_DISABLE_ENTITYLOGGING, "false")
                .addJerseyParam("foo", "qux");
        builder4.addJerseyFilter("filter1").addJerseyFilter("filter2").addJerseyFilter("filter3");
        final JerseyBaseServerModule module4 = (JerseyBaseServerModule) builder4.build();
        final Map<String, String> jerseyParams4 = module4.getJerseyParams().build();
        Assert.assertEquals(jerseyParams4.size(), 4);
        Assert.assertEquals(jerseyParams4.get(JerseyBaseServerModule.JERSEY_CONTAINER_REQUEST_FILTERS), "bar;filter1;filter2;filter3");
        Assert.assertEquals(jerseyParams4.get(JerseyBaseServerModule.JERSEY_CONTAINER_RESPONSE_FILTERS), "bar2;filter3;filter2;filter1");
        Assert.assertEquals(jerseyParams4.get(JerseyBaseServerModule.JERSEY_DISABLE_ENTITYLOGGING), "false");
        Assert.assertEquals(jerseyParams4.get("foo"), "qux");
    }
}
