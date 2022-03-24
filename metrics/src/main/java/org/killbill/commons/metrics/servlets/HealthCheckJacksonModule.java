/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.metrics.servlets;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.killbill.commons.health.api.Result;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class HealthCheckJacksonModule extends Module {

    @Override
    public String getModuleName() {
        return "healthchecks";
    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, "", "org.kill-bill.commons", "killbill-metrics");
    }

    @Override
    public void setupModule(final SetupContext context) {
        context.addSerializers(new SimpleSerializers(Collections.singletonList(new HealthCheckResultSerializer())));
    }

    private static class HealthCheckResultSerializer extends StdSerializer<Result> {

        private static final long serialVersionUID = 1L;

        private HealthCheckResultSerializer() {
            super(Result.class);
        }

        @Override
        public void serialize(final Result result,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeBooleanField("healthy", result.isHealthy());

            final String message = result.getMessage();
            if (message != null) {
                json.writeStringField("message", message);
            }

            serializeThrowable(json, result.getError(), "error");

            final Map<String, Object> details = result.getDetails();
            if (details != null && !details.isEmpty()) {
                for (final Map.Entry<String, Object> e : details.entrySet()) {
                    json.writeObjectField(e.getKey(), e.getValue());
                }
            }

            json.writeEndObject();
        }

        private void serializeThrowable(final JsonGenerator json, final Throwable error, final String name) throws IOException {
            if (error != null) {
                json.writeObjectFieldStart(name);
                json.writeStringField("type", error.getClass().getTypeName());
                json.writeStringField("message", error.getMessage());
                json.writeArrayFieldStart("stack");
                for (final StackTraceElement element : error.getStackTrace()) {
                    json.writeString(element.toString());
                }
                json.writeEndArray();

                if (error.getCause() != null) {
                    serializeThrowable(json, error.getCause(), "cause");
                }

                json.writeEndObject();
            }
        }
    }
}