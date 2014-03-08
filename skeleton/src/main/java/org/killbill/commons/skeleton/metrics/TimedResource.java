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

package org.killbill.commons.skeleton.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for marking a JAX-RS resource method of a Guice-provided object as timed.
 * <p/>
 * Given a method like this:
 * <code><pre>
 *     &#64;TimedResource(name = "fancyName", rateUnit = TimeUnit.SECONDS, durationUnit = TimeUnit.MICROSECONDS)
 *     public String getStuff() &#123;
 *         return "Sir Captain " + name;
 *     &#125;
 * </pre></code>
 * <p/>
 * One timer for each response code for the defining class with the name {@code getStuff-[responseCode]}
 * will be created and each time the {@code #getStuff()} method is invoked, the
 * method's execution will be timed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TimedResource {

    /**
     * The name of the timer.
     */
    String name() default "";

    /**
     * The default status code of the method.
     */
    int defaultStatusCode() default 200;
}
