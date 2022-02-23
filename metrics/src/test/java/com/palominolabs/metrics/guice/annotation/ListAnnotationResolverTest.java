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
import com.google.common.collect.Lists;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

class ListAnnotationResolverTest {

    @Test
    void testMixedAnnotations() throws Exception {
        final ListAnnotationResolver annotationProvider = new ListAnnotationResolver(
                Lists.newArrayList(
                        new MethodAnnotationResolver(),
                        new ClassAnnotationResolver()
                                  )
        );

        final Class<MixedAnnotatedClass> klass = MixedAnnotatedClass.class;
        final Method publicMethod = klass.getDeclaredMethod("publicMethod");
        final Method protectedMethod = klass.getDeclaredMethod("protectedMethod");
        final Method packagePrivateMethod = klass.getDeclaredMethod("packagePrivateMethod");

        final Timed classTimed = annotationProvider.findAnnotation(Timed.class, publicMethod);
        assertNotNull(classTimed);
        assertFalse(classTimed.absolute());
        assertNull(annotationProvider.findAnnotation(Metered.class, publicMethod));
        assertNull(annotationProvider.findAnnotation(Counted.class, publicMethod));

        assertNotNull(annotationProvider.findAnnotation(Timed.class, protectedMethod));
        assertNotNull(annotationProvider.findAnnotation(Metered.class, protectedMethod));
        assertNull(annotationProvider.findAnnotation(Counted.class, protectedMethod));

        final Timed methodTimed = annotationProvider.findAnnotation(Timed.class, packagePrivateMethod);
        assertNotNull(methodTimed);
        assertTrue(methodTimed.absolute());
        assertNull(annotationProvider.findAnnotation(Metered.class, packagePrivateMethod));
        assertNull(annotationProvider.findAnnotation(Counted.class, packagePrivateMethod));
    }

    @SuppressWarnings("WeakerAccess")
    @Timed
    private static class MixedAnnotatedClass {

        public void publicMethod() {
        }

        @Metered
        protected void protectedMethod() {
        }

        @Timed(absolute = true)
        void packagePrivateMethod() {
        }
    }
}
