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

package org.killbill.commons.utils.io;

import java.net.URL;
import java.util.Objects;

import org.killbill.commons.utils.Preconditions;

public final class Resources {

    /**
     * Returns a {@code URL} pointing to {@code resourceName} if the resource is found using the
     * {@linkplain Thread#getContextClassLoader() context class loader}. In simple environments, the
     * context class loader will find resources from the class path. In environments where different
     * threads can have different class loaders, for example app servers, the context class loader
     * will typically have been set to an appropriate loader for the current thread.
     *
     * <p>In the unusual case where the context class loader is null, the class loader that loaded
     * this class ({@code Resources}) will be used instead.
     *
     * @throws IllegalArgumentException if the resource is not found
     */
    public static URL getResource(final String resourceName) {
        final ClassLoader loader = Objects.requireNonNullElse(Thread.currentThread().getContextClassLoader(), Resources.class.getClassLoader());
        final URL url = loader.getResource(resourceName);
        Preconditions.checkArgument(url != null, "resource %s not found.", resourceName);
        return url;
    }
}
