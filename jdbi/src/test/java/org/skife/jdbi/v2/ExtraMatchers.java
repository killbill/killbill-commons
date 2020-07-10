/*
 * Copyright 2004-2014 Brian McCallister
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
package org.skife.jdbi.v2;

import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;

public class ExtraMatchers
{

    public static <T, S extends T> Matcher<T> isEqualTo(S it) {
        return (Matcher<T>) CoreMatchers.equalTo(it);
    }

    public static <T> Matcher<T> equalsOneOf(T... options)
    {
        final List opts = Arrays.asList(options);
        return new BaseMatcher<T>()
        {
            @Override
            public boolean matches(Object item)
            {
                for (Object opt : opts) {
                    if (opt.equals(item)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description d)
            {
                d.appendText("one of " + opts);
            }
        };
    }
}
