/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestUriAccessor {

    private static final Pattern GUAVA_PATTERN = Pattern.compile(".*/guava-(\\d{2}.\\d-jre).jar$");

    private URL guavaUrl = null;
    private String guavaVersion = null;

    @BeforeClass(groups = "fast")
    public void setUp() throws Exception {
        // Find the Guava Jar on the filesystem
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        final URL[] urls = ((URLClassLoader) systemClassLoader).getURLs();
        for (final URL url : urls) {
            final Matcher matcher = GUAVA_PATTERN.matcher(url.toString());
            if (matcher.matches()) {
                guavaUrl = url;
                guavaVersion = matcher.group(1);
                break;
            }
        }
        Assert.assertNotNull(guavaUrl);
        Assert.assertNotNull(guavaVersion);
    }

    @Test(groups = "fast")
    public void testAccessJar() throws Exception {
        final InputStream inputStream = UriAccessor.accessUri(guavaUrl.toString());
        Assert.assertNotNull(inputStream);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/226")
    public void testAccessFileInJar() throws Exception {
        final String guavaPomProperties = "jar:" + guavaUrl.toString() + "!/META-INF/maven/com.google.guava/guava/pom.properties";
        final InputStream inputStream = UriAccessor.accessUri(guavaPomProperties);
        Assert.assertNotNull(inputStream);

        final Properties properties = new Properties();
        properties.load(inputStream);
        Assert.assertEquals(properties.getProperty("version"), guavaVersion);
        Assert.assertEquals(properties.getProperty("groupId"), "com.google.guava");
        Assert.assertEquals(properties.getProperty("artifactId"), "guava");
    }
}
