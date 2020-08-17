/*
 * Copyright 2004-2014 Brian McCallister
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
package org.skife.jdbi.v2.sqlobject;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.JDBITests;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.NoOp;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@Category(JDBITests.class)
public class TestCglibDispatchBuilder
{
    /**
     * Dispatcher, FixedValue, InvocationHandler, LazyLoader, MethodInterceptor, NoOp
     *
     * @throws Exception
     */
    @Test
    public void testEnhancer() throws Exception
    {

        CGLIBDispatchBuilder b = CGLIBDispatchBuilder.create()
                                                     .withDefault(NoOp.INSTANCE)
                                                     .addCallback(Base.class.getMethod("foo"), new FixedValue()
                                                     {
                                                         @Override
                                                         public Object loadObject() throws Exception
                                                         {
                                                             return "hello";
                                                         }
                                                     });

        Enhancer e = new Enhancer();
        e.setSuperclass(Base.class);
        e.setCallbackFilter(b.getFilter());
        e.setCallbacks(b.getCallbacks());
        Base base = (Base) e.create();
        assertThat(base.foo(), equalTo("hello"));
        assertThat(base.bar(), equalTo("world"));
    }

    @Test
    public void testFactory() throws Exception
    {

        CGLIBDispatchBuilder b = CGLIBDispatchBuilder.create()
                                                     .withDefault(NoOp.INSTANCE)
                                                     .addCallback(Base.class.getMethod("foo"), new FixedValue()
                                                     {
                                                         @Override
                                                         public Object loadObject() throws Exception
                                                         {
                                                             return "hello";
                                                         }
                                                     });

        Enhancer e = new Enhancer();
        e.setSuperclass(Base.class);
        e.setCallbackFilter(b.getFilter());
        e.setCallbacks(b.getCallbacks());
        Factory factory = (Factory) e.create();

        Base base = (Base) factory.newInstance(b.getCallbacks());
        assertThat(base.foo(), equalTo("hello"));
        assertThat(base.bar(), equalTo("world"));

    }

    @Test
    public void testFactoryWithDifferentBuilder() throws Exception
    {

        CGLIBDispatchBuilder b = CGLIBDispatchBuilder.create()
                                                     .withDefault(NoOp.INSTANCE)
                                                     .addCallback(Base.class.getMethod("foo"), new FixedValue()
                                                     {
                                                         @Override
                                                         public Object loadObject() throws Exception
                                                         {
                                                             return "hello";
                                                         }
                                                     });

        Enhancer e = new Enhancer();
        e.setSuperclass(Base.class);
        e.setCallbackFilter(b.getFilter());
        e.setCallbacks(b.getCallbacks());
        Factory factory = (Factory) e.create();

        CGLIBDispatchBuilder b2 = CGLIBDispatchBuilder.create()
                                                     .withDefault(NoOp.INSTANCE)
                                                     .addCallback(Base.class.getMethod("foo"), new FixedValue()
                                                     {
                                                         @Override
                                                         public Object loadObject() throws Exception
                                                         {
                                                             return "fred";
                                                         }
                                                     });
        Base base = (Base) factory.newInstance(b2.getCallbacks());
        assertThat(base.foo(), equalTo("fred"));
        assertThat(base.bar(), equalTo("world"));

    }

    public static abstract class Base
    {
        public abstract String foo();

        public String bar()
        {
            return "world";
        }
    }
}
