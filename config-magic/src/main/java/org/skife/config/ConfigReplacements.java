/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.skife.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If a configuration bean is created with mapped replacement values via
 * {@link AugmentedConfigurationObjectFactory#buildWithReplacements(Class, java.util.Map)},
 * this annotation designates a method which should present the provided Map.
 * The map may not be changed and is not necessarily the same instance as the original.
 * If a key is provided, the return is instead the value for that key.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConfigReplacements {

    String DEFAULT_VALUE = "__%%%noValue%%%__";

    /**
     * The key to look up in the replacement map, if any.
     */
    String value() default DEFAULT_VALUE;
}
