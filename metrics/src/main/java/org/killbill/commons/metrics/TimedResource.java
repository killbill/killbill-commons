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

package org.killbill.commons.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for marking a JAX-RS resource method of a Guice-provided object as timed.
 * <p/>
 * Given a method like this:
 * <code><pre>
 *     &#64;TimedResource(name = "fancyName")
 *     public Response create() &#123;
 *         return "Sir Captain " + name;
 *     &#125;
 * </pre></code>
 * <p/>
 * A timer metric will be created for each response code returned by the method during runtime. Metrics will also be
 * grouped be the response code (2xx, 3xx, etc). Both rate and latency metrics will be provided.
 * <p/>
 * The generated metric name uses the provided name in the annotation or the method name, if the latter is omitted:
 * <pre>
 *     kb_resource./payments.fancyName.2xx.200
 *     kb_resource./payments.fancyName.2xx.201
 * </pre>
 * Note that the metric naming is affected by other factors, like the resource Path annotation and the presence of
 * MetricTag annotations in the method's parameters
 *
 * @see MetricTag
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TimedResource {

    /**
     * The name of the timer. If not provided, the name of the method will be used.
     */
    String name() default "";
}
