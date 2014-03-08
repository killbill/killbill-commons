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

import java.util.Properties;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;

public class ConfigModule extends AbstractModule {

    private final ConfigSource configSource;
    private final Iterable<Class> configs;

    public ConfigModule() {
        this(System.getProperties());
    }

    public ConfigModule(final Properties properties) {
        this(ImmutableList.<Class>of(), new SimplePropertyConfigSource(properties));
    }

    public ConfigModule(final Class config) {
        this(ImmutableList.<Class>of(config));
    }

    public ConfigModule(final Class... configs) {
        this(ImmutableList.<Class>copyOf(configs));
    }

    public ConfigModule(final Iterable<Class> configs) {
        this(configs, new SimplePropertyConfigSource(System.getProperties()));
    }

    public ConfigModule(final Iterable<Class> configs, final ConfigSource configSource) {
        this.configs = configs;
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        for (final Class config : configs) {
            bind(config).toInstance(new ConfigurationObjectFactory(configSource).build(config));
        }
    }
}
