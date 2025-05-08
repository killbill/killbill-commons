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

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.ConfigSource;
import org.skife.config.SimplePropertyConfigSource;

import com.google.inject.AbstractModule;

public class ConfigModule extends AbstractModule {

    private final ConfigSource configSource;
    private final Iterable<Class> configs;

    public ConfigModule() {
        this(System.getProperties());
    }

    public ConfigModule(final Properties properties) {
        this(Collections.emptyList(), new SimplePropertyConfigSource(properties));
    }

    public ConfigModule(final Class config) {
        this(List.of(config));
    }

    public ConfigModule(final Class... configs) {
        this(List.of(configs));
    }

    public ConfigModule(final Iterable<Class> configs) {
        this(configs, new SimplePropertyConfigSource(System.getProperties()));
    }

    public ConfigModule(final Iterable<Class> configs, final ConfigSource configSource) {
        this.configs = configs;
        this.configSource = configSource;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void configure() {
        for (final Class config : configs) {
            bind(config).toInstance(new AugmentedConfigurationObjectFactory(configSource).build(config));
        }
    }
}
