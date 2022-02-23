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

import java.lang.reflect.Method;

import org.testng.annotations.Test;

import com.codahale.metrics.annotation.Counted;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import static org.testng.Assert.assertNotNull;

class ClassAnnotationResolverTest {

    @Test
    void testTypeLevelAnnotations() throws Exception {
        final AnnotationResolver matcher = new ClassAnnotationResolver();
        final Class<TypeLevelAnnotatedClass> klass = TypeLevelAnnotatedClass.class;
        final Method publicMethod = klass.getDeclaredMethod("publicMethod");
        final Method protectedMethod = klass.getDeclaredMethod("protectedMethod");
        final Method packagePrivateMethod = klass.getDeclaredMethod("packagePrivateMethod");

        assertNotNull(matcher.findAnnotation(Timed.class, publicMethod));
        assertNotNull(matcher.findAnnotation(Metered.class, publicMethod));
        assertNotNull(matcher.findAnnotation(Counted.class, publicMethod));

        assertNotNull(matcher.findAnnotation(Timed.class, protectedMethod));
        assertNotNull(matcher.findAnnotation(Metered.class, protectedMethod));
        assertNotNull(matcher.findAnnotation(Counted.class, protectedMethod));

        assertNotNull(matcher.findAnnotation(Timed.class, packagePrivateMethod));
        assertNotNull(matcher.findAnnotation(Metered.class, packagePrivateMethod));
        assertNotNull(matcher.findAnnotation(Counted.class, packagePrivateMethod));
    }

    @SuppressWarnings("WeakerAccess")
    @Timed
    @Metered
    @Counted
    private static class TypeLevelAnnotatedClass {

        public void publicMethod() {
        }

        protected void protectedMethod() {
        }

        void packagePrivateMethod() {
        }
    }
}
