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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.AbstractModule;

public class JaxrsJacksonModule extends AbstractModule {

    private final ObjectMapper mapper;
    private final Annotations[] annotationsToUse;

    public JaxrsJacksonModule(final ObjectMapper mapper) {
        this(mapper, Annotations.JACKSON, Annotations.JAXB);
    }

    public JaxrsJacksonModule(final ObjectMapper mapper, final Annotations... annotationsToUse) {
        this.mapper = mapper;
        this.annotationsToUse = annotationsToUse;
    }

    @Override
    protected void configure() {
        bind(JacksonJsonProvider.class).toInstance(new JacksonJsonProvider(mapper, annotationsToUse));
    }
}
