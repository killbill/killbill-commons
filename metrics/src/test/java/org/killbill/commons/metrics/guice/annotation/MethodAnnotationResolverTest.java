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

package org.killbill.commons.metrics.guice.annotation;

import java.lang.reflect.Method;

import org.killbill.commons.metrics.api.annotation.Counted;
import org.killbill.commons.metrics.api.annotation.Metered;
import org.killbill.commons.metrics.api.annotation.Timed;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

class MethodAnnotationResolverTest {

    @Test
    void testMethodAnnotations() throws Exception {
        final AnnotationResolver matcher = new MethodAnnotationResolver();
        final Class<MethodAnnotatedClass> klass = MethodAnnotatedClass.class;
        final Method publicMethod = klass.getDeclaredMethod("publicMethod");
        final Method protectedMethod = klass.getDeclaredMethod("protectedMethod");
        final Method packagePrivateMethod = klass.getDeclaredMethod("packagePrivateMethod");

        assertNotNull(matcher.findAnnotation(Timed.class, publicMethod));
        assertNotNull(matcher.findAnnotation(Metered.class, protectedMethod));
        assertNotNull(matcher.findAnnotation(Counted.class, packagePrivateMethod));

        assertNull(matcher.findAnnotation(Timed.class, packagePrivateMethod));
        assertNull(matcher.findAnnotation(Counted.class, protectedMethod));
        assertNull(matcher.findAnnotation(Metered.class, publicMethod));
    }

    @SuppressWarnings("WeakerAccess")
    private static class MethodAnnotatedClass {

        @Timed
        public void publicMethod() {
        }

        @Metered
        protected void protectedMethod() {
        }

        @Counted
        void packagePrivateMethod() {
        }
    }
}
