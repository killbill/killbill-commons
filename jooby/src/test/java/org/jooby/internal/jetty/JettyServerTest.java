/*
 * Copyright 2026 The Billing Project, LLC
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
package org.jooby.internal.jetty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Provider;
import javax.net.ssl.SSLContext;

import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.jooby.spi.HttpHandler;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class JettyServerTest {

  @Test
  public void shouldBuildHttpServerAndExposeExecutor() throws Exception {
    JettyServer jetty = new JettyServer(handler(), config(false, false), sslProvider());

    Server server = server(jetty);
    ServerConnector connector = (ServerConnector) server.getConnectors()[0];
    ContextHandler context = (ContextHandler) server.getHandler();

    assertTrue(jetty.executor().isPresent());
    assertSame(server.getThreadPool(), jetty.executor().get());
    assertEquals(1, server.getConnectors().length);
    assertEquals(6789, connector.getPort());
    assertEquals("0.0.0.0", connector.getHost());
    assertFactoryTypes(connector, HttpConnectionFactory.class);
    assertNotNull(context);
    assertEquals("/", context.getContextPath());
    assertTrue(context.getHandler() instanceof JettyHandler);
    assertEquals("UTF-8", System.getProperty("org.eclipse.jetty.util.UrlEncoded.charset"));
    assertEquals("204800", System.getProperty("org.eclipse.jetty.server.Request.maxFormContentSize"));
  }

  @Test
  public void shouldBuildCleartextHttp2ConnectorWhenConfigured() throws Exception {
    JettyServer jetty = new JettyServer(handler(), config(false, true), sslProvider());

    ServerConnector connector = (ServerConnector) server(jetty).getConnectors()[0];

    assertEquals(1, server(jetty).getConnectors().length);
    assertFactoryTypes(connector, HttpConnectionFactory.class, HTTP2CServerConnectionFactory.class);
  }

  @Test
  public void shouldBuildSecureConnectorWhenConfigured() throws Exception {
    AtomicInteger sslRequests = new AtomicInteger();
    Provider<SSLContext> sslProvider = () -> {
      try {
        sslRequests.incrementAndGet();
        return SSLContext.getDefault();
      } catch (Exception x) {
        throw new IllegalStateException(x);
      }
    };

    JettyServer jetty = new JettyServer(handler(), config(true, false), sslProvider);

    Connector[] connectors = server(jetty).getConnectors();
    ServerConnector http = Arrays.stream(connectors)
        .map(ServerConnector.class::cast)
        .filter(it -> it.getConnectionFactory(HttpConnectionFactory.class) != null
            && it.getConnectionFactory(SslConnectionFactory.class) == null)
        .findFirst()
        .orElseThrow(AssertionError::new);
    ServerConnector https = Arrays.stream(connectors)
        .map(ServerConnector.class::cast)
        .filter(it -> it.getConnectionFactory(SslConnectionFactory.class) != null)
        .findFirst()
        .orElseThrow(AssertionError::new);

    assertEquals(2, connectors.length);
    assertEquals(6789, http.getPort());
    assertEquals(7443, https.getPort());
    assertFactoryTypes(http, HttpConnectionFactory.class);
    assertFactoryTypes(https, SslConnectionFactory.class, HttpConnectionFactory.class);
    assertEquals(1, sslRequests.get());
  }

  @Test
  public void shouldStartAndStopServer() throws Exception {
    JettyServer jetty = new JettyServer(handler(),
        config(false, false).withValue("application.port", ConfigValueFactory.fromAnyRef(0)),
        sslProvider());

    jetty.start();
    try {
      assertTrue(server(jetty).isStarted());
    } finally {
      jetty.stop();
    }
    assertTrue(server(jetty).isStopped());
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldPropagateSetterFailures() throws Throwable {
    JettyServer jetty = new JettyServer(handler(), config(false, false), sslProvider());
    Method conf = JettyServer.class.getDeclaredMethod("conf", Object.class, Config.class, String.class);
    conf.setAccessible(true);

    try {
      conf.invoke(jetty, new ThrowingOption(), ConfigFactory.parseMap(Map.of("MaxThreads", 10)), "test");
    } catch (InvocationTargetException x) {
      throw x.getCause();
    }
  }

  @Test(expected = ConfigException.BadValue.class)
  public void shouldRejectBadThreadConfig() {
    new JettyServer(handler(),
        config(false, false).withValue("jetty.threads.MinThreads",
            ConfigValueFactory.fromAnyRef("x")),
        sslProvider());
  }

  private static HttpHandler handler() {
    return mock(HttpHandler.class);
  }

  private static Provider<SSLContext> sslProvider() {
    return () -> {
      try {
        return SSLContext.getDefault();
      } catch (Exception x) {
        throw new IllegalStateException(x);
      }
    };
  }

  private static Config config(final boolean securePort, final boolean http2) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("jetty.threads.MinThreads", "1");
    source.put("jetty.threads.MaxThreads", "10");
    source.put("jetty.threads.IdleTimeout", "3s");
    source.put("jetty.threads.Name", "jetty task");
    source.put("jetty.FileSizeThreshold", 1024);
    source.put("jetty.url.charset", "UTF-8");
    source.put("jetty.http.HeaderCacheSize", "8k");
    source.put("jetty.http.RequestHeaderSize", "8k");
    source.put("jetty.http.ResponseHeaderSize", "8k");
    source.put("jetty.http.SendServerVersion", false);
    source.put("jetty.http.SendXPoweredBy", false);
    source.put("jetty.http.SendDateHeader", false);
    source.put("jetty.http.OutputBufferSize", "32k");
    source.put("jetty.http.connector.AcceptQueueSize", 0);
    source.put("jetty.http.connector.IdleTimeout", "3s");
    source.put("server.http.MaxRequestSize", "200k");
    source.put("server.http2.enabled", http2);
    source.put("application.port", 6789);
    source.put("application.host", "0.0.0.0");
    source.put("application.tmpdir", "target");
    if (securePort) {
      source.put("application.securePort", 7443);
    }
    return ConfigFactory.parseMap(source);
  }

  private static Server server(final JettyServer jetty) throws Exception {
    Field field = JettyServer.class.getDeclaredField("server");
    field.setAccessible(true);
    return (Server) field.get(jetty);
  }

  private static void assertFactoryTypes(final ServerConnector connector,
      final Class<?>... expectedTypes) {
    ConnectionFactory[] factories = connector.getConnectionFactories().toArray(new ConnectionFactory[0]);
    assertEquals(expectedTypes.length, factories.length);
    for (int i = 0; i < expectedTypes.length; i++) {
      assertTrue(expectedTypes[i].isInstance(factories[i]));
    }
  }

  public static class ThrowingOption {
    public void setMaxThreads(final Integer value) {
      throw new IllegalArgumentException(String.valueOf(value));
    }
  }
}
