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

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.FileAttribute;

public final class Files {

    /**
     * Do samething as {@link java.nio.file.Files#createTempDirectory(String, FileAttribute[])} with
     * {@link System#currentTimeMillis()} as its name.
     */
    public static File createTempDirectory() {
        try {
            return java.nio.file.Files.createTempDirectory(String.valueOf(System.currentTimeMillis())).toFile();
        } catch (final IOException e) {
            throw new RuntimeException("Cannot create temp directory: " + e.getMessage());
        }
    }
}
