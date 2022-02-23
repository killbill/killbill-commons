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

package com.palominolabs.metrics.guice.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Finds annotations, if any, pertaining to a particular Method. Extension point for customizing annotation lookup.
 */
public interface AnnotationResolver {

    /**
     * @param annotationClass Metrics annotation to look for
     * @param method          method that the corresponding metric may be applied to
     * @param <T>             annotation type
     * @return a T instance, if found, else null
     */
    @Nullable
    <T extends Annotation> T findAnnotation(@Nonnull Class<T> annotationClass, @Nonnull Method method);
}
