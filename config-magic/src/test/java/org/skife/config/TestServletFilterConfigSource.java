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

import static org.hamcrest.CoreMatchers.is;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestServletFilterConfigSource
{
    @Test
    public void simpleTest()
    {
        final MockFilterConfig mfc = new MockFilterConfig();
        mfc.put("foo", "hello, world");
        mfc.put("bar", "23");

        final ServletFilterConfigSource sfcs = new ServletFilterConfigSource(mfc);

        final ConfigurationObjectFactory configurationObjectFactory = new ConfigurationObjectFactory(sfcs);

        final Config5 config = configurationObjectFactory.build(Config5.class);

        Assert.assertThat(config.getFoo(), is("hello, world"));
        Assert.assertThat(config.getBar(), is(23));
    }

    private static class MockFilterConfig implements FilterConfig
    {
        private final Map<String, String> parameters = new HashMap<String, String>();

        private void put(String key, String value)
        {
            parameters.put(key, value);
        }

        public String getFilterName() {
            return "bogus";
        }

        public String getInitParameter(String name) {
            return  parameters.get(name);
        }

        @SuppressWarnings("unchecked")
        public Enumeration getInitParameterNames() {
            return new IteratorEnumeration(parameters.keySet().iterator());
        }

        public ServletContext getServletContext() {
            return null;
        }

    }
}


