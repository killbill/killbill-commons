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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestDefaultCoercibles
{
    @Test
    public void testValueOfCoercible1()
    {
        Coercer<?> c = DefaultCoercibles.VALUE_OF_COERCIBLE.accept(Date.class);

        Assert.assertThat(c, is(notNullValue()));

        Object result = c.coerce("2010-11-21");

        Assert.assertEquals(Date.class, result.getClass());
        Assert.assertThat((Date) result, equalTo(Date.valueOf("2010-11-21")));
    }

    @Test
    public void testValueOfCoercible2()
    {
        Coercer<?> c = DefaultCoercibles.VALUE_OF_COERCIBLE.accept(Long.class);

        Assert.assertThat(c, is(notNullValue()));

        Object result = c.coerce("4815162342");

        Assert.assertEquals(Long.class, result.getClass());
        Assert.assertThat((Long) result, is(4815162342L));
    }

    @Test
    public void testStringCtor1() throws MalformedURLException
    {
        Coercer<?> c = DefaultCoercibles.STRING_CTOR_COERCIBLE.accept(URL.class);

        Assert.assertThat(c, is(notNullValue()));

        Object result = c.coerce("http://www.cnn.com/");

        Assert.assertEquals(URL.class, result.getClass());
        Assert.assertThat((URL) result, equalTo(new URL("http://www.cnn.com/")));
    }

    @Test
    public void testStringCtor2()
    {
        Coercer<?> c = DefaultCoercibles.STRING_CTOR_COERCIBLE.accept(StringBuilder.class);

        Assert.assertThat(c, is(notNullValue()));

        Object result = c.coerce("Ich bin zwei Oeltanks.");

        Assert.assertEquals(StringBuilder.class, result.getClass());
        Assert.assertThat(result.toString(), is("Ich bin zwei Oeltanks."));
    }

    @Test
    public void testObjectCtor1()
    {
        Coercer<?> c = DefaultCoercibles.OBJECT_CTOR_COERCIBLE.accept(DateTime.class);

        Assert.assertThat(c, is(notNullValue()));

        Object result = c.coerce("2010-11-22T01:58Z");

        Assert.assertEquals(DateTime.class, result.getClass());
        Assert.assertThat((DateTime) result, equalTo(new DateTime("2010-11-22T01:58Z")));
    }
}
