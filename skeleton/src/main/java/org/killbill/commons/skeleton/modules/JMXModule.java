/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import org.weakref.jmx.guice.ExportBinder;
import org.weakref.jmx.guice.MBeanModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;

public class JMXModule extends AbstractModule {

    // JMX beans to export
    private final Iterable<Class> beans;

    public JMXModule() {
        this(ImmutableList.<Class>of());
    }

    public JMXModule(final Class bean) {
        this(ImmutableList.<Class>of(bean));
    }

    public JMXModule(final Class... beans) {
        this(ImmutableList.<Class>copyOf(beans));
    }

    public JMXModule(final Iterable<Class> beans) {
        this.beans = beans;
    }

    @Override
    protected void configure() {
        final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        binder().bind(MBeanServer.class).toInstance(mBeanServer);

        install(new MBeanModule());

        final ExportBinder builder = ExportBinder.newExporter(binder());
        for (final Class beanClass : beans) {
            builder.export(beanClass).withGeneratedName();
        }
    }
}
