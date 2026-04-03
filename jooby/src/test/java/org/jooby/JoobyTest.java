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
package org.jooby;

import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.UrlEscapers;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.jooby.Session.Definition;
import org.jooby.Session.Store;
import org.jooby.internal.AppPrinter;
import org.jooby.internal.BuiltinParser;
import org.jooby.internal.BuiltinRenderer;
import org.jooby.internal.CookieSessionManager;
import org.jooby.internal.DefaulErrRenderer;
import org.jooby.internal.HttpHandlerImpl;
import org.jooby.internal.ParameterNameProvider;
import org.jooby.internal.RequestScope;
import org.jooby.internal.RouteImpl;
import org.jooby.internal.RouteMetadata;
import org.jooby.internal.ServerSessionManager;
import org.jooby.internal.SessionManager;
import org.jooby.internal.TypeConverters;
import org.jooby.internal.parser.BeanParser;
import org.jooby.internal.parser.DateParser;
import org.jooby.internal.parser.LocalDateParser;
import org.jooby.internal.parser.LocaleParser;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.internal.parser.StaticMethodParser;
import org.jooby.internal.parser.StringConstructorParser;
import org.jooby.internal.parser.ZonedDateTimeParser;
import org.jooby.internal.ssl.SslContextProvider;
import org.jooby.mvc.GET;
import org.jooby.mvc.POST;
import org.jooby.mvc.Path;
import org.jooby.scope.RequestScoped;
import org.jooby.spi.HttpHandler;
import org.jooby.spi.Server;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.jooby.funzy.Throwing;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class JoobyTest {

  public static class InternalOnStart implements Throwing.Consumer<Registry> {

    @Override
    public void tryAccept(final Registry value) throws Throwable {

    }
  }

  @Path("/singleton")
  @Singleton
  public static class SingletonTestRoute {

    @GET
    @POST
    public Object m1() {
      return "";
    }

  }

  @Path("/singleton")
  @com.google.inject.Singleton
  public static class GuiceSingletonTestRoute {

    @GET
    @POST
    public Object m1() {
      return "";
    }

  }

  @Path("/proto")
  public static class ProtoTestRoute {

    @GET
    public Object m1() {
      return "";
    }

  }

  @SuppressWarnings("rawtypes")
  private MockUnit.Block config = unit -> {
    ConstantBindingBuilder strCBB = unit.mock(ConstantBindingBuilder.class);

    AnnotatedConstantBindingBuilder strACBB = unit.mock(AnnotatedConstantBindingBuilder.class);
    when(strACBB.annotatedWith(isA(Named.class))).thenReturn(strCBB);

    LinkedBindingBuilder<List<String>> listOfString = unit.mock(LinkedBindingBuilder.class);

    LinkedBindingBuilder<Config> configBinding = unit.mock(LinkedBindingBuilder.class);
    AnnotatedBindingBuilder<Config> configAnnotatedBinding = unit
        .mock(AnnotatedBindingBuilder.class);

    when(configAnnotatedBinding.annotatedWith(isA(Named.class))).thenReturn(configBinding);
    // root config

    Binder binder = unit.get(Binder.class);
    when(binder.bindConstant()).thenReturn(strACBB);
    when(binder.bind(Config.class)).thenReturn(configAnnotatedBinding);
    when(binder.bind(Key.get(Types.listOf(String.class), Names.named("cors.allowedHeaders"))))
        .thenReturn((LinkedBindingBuilder) listOfString);
    when(binder.bind(Key.get(Types.listOf(String.class), Names.named("cors.allowedMethods"))))
        .thenReturn((LinkedBindingBuilder) listOfString);
  };

  private MockUnit.Block env = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<Env> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(Env.class)).thenReturn(binding);
  };

  private MockUnit.Block ssl = unit -> {
    Binder binder = unit.get(Binder.class);

    ScopedBindingBuilder sbbSsl = unit.mock(ScopedBindingBuilder.class);

    AnnotatedBindingBuilder<SSLContext> binding = unit.mock(AnnotatedBindingBuilder.class);
    when(binding.toProvider(SslContextProvider.class)).thenReturn(sbbSsl);

    when(binder.bind(SSLContext.class)).thenReturn(binding);
  };

  private MockUnit.Block classInfo = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<ParameterNameProvider> binding = unit
        .mock(AnnotatedBindingBuilder.class);

    when(binder.bind(ParameterNameProvider.class)).thenReturn(binding);
  };

  private MockUnit.Block charset = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<Charset> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(Charset.class)).thenReturn(binding);
  };

  private MockUnit.Block locale = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<Locale> binding = unit.mock(AnnotatedBindingBuilder.class);

    AnnotatedBindingBuilder<List<Locale>> bindings = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(Locale.class)).thenReturn(binding);

    TypeLiteral<List<Locale>> localeType = (TypeLiteral<List<Locale>>) TypeLiteral
        .get(Types.listOf(Locale.class));
    when(binder.bind(localeType)).thenReturn(bindings);
  };

  private MockUnit.Block zoneId = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<ZoneId> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(ZoneId.class)).thenReturn(binding);
  };

  private MockUnit.Block timeZone = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<TimeZone> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(TimeZone.class)).thenReturn(binding);
  };

  private MockUnit.Block dateTimeFormatter = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<DateTimeFormatter> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(DateTimeFormatter.class)).thenReturn(binding);
  };

  private MockUnit.Block numberFormat = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<NumberFormat> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(NumberFormat.class)).thenReturn(binding);
  };

  private MockUnit.Block decimalFormat = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<DecimalFormat> binding = unit.mock(AnnotatedBindingBuilder.class);

    when(binder.bind(DecimalFormat.class)).thenReturn(binding);
  };

  private MockUnit.Block renderers = unit -> {
    Multibinder<Renderer> multibinder = unit.mock(Multibinder.class);

    Binder binder = unit.get(Binder.class);
    unit.mockStatic(Multibinder.class);

    unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Renderer.class)).thenReturn(multibinder);

    LinkedBindingBuilder<Renderer> formatAsset = unit.mock(LinkedBindingBuilder.class);
    formatAsset.toInstance(BuiltinRenderer.asset);

    LinkedBindingBuilder<Renderer> formatByteArray = unit.mock(LinkedBindingBuilder.class);
    formatByteArray.toInstance(BuiltinRenderer.bytes);

    LinkedBindingBuilder<Renderer> formatByteBuffer = unit.mock(LinkedBindingBuilder.class);
    formatByteBuffer.toInstance(BuiltinRenderer.byteBuffer);

    LinkedBindingBuilder<Renderer> file = unit.mock(LinkedBindingBuilder.class);
    file.toInstance(BuiltinRenderer.file);

    LinkedBindingBuilder<Renderer> formatStream = unit.mock(LinkedBindingBuilder.class);
    formatStream.toInstance(BuiltinRenderer.stream);

    LinkedBindingBuilder<Renderer> reader = unit.mock(LinkedBindingBuilder.class);
    reader.toInstance(BuiltinRenderer.reader);

    LinkedBindingBuilder<Renderer> charBuffer = unit.mock(LinkedBindingBuilder.class);
    charBuffer.toInstance(BuiltinRenderer.charBuffer);

    LinkedBindingBuilder<Renderer> fchannel = unit.mock(LinkedBindingBuilder.class);
    fchannel.toInstance(BuiltinRenderer.fileChannel);

    LinkedBindingBuilder<Renderer> err = unit.mock(LinkedBindingBuilder.class);

    LinkedBindingBuilder<Renderer> formatAny = unit.mock(LinkedBindingBuilder.class);
    formatAny.toInstance(BuiltinRenderer.text);

    when(multibinder.addBinding()).thenReturn(formatAsset);
    when(multibinder.addBinding()).thenReturn(formatByteArray);
    when(multibinder.addBinding()).thenReturn(formatByteBuffer);
    when(multibinder.addBinding()).thenReturn(file);
    when(multibinder.addBinding()).thenReturn(charBuffer);
    when(multibinder.addBinding()).thenReturn(formatStream);
    when(multibinder.addBinding()).thenReturn(reader);
    when(multibinder.addBinding()).thenReturn(fchannel);
    when(multibinder.addBinding()).thenReturn(err);
    when(multibinder.addBinding()).thenReturn(formatAny);

  };

  private MockUnit.Block routes = unit -> {
    Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

    Binder binder = unit.get(Binder.class);

    unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(multibinder);
  };

  private MockUnit.Block routeHandler = unit -> {
    ScopedBindingBuilder routehandlerscope = unit.mock(ScopedBindingBuilder.class);
    routehandlerscope.in(Singleton.class);

    AnnotatedBindingBuilder<HttpHandler> routehandlerbinding = unit
        .mock(AnnotatedBindingBuilder.class);
    when(routehandlerbinding.to(HttpHandlerImpl.class)).thenReturn(routehandlerscope);

    when(unit.get(Binder.class).bind(HttpHandler.class)).thenReturn(routehandlerbinding);
  };

  private MockUnit.Block webSockets = unit -> {
    Multibinder<WebSocket.Definition> multibinder = unit.mock(Multibinder.class);

    Binder binder = unit.get(Binder.class);

    unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, WebSocket.Definition.class)).thenReturn(multibinder);
  };

  private MockUnit.Block tmpdir = unit -> {
    Binder binder = unit.get(Binder.class);

    LinkedBindingBuilder<File> instance = unit.mock(LinkedBindingBuilder.class);

    AnnotatedBindingBuilder<File> named = unit.mock(AnnotatedBindingBuilder.class);
    when(named.annotatedWith(Names.named("application.tmpdir"))).thenReturn(instance);

    when(binder.bind(java.io.File.class)).thenReturn(named);
  };

  private MockUnit.Block err = unit -> {
    Binder binder = unit.get(Binder.class);

    LinkedBindingBuilder<Err.Handler> ehlbb = unit.mock(LinkedBindingBuilder.class);

    Multibinder<Err.Handler> multibinder = unit.mock(Multibinder.class);
    unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Err.Handler.class)).thenReturn(multibinder);

    when(multibinder.addBinding()).thenReturn(ehlbb);
  };

  private MockUnit.Block session = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<SessionManager> smABB = unit.mock(AnnotatedBindingBuilder.class);
    when(smABB.to(ServerSessionManager.class)).thenReturn(smABB);
    smABB.asEagerSingleton();

    ScopedBindingBuilder ssSBB = unit.mock(ScopedBindingBuilder.class);
    ssSBB.asEagerSingleton();

    AnnotatedBindingBuilder<Store> ssABB = unit.mock(AnnotatedBindingBuilder.class);
    when(ssABB.to(Session.Mem.class)).thenReturn(ssSBB);

    when(binder.bind(SessionManager.class)).thenReturn(smABB);
    when(binder.bind(Session.Store.class)).thenReturn(ssABB);

    AnnotatedBindingBuilder<Session.Definition> sdABB = unit.mock(AnnotatedBindingBuilder.class);
    when(sdABB.toProvider(isA(com.google.inject.Provider.class))).thenReturn(sdABB);
    sdABB.asEagerSingleton();

    when(binder.bind(Session.Definition.class)).thenReturn(sdABB);
  };

  private MockUnit.Block boot = unit -> {
    Module module = unit.captured(Module.class).iterator().next();

    module.configure(unit.get(Binder.class));

    unit.captured(Runnable.class).get(0).run();
  };

  private MockUnit.Block requestScope = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<RequestScope> reqscopebinding = unit
        .mock(AnnotatedBindingBuilder.class);

    when(binder.bind(RequestScope.class)).thenReturn(reqscopebinding);
    binder.bindScope(RequestScoped.class, null);

    ScopedBindingBuilder reqscope = unit.mock(ScopedBindingBuilder.class);
    reqscope.in(RequestScoped.class);
    reqscope.in(RequestScoped.class);
    reqscope.in(RequestScoped.class);


    AnnotatedBindingBuilder<Request> reqbinding = unit.mock(AnnotatedBindingBuilder.class);
    when(reqbinding.toProvider(isA(Provider.class))).thenReturn(reqscope);

    when(binder.bind(Request.class)).thenReturn(reqbinding);

    AnnotatedBindingBuilder<Route.Chain> chainbinding = unit.mock(AnnotatedBindingBuilder.class);
    when(chainbinding.toProvider(isA(Provider.class))).thenReturn(reqscope);

    when(binder.bind(Route.Chain.class)).thenReturn(chainbinding);

    ScopedBindingBuilder rspscope = unit.mock(ScopedBindingBuilder.class);
    rspscope.in(RequestScoped.class);
    AnnotatedBindingBuilder<Response> rspbinding = unit.mock(AnnotatedBindingBuilder.class);
    when(rspbinding.toProvider(isA(Provider.class))).thenReturn(rspscope);

    when(binder.bind(Response.class)).thenReturn(rspbinding);

    ScopedBindingBuilder sessionscope = unit.mock(ScopedBindingBuilder.class);
    sessionscope.in(RequestScoped.class);

    AnnotatedBindingBuilder<Session> sessionbinding = unit.mock(AnnotatedBindingBuilder.class);
    when(sessionbinding.toProvider(isA(Provider.class)))
        .thenReturn(sessionscope);

    when(binder.bind(Session.class)).thenReturn(sessionbinding);

    AnnotatedBindingBuilder<Sse> sseb = unit.mock(AnnotatedBindingBuilder.class);
    when(sseb.toProvider(isA(Provider.class)))
        .thenReturn(reqscope);
    when(binder.bind(Sse.class)).thenReturn(sseb);
  };

  private MockUnit.Block params = unit -> {
    Binder binder = unit.get(Binder.class);

    AnnotatedBindingBuilder<ParserExecutor> parambinding = unit
        .mock(AnnotatedBindingBuilder.class);
    parambinding.in(Singleton.class);

    when(binder.bind(ParserExecutor.class)).thenReturn(parambinding);

    Multibinder<Parser> multibinder = unit.mock(Multibinder.class, true);

    for (Parser parser : BuiltinParser.values()) {
      LinkedBindingBuilder<Parser> converterBinding = unit.mock(LinkedBindingBuilder.class);
      converterBinding.toInstance(parser);
      when(multibinder.addBinding()).thenReturn(converterBinding);
    }

    @SuppressWarnings("rawtypes")
    Class[] parserClasses = {
        DateParser.class,
        LocalDateParser.class,
        ZonedDateTimeParser.class,
        LocaleParser.class,
        StaticMethodParser.class,
        StaticMethodParser.class,
        StaticMethodParser.class,
        StringConstructorParser.class,
        BeanParser.class
    };

    for (Class<? extends Parser> converter : parserClasses) {
      LinkedBindingBuilder<Parser> converterBinding = unit.mock(LinkedBindingBuilder.class);
      when(multibinder.addBinding()).thenReturn(converterBinding);
    }

    unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Parser.class)).thenReturn(multibinder);

  };

  private MockUnit.Block shutdown = unit -> {
    Runtime runtime = unit.mock(Runtime.class);

    Thread thread = unit.mockConstructor(Thread.class, new Class<?>[]{Runnable.class},
        unit.capture(Runnable.class));

    unit.mockStatic(Runtime.class).when(Runtime::getRuntime).thenReturn(runtime);
  };

  private MockUnit.Block guice = unit -> {
    Server server = unit.mock(Server.class);

    server.start();
    server.join();
    server.stop();

    ScopedBindingBuilder serverScope = unit.mock(ScopedBindingBuilder.class);
    serverScope.in(Singleton.class);

    AnnotatedBindingBuilder<Server> serverBinding = unit.mock(AnnotatedBindingBuilder.class);
    when(serverBinding.to(isA(Class.class))).thenReturn(serverScope);

    Binder binder = unit.get(Binder.class);
    when(binder.bind(Server.class)).thenReturn(serverBinding);

    // ConfigOrigin configOrigin = unit.mock(ConfigOrigin.class);
    // when(configOrigin.description()).thenReturn("test.conf, mock.conf");

    Config config = unit.mock(Config.class);
    when(config.getString("application.env")).thenReturn("dev");
    when(config.hasPath("server.join")).thenReturn(true);
    when(config.getBoolean("server.join")).thenReturn(true);
    unit.registerMock(Config.class, config);
    // when(config.origin()).thenReturn(configOrigin);

    Injector injector = unit.mock(Injector.class);
    when(injector.getInstance(Server.class)).thenReturn(server);
    when(injector.getInstance(Config.class)).thenReturn(config);
    when(injector.getInstance(Route.KEY)).thenReturn(Collections.emptySet());
    when(injector.getInstance(WebSocket.KEY)).thenReturn(Collections.emptySet());
    unit.registerMock(Injector.class, injector);

    AppPrinter printer = unit.constructor(AppPrinter.class)
        .args(Set.class, Set.class, Config.class)
        .build(isA(Set.class), isA(Set.class), isA(Config.class));

    unit.mockStatic(Guice.class);
    unit.mockStatic(Guice.class).when(() -> Guice.createInjector(eq(Stage.DEVELOPMENT), unit.capture(Module.class))).thenReturn(injector);

    unit.mockStatic(OptionalBinder.class);

    TypeConverters tc = unit.mockConstructor(TypeConverters.class);
  };

  @Test
  public void applicationSecret() throws Exception {

    new MockUnit(Binder.class)
        .expect(
            unit -> {
              Server server = unit.mock(Server.class);
              server.start();
              server.join();
              server.stop();

              ScopedBindingBuilder serverScope = unit.mock(ScopedBindingBuilder.class);
              serverScope.in(Singleton.class);

              AnnotatedBindingBuilder<Server> serverBinding = unit
                  .mock(AnnotatedBindingBuilder.class);
              when(serverBinding.to(isA(Class.class))).thenReturn(serverScope);

              Binder binder = unit.get(Binder.class);
              when(binder.bind(Server.class)).thenReturn(serverBinding);

              // ConfigOrigin configOrigin = unit.mock(ConfigOrigin.class);
              // when(configOrigin.description()).thenReturn("test.conf, mock.conf");

              Config config = unit.mock(Config.class);
              when(config.getString("application.env")).thenReturn("dev");
              when(config.hasPath("server.join")).thenReturn(true);
              when(config.getBoolean("server.join")).thenReturn(true);
              unit.registerMock(Config.class, config);
              // when(config.origin()).thenReturn(configOrigin);

              AppPrinter printer = unit.constructor(AppPrinter.class)
                  .args(Set.class, Set.class, Config.class)
                  .build(isA(Set.class), isA(Set.class), isA(Config.class));

              Injector injector = unit.mock(Injector.class);
              when(injector.getInstance(Server.class)).thenReturn(server);
              when(injector.getInstance(Config.class)).thenReturn(config);
              when(injector.getInstance(Route.KEY)).thenReturn(Collections.emptySet());
              when(injector.getInstance(WebSocket.KEY)).thenReturn(Collections.emptySet());

              unit.mockStatic(Guice.class);
              unit.mockStatic(Guice.class).when(() -> Guice.createInjector(eq(Stage.PRODUCTION), unit.capture(Module.class))).thenReturn(
                  injector);

              unit.mockStatic(OptionalBinder.class);

              TypeConverters tc = unit.mockConstructor(TypeConverters.class);
            })
        .expect(shutdown)
        .expect(config)
        .expect(internalOnStart(false))
        .expect(ssl)
        .expect(env)
        .expect(classInfo)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.use(ConfigFactory.empty()
              .withValue("application.env", ConfigValueFactory.fromAnyRef("prod"))
              .withValue("application.secret", ConfigValueFactory.fromAnyRef("234")));

          jooby.start();

        }, boot);
  }

  @Test
  public void defaults() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(internalOnStart(false))
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          assertEquals(false, jooby.isStarted());

          jooby.start();

          assertEquals(true, jooby.isStarted());

        }, boot);
  }

  @Test
  public void requireShouldHideProvisionExceptionWhenCauseIsErr() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(internalOnStart(false))
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(unit -> {
          Injector injector = unit.get(Injector.class);
          ProvisionException x = new ProvisionException("intentional error", new Err(Status.BAD_REQUEST));
          when(injector.getInstance(Key.get(Object.class))).thenThrow(x);
        })
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.start();

          try {
            jooby.require(Object.class);
            fail("Should throw Err");
          } catch (Err x) {
            assertEquals(400, x.statusCode());
          }

        }, boot);
  }

  @Test
  public void requireShouldNotHideProvisionExceptionWhenCauseIsNotErr() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(internalOnStart(false))
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(unit -> {
          Injector injector = unit.get(Injector.class);
          ProvisionException x = new ProvisionException("intentional error");
          when(injector.getInstance(Key.get(Object.class))).thenThrow(x);
        })
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.start();

          try {
            jooby.require(Object.class);
            fail("Should throw Err");
          } catch (ProvisionException x) {
          }

        }, boot);
  }

  @Test
  public void withInternalOnStart() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(internalOnStart(true))
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          assertEquals(false, jooby.isStarted());

          jooby.start();

          assertEquals(true, jooby.isStarted());

        }, boot);
  }

  @Test
  public void requireByNameAndTypeLiteralShouldWork() throws Exception {

    Object someVerySpecificObject = new Object();

    new MockUnit(Binder.class)
            .expect(guice)
            .expect(internalOnStart(false))
            .expect(shutdown)
            .expect(config)
            .expect(env)
            .expect(classInfo)
            .expect(ssl)
            .expect(charset)
            .expect(locale)
            .expect(zoneId)
            .expect(timeZone)
            .expect(dateTimeFormatter)
            .expect(numberFormat)
            .expect(decimalFormat)
            .expect(renderers)
            .expect(session)
            .expect(routes)
            .expect(routeHandler)
            .expect(params)
            .expect(requestScope)
            .expect(webSockets)
            .expect(tmpdir)
            .expect(err)
            .expect(unit -> {
              Injector injector = unit.get(Injector.class);
              when(injector.getInstance(Key.get(Object.class, Names.named("foo")))).thenReturn(someVerySpecificObject);
            })
            .run(unit -> {

              Jooby jooby = new Jooby();

              jooby.start();
              Object actual = jooby.require("foo", TypeLiteral.get(Object.class));
              assertEquals(actual, someVerySpecificObject);

            }, boot);
  }

  private Block internalOnStart(final boolean b) {
    return unit -> {
      Config conf = unit.get(Config.class);
      when(conf.hasPath("jooby.internal.onStart")).thenReturn(b);
      if (b) {
        when(conf.getString("jooby.internal.onStart"))
            .thenReturn(InternalOnStart.class.getName());
      }
    };
  }

  @Test
  public void cookieSession() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(unit -> {
          Binder binder = unit.get(Binder.class);

          AnnotatedBindingBuilder<SessionManager> smABB = unit.mock(AnnotatedBindingBuilder.class);
          when(smABB.to(CookieSessionManager.class)).thenReturn(smABB);
          smABB.asEagerSingleton();

          when(binder.bind(SessionManager.class)).thenReturn(smABB);

          AnnotatedBindingBuilder<Session.Definition> sdABB = unit
              .mock(AnnotatedBindingBuilder.class);
          when(sdABB.toProvider(isA(com.google.inject.Provider.class))).thenReturn(sdABB);
          sdABB.asEagerSingleton();

          when(binder.bind(Session.Definition.class)).thenReturn(sdABB);
        })
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(internalOnStart(false))
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.use(ConfigFactory.empty()
              .withValue("application.secret", ConfigValueFactory.fromAnyRef("234")));

          jooby.cookieSession();

          jooby.start();

        }, boot);
  }

  @Test
  public void cookieSessionShouldFailWhenApplicationSecretIsnotPresent() throws Throwable {

    Jooby jooby = new Jooby();

    jooby.cookieSession();

    jooby.start();
  }

  @Test
  public void onStartStopCallback() throws Exception {

    new MockUnit(Binder.class, Throwing.Runnable.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(internalOnStart(false))
        .expect(unit -> {
          unit.get(Throwing.Runnable.class).run();
          unit.get(Throwing.Runnable.class).run();
        })
        .run(unit -> {

          Jooby app = new Jooby()
              .onStart(unit.get(Throwing.Runnable.class))
              .onStop(unit.get(Throwing.Runnable.class));
          app.start();
          app.stop();

        }, boot);
  }

  @Test(expected = IllegalStateException.class)
  public void appDidnStart() throws Exception {
    new Jooby().require(Object.class);
  }

  @Test
  public void onStopCallbackLogError() throws Exception {

    new MockUnit(Binder.class, Throwing.Runnable.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(internalOnStart(false))
        .expect(unit -> {
          unit.get(Throwing.Runnable.class).run();
          doThrow(new IllegalStateException("intentional err")).when(unit.get(Throwing.Runnable.class)).run();
        })
        .run(unit -> {

          Jooby app = new Jooby()
              .onStart(unit.get(Throwing.Runnable.class))
              .onStop(unit.get(Throwing.Runnable.class));
          app.start();
          app.stop();

        }, boot);
  }

  @Test
  public void defaultsWithCallback() throws Exception {

    Jooby jooby = new Jooby();
    assertNotNull(Jooby.exportRoutes(jooby));
  }

  @Test
  public void customEnv() throws Exception {

    new MockUnit(Binder.class, Env.Builder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(unit -> {
          Env env = unit.mock(Env.class);
          when(env.name()).thenReturn("dev");
          when(env.startTasks()).thenReturn(Collections.emptyList());
          when(env.startedTasks()).thenReturn(Collections.emptyList());
          when(env.stopTasks()).thenReturn(Collections.emptyList());

          Env.Builder builder = unit.get(Env.Builder.class);
          when(builder.build(isA(Config.class), isA(Jooby.class), isA(Locale.class)))
              .thenReturn(env);

          unit.mockStatic(UrlEscapers.class);
          unit.mockStatic(HtmlEscapers.class);
          Escaper escaper = unit.mock(Escaper.class);

          unit.mockStatic(UrlEscapers.class).when(UrlEscapers::urlFragmentEscaper).thenReturn(escaper);
          unit.mockStatic(UrlEscapers.class).when(UrlEscapers::urlFormParameterEscaper).thenReturn(escaper);
          unit.mockStatic(UrlEscapers.class).when(UrlEscapers::urlPathSegmentEscaper).thenReturn(escaper);
          unit.mockStatic(HtmlEscapers.class).when(HtmlEscapers::htmlEscaper).thenReturn(escaper);

          when(env.xss(eq("urlFragment"), unit.capture(Function.class))).thenReturn(env);
          when(env.xss(eq("formParam"), unit.capture(Function.class))).thenReturn(env);
          when(env.xss(eq("pathSegment"), unit.capture(Function.class))).thenReturn(env);
          when(env.xss(eq("html"), unit.capture(Function.class))).thenReturn(env);

          Binder binder = unit.get(Binder.class);

          AnnotatedBindingBuilder<Env> binding = unit.mock(AnnotatedBindingBuilder.class);
          binding.toInstance(env);

          when(binder.bind(Env.class)).thenReturn(binding);
        })
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(internalOnStart(false))
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.env(unit.get(Env.Builder.class));

          jooby.start();

        }, boot);
  }

  @Test
  public void exportRoutes() {
    Jooby app = new Jooby();
    app.get("/export", () -> "OK");
    List<Route.Definition> routes = Jooby.exportRoutes(app);
    assertEquals(1, routes.size());
    assertEquals("/export", routes.get(0).pattern());
    assertEquals("GET", routes.get(0).method());
  }

  @Test
  public void exportConf() {
    Jooby app = new Jooby();
    app.use(ConfigFactory.empty().withValue("JoobyTest", ConfigValueFactory.fromAnyRef("foo")));
    Config conf = Jooby.exportConf(app);
    assertEquals("foo", conf.getString("JoobyTest"));
  }

  @Test
  public void exportRoutesFailure() {
    Jooby app = new Jooby();
    // generate an error on bootstrap
    app.use(ConfigFactory.empty().withValue("application.lang", ConfigValueFactory.fromAnyRef("")));

    app.get("/export", () -> "OK");
    List<Route.Definition> routes = Jooby.exportRoutes(app);
    assertEquals(0, routes.size());
  }

  @Test
  public void customLang() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(
            unit -> {

              Jooby jooby = new Jooby();
              jooby.use(ConfigFactory.empty().withValue("application.lang",
                  ConfigValueFactory.fromAnyRef("es")));

              jooby.start();

            }, boot);
  }

  @Test
  public void stopOnServerFailure() throws Exception {

    new MockUnit(Binder.class)
        .expect(
            unit -> {
              Server server = unit.mock(Server.class);
              server.start();
              server.join();
              doThrow(new Exception()).when(server).stop();

              ScopedBindingBuilder serverScope = unit.mock(ScopedBindingBuilder.class);
              serverScope.in(Singleton.class);

              AnnotatedBindingBuilder<Server> serverBinding = unit
                  .mock(AnnotatedBindingBuilder.class);
              when(serverBinding.to(isA(Class.class))).thenReturn(serverScope);

              Binder binder = unit.get(Binder.class);
              when(binder.bind(Server.class)).thenReturn(serverBinding);

              // ConfigOrigin configOrigin = unit.mock(ConfigOrigin.class);
              // when(configOrigin.description()).thenReturn("test.conf, mock.conf");

              Config config = unit.mock(Config.class);
              when(config.getString("application.env")).thenReturn("dev");
              when(config.hasPath("server.join")).thenReturn(true);
              when(config.getBoolean("server.join")).thenReturn(true);
              unit.registerMock(Config.class, config);

              AppPrinter printer = unit.constructor(AppPrinter.class)
                  .args(Set.class, Set.class, Config.class)
                  .build(isA(Set.class), isA(Set.class), isA(Config.class));

              Injector injector = unit.mock(Injector.class);
              when(injector.getInstance(Server.class)).thenReturn(server);
              when(injector.getInstance(Config.class)).thenReturn(config);
              when(injector.getInstance(Route.KEY)).thenReturn(Collections.emptySet());
              when(injector.getInstance(WebSocket.KEY)).thenReturn(Collections.emptySet());

              unit.mockStatic(Guice.class);
              when(Guice.createInjector(eq(Stage.DEVELOPMENT), unit.capture(Module.class)))
                  .thenReturn(
                      injector);

              unit.mockStatic(OptionalBinder.class);

              TypeConverters tc = unit.mockConstructor(TypeConverters.class);
            })
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(internalOnStart(false))
        .expect(tmpdir)
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.start();

        }, boot);
  }

  @Test
  public void useFilter() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit.mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.use("/filter", unit.get(Route.Filter.class));
              assertNotNull(first);
              assertEquals("/filter", first.pattern());
              assertEquals("*", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.use("GET", "*", unit.get(Route.Filter.class));
              assertNotNull(second);
              assertEquals("/**", second.pattern());
              assertEquals("GET", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void useHandler() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit.mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(internalOnStart(false))
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.use("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("*", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.use("GET", "*", unit.get(Route.Handler.class));
              assertNotNull(second);
              assertEquals("/**", second.pattern());
              assertEquals("GET", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void postHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.post("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("POST", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.post("/second", unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("POST", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.post("/third", unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("POST", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.post("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("POST", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void headHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.head("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("HEAD", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.head("/second", unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("HEAD", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.head("/third", unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("HEAD", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.head("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("HEAD", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void optionsHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.options("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("OPTIONS", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.options("/second",
                  unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("OPTIONS", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.options("/third",
                  unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("OPTIONS", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.options("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("OPTIONS", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void putHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.put("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("PUT", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.put("/second", unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("PUT", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.put("/third", unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("PUT", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.put("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("PUT", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void patchHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.patch("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("PATCH", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.patch("/second", unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("PATCH", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.patch("/third", unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("PATCH", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.patch("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("PATCH", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void deleteHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.delete("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("DELETE", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.delete("/second",
                  unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("DELETE", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.delete("/third", unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("DELETE", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.delete("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("DELETE", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void connectHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.connect("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("CONNECT", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.connect("/second",
                  unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("CONNECT", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.connect("/third",
                  unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("CONNECT", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.connect("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("CONNECT", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void traceHandlers() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    new MockUnit(Binder.class, Route.Handler.class, Route.OneArgHandler.class,
        Route.ZeroArgHandler.class, Route.Filter.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();

              Route.Definition first = jooby.trace("/first", unit.get(Route.Handler.class));
              assertNotNull(first);
              assertEquals("/first", first.pattern());
              assertEquals("TRACE", first.method());
              assertEquals("/anonymous", first.name());
              assertEquals(MediaType.ALL, first.consumes());
              assertEquals(MediaType.ALL, first.produces());

              expected.add(first);

              Route.Definition second = jooby.trace("/second", unit.get(Route.OneArgHandler.class));
              assertNotNull(second);
              assertEquals("/second", second.pattern());
              assertEquals("TRACE", second.method());
              assertEquals("/anonymous", second.name());
              assertEquals(MediaType.ALL, second.consumes());
              assertEquals(MediaType.ALL, second.produces());

              expected.add(second);

              Route.Definition third = jooby.trace("/third", unit.get(Route.ZeroArgHandler.class));
              assertNotNull(third);
              assertEquals("/third", third.pattern());
              assertEquals("TRACE", third.method());
              assertEquals("/anonymous", third.name());
              assertEquals(MediaType.ALL, third.consumes());
              assertEquals(MediaType.ALL, third.produces());

              expected.add(third);

              Route.Definition fourth = jooby.trace("/fourth", unit.get(Route.Filter.class));
              assertNotNull(fourth);
              assertEquals("/fourth", fourth.pattern());
              assertEquals("TRACE", fourth.method());
              assertEquals("/anonymous", fourth.name());
              assertEquals(MediaType.ALL, fourth.consumes());
              assertEquals(MediaType.ALL, fourth.produces());

              expected.add(fourth);

              jooby.start();

            }, boot,
            unit -> {
              List<Route.Definition> found = unit.captured(Route.Definition.class);
              assertEquals(expected, found);
            });
  }

  @Test
  public void assets() throws Exception {

    List<Route.Definition> expected = new LinkedList<>();

    String path = "/org/jooby/JoobyTest.js";
    new MockUnit(Binder.class, Request.class, Response.class, Route.Chain.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(unit -> {
          Multibinder<Renderer> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class);
          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Renderer.class)).thenReturn(multibinder);

          LinkedBindingBuilder<Renderer> customFormatter = unit
              .mock(LinkedBindingBuilder.class);
          customFormatter.toInstance(BuiltinRenderer.asset);

          LinkedBindingBuilder<Renderer> formatByteArray = unit.mock(LinkedBindingBuilder.class);
          formatByteArray.toInstance(BuiltinRenderer.bytes);

          LinkedBindingBuilder<Renderer> formatByteBuffer = unit.mock(LinkedBindingBuilder.class);
          formatByteBuffer.toInstance(BuiltinRenderer.byteBuffer);

          LinkedBindingBuilder<Renderer> file = unit.mock(LinkedBindingBuilder.class);
          file.toInstance(BuiltinRenderer.file);

          LinkedBindingBuilder<Renderer> formatStream = unit.mock(LinkedBindingBuilder.class);
          formatStream.toInstance(BuiltinRenderer.stream);

          LinkedBindingBuilder<Renderer> reader = unit.mock(LinkedBindingBuilder.class);
          reader.toInstance(BuiltinRenderer.reader);

          LinkedBindingBuilder<Renderer> charBuffer = unit.mock(LinkedBindingBuilder.class);
          charBuffer.toInstance(BuiltinRenderer.charBuffer);

          LinkedBindingBuilder<Renderer> fchannel = unit.mock(LinkedBindingBuilder.class);
          fchannel.toInstance(BuiltinRenderer.fileChannel);

          LinkedBindingBuilder<Renderer> err = unit.mock(LinkedBindingBuilder.class);

          LinkedBindingBuilder<Renderer> formatAny = unit.mock(LinkedBindingBuilder.class);
          formatAny.toInstance(BuiltinRenderer.text);

          when(multibinder.addBinding()).thenReturn(customFormatter);
          when(multibinder.addBinding()).thenReturn(formatByteArray);
          when(multibinder.addBinding()).thenReturn(formatByteBuffer);
          when(multibinder.addBinding()).thenReturn(file);
          when(multibinder.addBinding()).thenReturn(charBuffer);
          when(multibinder.addBinding()).thenReturn(formatStream);
          when(multibinder.addBinding()).thenReturn(reader);
          when(multibinder.addBinding()).thenReturn(fchannel);
          when(multibinder.addBinding()).thenReturn(err);
          when(multibinder.addBinding()).thenReturn(formatAny);
        })
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit.mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(err)
        .expect(unit -> {
          Mutant ifModifiedSince = unit.mock(Mutant.class);
          when(ifModifiedSince.toOptional(Long.class)).thenReturn(Optional.empty());

          Mutant ifnm = unit.mock(Mutant.class);
          when(ifnm.toOptional()).thenReturn(Optional.empty());

          Request req = unit.get(Request.class);
          when(req.path()).thenReturn(path);
          when(req.header("If-Modified-Since")).thenReturn(ifModifiedSince);
          when(req.header("If-None-Match")).thenReturn(ifnm);

          Response rsp = unit.get(Response.class);
          when(rsp.header(eq("Last-Modified"), unit.capture(java.util.Date.class)))
              .thenReturn(rsp);
          when(rsp.header(eq("ETag"), isA(String.class))).thenReturn(rsp);

          Route.Chain chain = unit.get(Route.Chain.class);
          chain.next(req, rsp);
        })
        .expect(internalOnStart(false))
        .expect(unit -> {
          Config conf = unit.get(Config.class);
          when(conf.getString("assets.cdn")).thenReturn("");
          when(conf.getBoolean("assets.lastModified")).thenReturn(true);
          when(conf.getBoolean("assets.etag")).thenReturn(true);
          when(conf.getString("assets.cache.maxAge")).thenReturn("-1");

          Injector injector = unit.get(Injector.class);
          when(injector.getInstance(Key.get(Config.class))).thenReturn(conf);
        })
        .run(unit -> {
          Jooby jooby = new Jooby();

          Route.Definition assets = jooby.assets("/org/jooby/**");
          expected.add(assets);

          Route.Definition dir = jooby.assets("/dir/**");
          expected.add(dir);

          jooby.start();

          Optional<Route> route = assets.matches("GET", "/org/jooby/JoobyTest.js",
              MediaType.all, MediaType.ALL);
          assertNotNull(route);
          assertTrue(route.isPresent());

          ((RouteImpl) route.get()).handle(unit.get(Request.class), unit.get(Response.class),
              unit.get(Route.Chain.class));

        }, boot, unit -> {
          List<Route.Definition> found = unit.captured(Route.Definition.class);
          assertEquals(expected, found);
        });
  }

  @Test
  public void mvcRoute() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(unit -> {
          Multibinder<Route.Definition> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Route.Definition.class)).thenReturn(
              multibinder);

          LinkedBindingBuilder<Route.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);
          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(Route.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());


          when(binder.bind(SingletonTestRoute.class)).thenReturn(null);

          when(binder.bind(GuiceSingletonTestRoute.class)).thenReturn(null);

          when(binder.bind(ProtoTestRoute.class)).thenReturn(null);
        })
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

              Jooby jooby = new Jooby();
              jooby.use(SingletonTestRoute.class);
              jooby.use(GuiceSingletonTestRoute.class);
              jooby.use(ProtoTestRoute.class);
              jooby.use("/test", SingletonTestRoute.class);
              jooby.start();

            },
            boot,
            unit -> {
              // assert routes
              List<Route.Definition> defs = unit.captured(Route.Definition.class);
              assertEquals(7, defs.size());

              assertEquals("GET", defs.get(0).method());
              assertEquals("/singleton", defs.get(0).pattern());
              assertEquals("/SingletonTestRoute.m1", defs.get(0).name());

              assertEquals("POST", defs.get(1).method());
              assertEquals("/singleton", defs.get(1).pattern());
              assertEquals("/SingletonTestRoute.m1", defs.get(1).name());

              assertEquals("GET", defs.get(2).method());
              assertEquals("/singleton", defs.get(2).pattern());
              assertEquals("/GuiceSingletonTestRoute.m1", defs.get(2).name());

              assertEquals("POST", defs.get(3).method());
              assertEquals("/singleton", defs.get(3).pattern());
              assertEquals("/GuiceSingletonTestRoute.m1", defs.get(3).name());

              assertEquals("GET", defs.get(4).method());
              assertEquals("/proto", defs.get(4).pattern());
              assertEquals("/ProtoTestRoute.m1", defs.get(4).name());

              assertEquals("GET", defs.get(5).method());
              assertEquals("/test/singleton", defs.get(5).pattern());
              assertEquals("/SingletonTestRoute.m1", defs.get(5).name());

              assertEquals("POST", defs.get(6).method());
              assertEquals("/test/singleton", defs.get(6).pattern());
              assertEquals("/SingletonTestRoute.m1", defs.get(6).name());
            });
  }

  @Test
  public void globHead() throws Exception {
    new MockUnit(Request.class, Response.class)
        .run(unit -> {
          Jooby jooby = new Jooby();

          Route.Definition head = jooby.head();
          assertNotNull(head);
          assertEquals("/**", head.pattern());
          assertEquals("HEAD", head.method());
        });
  }

  @Test
  public void globOptions() throws Exception {
    new MockUnit(Request.class, Response.class)
        .run(unit -> {
          Jooby jooby = new Jooby();

          Route.Definition options = jooby.options();
          assertNotNull(options);
          assertEquals("/**", options.pattern());
          assertEquals("OPTIONS", options.method());
        });
  }

  @Test
  public void globTrace() throws Exception {
    new MockUnit(Request.class, Response.class)
        .run(unit -> {
          Jooby jooby = new Jooby();

          Route.Definition trace = jooby.trace();
          assertNotNull(trace);
          assertEquals("/**", trace.pattern());
          assertEquals("TRACE", trace.method());
        });
  }

  @Test
  public void ws() throws Exception {

    List<WebSocket.Definition> defs = new LinkedList<>();

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(unit -> {
          Multibinder<WebSocket.Definition> multibinder = unit.mock(Multibinder.class);

          LinkedBindingBuilder<WebSocket.Definition> binding = unit
              .mock(LinkedBindingBuilder.class);

          when(multibinder.addBinding()).thenReturn(binding);
          doAnswer(inv -> { unit.addVoidCapture(WebSocket.Definition.class, inv.getArgument(0)); return null; })
              .when(binding).toInstance(any());

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, WebSocket.Definition.class)).thenReturn(
              multibinder);
        })
        .expect(tmpdir)
        .expect(err)
        .expect(internalOnStart(false))
        .run(unit -> {

          Jooby jooby = new Jooby();

          WebSocket.Definition ws = jooby.ws("/", (socket) -> {
          });
          assertEquals("/", ws.pattern());
          assertEquals(MediaType.plain, ws.consumes());
          assertEquals(MediaType.plain, ws.produces());
          defs.add(ws);

          jooby.start();

        }, boot, unit -> {
          assertEquals(defs, unit.captured(WebSocket.Definition.class));
        });
  }

  @Test
  public void useStore() throws Exception {

    new MockUnit(Store.class, Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(
            unit -> {
              Binder binder = unit.get(Binder.class);

              AnnotatedBindingBuilder<SessionManager> smABB = unit
                  .mock(AnnotatedBindingBuilder.class);
              when(smABB.to(ServerSessionManager.class)).thenReturn(smABB);
              smABB.asEagerSingleton();

              ScopedBindingBuilder ssSBB = unit.mock(ScopedBindingBuilder.class);
              ssSBB.asEagerSingleton();

              AnnotatedBindingBuilder<Store> ssABB = unit.mock(AnnotatedBindingBuilder.class);
              when(ssABB.to(unit.get(Session.Store.class).getClass())).thenReturn(ssSBB);

              when(binder.bind(SessionManager.class)).thenReturn(smABB);
              when(binder.bind(Session.Store.class)).thenReturn(ssABB);

              AnnotatedBindingBuilder<Session.Definition> sdABB = unit
                  .mock(AnnotatedBindingBuilder.class);
              when(sdABB.toProvider(unit.capture(com.google.inject.Provider.class)))
                  .thenReturn(sdABB);
              sdABB.asEagerSingleton();

              when(binder.bind(Session.Definition.class)).thenReturn(sdABB);
            })
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();
          jooby.session(unit.get(Store.class).getClass());

          jooby.start();

        }, boot, unit -> {
          Definition def = (Definition) unit.captured(com.google.inject.Provider.class)
              .iterator().next().get();
          assertEquals(unit.get(Store.class).getClass(), def.store());
        });
  }

  @Test
  public void renderer() throws Exception {

    new MockUnit(Renderer.class, Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(session)
        .expect(unit -> {
          Multibinder<Renderer> multibinder = unit.mock(Multibinder.class);

          Binder binder = unit.get(Binder.class);

          unit.mockStatic(Multibinder.class);
          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Renderer.class)).thenReturn(multibinder);

          LinkedBindingBuilder<Renderer> customFormatter = unit
              .mock(LinkedBindingBuilder.class);
          customFormatter.toInstance(unit.get(Renderer.class));

          LinkedBindingBuilder<Renderer> formatAsset = unit.mock(LinkedBindingBuilder.class);
          formatAsset.toInstance(BuiltinRenderer.asset);

          LinkedBindingBuilder<Renderer> formatByteArray = unit.mock(LinkedBindingBuilder.class);
          formatByteArray.toInstance(BuiltinRenderer.bytes);

          LinkedBindingBuilder<Renderer> formatByteBuffer = unit.mock(LinkedBindingBuilder.class);
          formatByteBuffer.toInstance(BuiltinRenderer.byteBuffer);

          LinkedBindingBuilder<Renderer> file = unit.mock(LinkedBindingBuilder.class);
          file.toInstance(BuiltinRenderer.file);

          LinkedBindingBuilder<Renderer> formatStream = unit.mock(LinkedBindingBuilder.class);
          formatStream.toInstance(BuiltinRenderer.stream);

          LinkedBindingBuilder<Renderer> reader = unit.mock(LinkedBindingBuilder.class);
          reader.toInstance(BuiltinRenderer.reader);

          LinkedBindingBuilder<Renderer> charBuffer = unit.mock(LinkedBindingBuilder.class);
          charBuffer.toInstance(BuiltinRenderer.charBuffer);

          LinkedBindingBuilder<Renderer> fchannel = unit.mock(LinkedBindingBuilder.class);
          fchannel.toInstance(BuiltinRenderer.fileChannel);

          LinkedBindingBuilder<Renderer> err = unit.mock(LinkedBindingBuilder.class);

          LinkedBindingBuilder<Renderer> formatAny = unit.mock(LinkedBindingBuilder.class);
          formatAny.toInstance(BuiltinRenderer.text);

          when(multibinder.addBinding()).thenReturn(formatAsset);
          when(multibinder.addBinding()).thenReturn(formatByteArray);
          when(multibinder.addBinding()).thenReturn(formatByteBuffer);
          when(multibinder.addBinding()).thenReturn(file);
          when(multibinder.addBinding()).thenReturn(charBuffer);
          when(multibinder.addBinding()).thenReturn(formatStream);
          when(multibinder.addBinding()).thenReturn(reader);
          when(multibinder.addBinding()).thenReturn(fchannel);
          when(multibinder.addBinding()).thenReturn(customFormatter);
          when(multibinder.addBinding()).thenReturn(err);
          when(multibinder.addBinding()).thenReturn(formatAny);
        })
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();
          jooby.renderer(unit.get(Renderer.class));

          jooby.start();

        }, boot);
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void useParser() throws Exception {

    new MockUnit(Parser.class, Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(unit -> {
          Binder binder = unit.get(Binder.class);

          AnnotatedBindingBuilder<ParserExecutor> parambinding = unit
              .mock(AnnotatedBindingBuilder.class);
          parambinding.in(Singleton.class);

          when(binder.bind(ParserExecutor.class)).thenReturn(parambinding);

          Multibinder<Parser> multibinder = unit.mock(Multibinder.class, true);

          LinkedBindingBuilder<Parser> customParser = unit.mock(LinkedBindingBuilder.class);
          customParser.toInstance(unit.get(Parser.class));

          for (Parser parser : BuiltinParser.values()) {
            LinkedBindingBuilder<Parser> converterBinding = unit.mock(LinkedBindingBuilder.class);
            converterBinding.toInstance(parser);
            when(multibinder.addBinding()).thenReturn(converterBinding);
          }

          when(multibinder.addBinding()).thenReturn(customParser);

          Class[] parserClasses = {
              DateParser.class,
              LocalDateParser.class,
              ZonedDateTimeParser.class,
              LocaleParser.class,
              StaticMethodParser.class,
              StaticMethodParser.class,
              StaticMethodParser.class,
              StringConstructorParser.class,
              BeanParser.class
          };

          for (Class<? extends Parser> converter : parserClasses) {
            LinkedBindingBuilder<Parser> converterBinding = unit.mock(LinkedBindingBuilder.class);
            when(multibinder.addBinding()).thenReturn(converterBinding);
          }

          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Parser.class)).thenReturn(multibinder);
        })
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.parser(unit.get(Parser.class));

          jooby.start();

        }, boot);
  }

  @Test
  public void useModule() throws Exception {

    new MockUnit(Binder.class, Jooby.Module.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .expect(unit -> {
          Binder binder = unit.get(Binder.class);
          Jooby.Module module = unit.get(Jooby.Module.class);

          Config config = ConfigFactory.empty();

          when(module.config()).thenReturn(config);

          module.configure(null, null, binder);
        })
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.use(unit.get(Jooby.Module.class));

          jooby.start();

        }, boot);
  }

  @Test
  public void useModuleWithError() throws Exception {
    Jooby jooby = new Jooby();

    jooby.use((env, conf, binder) -> {
      throw new NullPointerException("intentional err");
    });

    jooby.start();
  }

  @Test
  public void useConfig() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .expect(unit -> {
          AnnotatedBindingBuilder<List<Integer>> listAnnotatedBinding = unit
              .mock(AnnotatedBindingBuilder.class);
          listAnnotatedBinding.toInstance(Arrays.asList(1, 2, 3));

          Binder binder = unit.get(Binder.class);
          Key<List<Integer>> key = (Key<List<Integer>>) Key.get(Types.listOf(Integer.class),
              Names.named("list"));
          when(binder.bind(key)).thenReturn(listAnnotatedBinding);
        })
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.use(ConfigFactory.parseResources(getClass(), "JoobyTest.conf"));

          jooby.start();

        }, boot);
  }

  @Test
  public void customConf() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.conf("JoobyTest.conf");

          jooby.start();

        }, boot);
  }

  @Test
  public void customConfFile() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(classInfo)
        .expect(ssl)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.conf(new File("JoobyTest.conf"));

          jooby.start();

        }, boot);
  }

  @Test
  public void useMissingConfig() throws Exception {

    new MockUnit(Binder.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(ssl)
        .expect(classInfo)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(err)
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.use(ConfigFactory.parseResources("missing.conf"));

          jooby.start();

        }, boot);
  }

  @Test
  public void useErr() throws Exception {

    new MockUnit(Binder.class, Err.Handler.class)
        .expect(guice)
        .expect(shutdown)
        .expect(config)
        .expect(env)
        .expect(ssl)
        .expect(classInfo)
        .expect(charset)
        .expect(locale)
        .expect(zoneId)
        .expect(timeZone)
        .expect(dateTimeFormatter)
        .expect(numberFormat)
        .expect(decimalFormat)
        .expect(renderers)
        .expect(session)
        .expect(routes)
        .expect(routeHandler)
        .expect(params)
        .expect(requestScope)
        .expect(webSockets)
        .expect(tmpdir)
        .expect(internalOnStart(false))
        .expect(unit -> {
          Binder binder = unit.get(Binder.class);

          LinkedBindingBuilder<Err.Handler> ehlbb = unit.mock(LinkedBindingBuilder.class);
          ehlbb.toInstance(unit.get(Err.Handler.class));

          LinkedBindingBuilder<Err.Handler> dehlbb = unit.mock(LinkedBindingBuilder.class);

          Multibinder<Err.Handler> multibinder = unit.mock(Multibinder.class);
          unit.mockStatic(Multibinder.class).when(() -> Multibinder.newSetBinder(binder, Err.Handler.class)).thenReturn(multibinder);

          when(multibinder.addBinding()).thenReturn(ehlbb);
          when(multibinder.addBinding()).thenReturn(dehlbb);
        })
        .run(unit -> {

          Jooby jooby = new Jooby();

          jooby.err(unit.get(Err.Handler.class));

          jooby.start();

        }, boot);
  }

}
