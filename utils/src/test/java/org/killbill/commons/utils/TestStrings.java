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

package org.killbill.commons.utils;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestStrings {

    @DataProvider(name = "testContainsUpperCase")
    public Object[][] testContainsUpperCaseData() {
        return new Object[][] {
                {"this_is_lower_case", false},
                {"lower-case-with-dash", false},
                {"lower-case-with_number-123", false},
                {"lower-case_with_other_char-!@#$", false},
                {"ALL_UPPER_CASE", true},
                {"lower_UPPER_comBINED", true},
                {null, false},
                {"", false}
        };
    }

    @Test(groups = "fast", dataProvider = "testContainsUpperCase")
    public void testContainsUpperCase(final String sample, final boolean valid) {
        Assert.assertEquals(Strings.containsUpperCase(sample), valid);
    }

    @DataProvider(name = "testToCamelCase")
    public Object[][] testToCamelCaseData() {
        return new Object[][] {
                {"this_is_lower_case", "thisIsLowerCase"},
                {"lower-case-with-dash", "lower-case-with-dash"},
                {"lower-case-with_number-123", "lower-case-withNumber-123"},
                {"lower-case_with_other_char-!@#$", "lower-caseWithOtherChar-!@#$"},
                {"ALL_UPPER_CASE", "allUpperCase"},
                {"__the_heart_of_life-_John-Mayer__", "theHeartOfLife-John-mayer"}
        };
    }

    @Test(groups = "fast", dataProvider = "testToCamelCase")
    public void testToCamelCase(final String sample, final String result) {
        final String camelCase = Strings.toCamelCase(sample, false, '_');
        // Test compatibility with Guava CaseFormat . Used in LowerToCamelBeanMapper.java
        // final String camelCase = com.google.common.base.CaseFormat.LOWER_UNDERSCORE.to(com.google.common.base.CaseFormat.LOWER_CAMEL, sample);
        Assert.assertEquals(camelCase, result);
    }

    @DataProvider(name = "testToSnakeCase")
    public Object[][] testToSnakeCaseData() {
        return new Object[][] {
                {"thisIsASentence", "this_is_a_sentence"},
                {"thisIsLowerCase", "this_is_lower_case"},
                {"lower-case-with-dash", "lower-case-with-dash"},
                {"lower-case-withNumber-123", "lower-case-with_number-123"},
                {"lower-caseWithOtherChar-!@#$", "lower-case_with_other_char-!@#$"},
                {"theHeartOfLife-John-mayer", "the_heart_of_life-_john-mayer"}
        };
    }

    @Test(groups = "fast", dataProvider = "testToSnakeCase")
    public void testToSnakeCase(final String sample, final String result) {
        final String snakeCase = Strings.toSnakeCase(sample);
        // Test compatibility with Guava CaseFormat . Used in LowerToCamelBeanMapper.java
        // final String snakeCase = com.google.common.base.CaseFormat.LOWER_CAMEL.to(com.google.common.base.CaseFormat.LOWER_UNDERSCORE, sample);
        Assert.assertEquals(snakeCase, result);
    }
}
