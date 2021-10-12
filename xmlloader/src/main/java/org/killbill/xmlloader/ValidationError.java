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

package org.killbill.xmlloader;

import org.slf4j.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class ValidationError {

    private final String description;
    private final Class<?> objectType;
    private final String objectName;

    public ValidationError(final String description,
                           final Class<?> objectType,
                           final String objectName) {
        super();
        this.description = description;
        this.objectType = objectType;
        this.objectName = objectName;
    }

    public String getDescription() {
        return description;
    }

    public Class<?> getObjectType() {
        return objectType;
    }

    public String getObjectName() {
        return objectName;
    }

    public void log(final Logger log) {
        log.error(String.format("%s (%s:%s)", description, objectType, objectName));
    }

    public String toString() {
        return String.format("%s (%s:%s)%n", description, objectType, objectName);
    }
}
