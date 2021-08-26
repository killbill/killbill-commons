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

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestTimeSpan
{
    private ConfigurationObjectFactory cof;

    @Before
    public void setUp()
    {
        cof = new ConfigurationObjectFactory(new Properties());
    }

    @After
    public void tearDown()
    {
        cof = null;
    }

    @Test
    public void testMilliSeconds()
    {
        ClassWithMilliseconds ec = cof.build(ClassWithMilliseconds.class);

        Assert.assertEquals(5, ec.getValue().getPeriod());
        Assert.assertEquals(TimeUnit.MILLISECONDS, ec.getValue().getUnit());
        Assert.assertEquals(new TimeSpan(5, TimeUnit.MILLISECONDS), ec.getValue());
        Assert.assertEquals(5, ec.getValue().getMillis());
    }

    @Test
    public void testSeconds()
    {
        ClassWithSeconds ec = cof.build(ClassWithSeconds.class);

        Assert.assertEquals(5, ec.getValue().getPeriod());
        Assert.assertEquals(TimeUnit.SECONDS, ec.getValue().getUnit());
        Assert.assertEquals(new TimeSpan(5, TimeUnit.SECONDS), ec.getValue());
        Assert.assertEquals(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS), ec.getValue().getMillis());
    }

    @Test
    public void testMinutes()
    {
        ClassWithMinutes ec = cof.build(ClassWithMinutes.class);

        Assert.assertEquals(5, ec.getValue().getPeriod());
        Assert.assertEquals(TimeUnit.MINUTES, ec.getValue().getUnit());
        Assert.assertEquals(new TimeSpan(5, TimeUnit.MINUTES), ec.getValue());
        Assert.assertEquals(TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES), ec.getValue().getMillis());
    }

    @Test
    public void testHours()
    {
        ClassWithHours ec = cof.build(ClassWithHours.class);

        Assert.assertEquals(5, ec.getValue().getPeriod());
        Assert.assertEquals(TimeUnit.HOURS, ec.getValue().getUnit());
        Assert.assertEquals(new TimeSpan(5, TimeUnit.HOURS), ec.getValue());
        Assert.assertEquals(TimeUnit.MILLISECONDS.convert(5, TimeUnit.HOURS), ec.getValue().getMillis());
    }

    @Test
    public void testDays()
    {
        ClassWithDays ec = cof.build(ClassWithDays.class);

        Assert.assertEquals(5, ec.getValue().getPeriod());
        Assert.assertEquals(TimeUnit.DAYS, ec.getValue().getUnit());
        Assert.assertEquals(new TimeSpan(5, TimeUnit.DAYS), ec.getValue());
        Assert.assertEquals(TimeUnit.MILLISECONDS.convert(5, TimeUnit.DAYS), ec.getValue().getMillis());
    }

    // for [Issue-5]
    @Test
    public void testAliases()
    {
        Assert.assertEquals(new TimeSpan("5ms"), new TimeSpan("5milliseconds"));
        Assert.assertEquals(new TimeSpan("1ms"), new TimeSpan("1 millisecond"));
        Assert.assertEquals(new TimeSpan("7s"), new TimeSpan("7seconds"));
        Assert.assertEquals(new TimeSpan("1s"), new TimeSpan("1second"));
        Assert.assertEquals(new TimeSpan("15m"), new TimeSpan("15minutes"));
        Assert.assertEquals(new TimeSpan("1m"), new TimeSpan("1minute"));
        Assert.assertEquals(new TimeSpan("7m"), new TimeSpan("7min"));
        Assert.assertEquals(new TimeSpan("25h"), new TimeSpan("25hours"));
        Assert.assertEquals(new TimeSpan("1h"), new TimeSpan("1hour"));
        Assert.assertEquals(new TimeSpan("31d"), new TimeSpan("31days"));
        Assert.assertEquals(new TimeSpan("1d"), new TimeSpan("1day"));
    }

    // for [Issue-6]
    @Test
    public void testWhitespace()
    {
        ClassWithTimespanWithWhitespace ec = cof.build(ClassWithTimespanWithWhitespace.class);
        // "5 h"
        Assert.assertEquals(5, ec.getValue().getPeriod());
        Assert.assertEquals(TimeUnit.HOURS, ec.getValue().getUnit());
        Assert.assertEquals(new TimeSpan(5, TimeUnit.HOURS), ec.getValue());
        Assert.assertEquals(TimeUnit.MILLISECONDS.convert(5, TimeUnit.HOURS), ec.getValue().getMillis());

        Assert.assertEquals(new TimeSpan("5ms"), new TimeSpan("5 milliseconds"));
        Assert.assertEquals(new TimeSpan("5s"), new TimeSpan("5 seconds"));
        Assert.assertEquals(new TimeSpan("5m"), new TimeSpan("5 minutes"));
        Assert.assertEquals(new TimeSpan("5d"), new TimeSpan("5 days"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoUnit()
    {
        cof.build(ClassWithTimespanWithoutUnit.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalUnit()
    {
        cof.build(ClassWithTimespanWithIllegalUnit.class);
    }

    public static abstract class ClassWithMilliseconds
    {
        @Config("value")
        @Default("5ms")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithSeconds
    {
        @Config("value")
        @Default("5s")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithSeconds2
    {
        @Config("value")
        @Default("5seconds")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithMinutes
    {
        @Config("value")
        @Default("5m")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithHours
    {
        @Config("value")
        @Default("5h")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithDays
    {
        @Config("value")
        @Default("5d")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithTimespanWithoutUnit
    {
        @Config("value")
        @Default("5")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithTimespanWithIllegalUnit
    {
        @Config("value")
        @Default("5x")
        public abstract TimeSpan getValue();
    }

    public static abstract class ClassWithTimespanWithWhitespace
    {
        @Config("value")
        @Default("5 h")
        public abstract TimeSpan getValue();
    }
}
