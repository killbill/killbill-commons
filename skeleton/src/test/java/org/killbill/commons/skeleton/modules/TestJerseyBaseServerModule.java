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

import java.util.concurrent.Future;

import org.eclipse.jetty.server.Server;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

public class TestJerseyBaseServerModule extends AbstractBaseServerModuleTest {

    @Test(groups = "slow")
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
}
