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

package org.killbill.commons.metrics.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for marking a method of an annotated object as metered.
 * <p>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}ExceptionMetered(name = "fancyName", cause=IllegalArgumentException.class)
 *     public String fancyName(String name) {
 *         return "Sir Captain " + name;
 *     }
 * </code></pre>
 * <p>
 * A meter for the defining class with the name {@code fancyName} will be created and each time the
 * {@code #fancyName(String)} throws an exception of type {@code cause} (or a subclass), the meter
 * will be marked.
 * <p>
 * A name for the metric can be specified as an annotation parameter, otherwise, the metric will be
 * named based on the method name.
 * <p>
 * For instance, given a declaration of
 * <pre><code>
 *     {@literal @}ExceptionMetered
 *     public String fancyName(String name) {
 *         return "Sir Captain " + name;
 *     }
 * </code></pre>
 * <p>
 * A meter named {@code fancyName.exceptions} will be created and marked every time an exception is
 * thrown.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface ExceptionMetered {

    /**
     * The default suffix for meter names.
     */
    String DEFAULT_NAME_SUFFIX = "exceptions";

    /**
     * @return The name of the meter. If not specified, the meter will be given a name based on the method
     * it decorates and the suffix "Exceptions".
     */
    String name() default "";

    /**
     * @return If {@code true}, use the given name as an absolute name. If {@code false}, use the given name
     * relative to the annotated class. When annotating a class, this must be {@code false}.
     */
    boolean absolute() default false;

    /**
     * @return The type of exceptions that the meter will catch and count.
     */
    Class<? extends Throwable> cause() default Exception.class;
}
