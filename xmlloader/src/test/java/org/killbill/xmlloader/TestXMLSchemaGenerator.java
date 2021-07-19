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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import jakarta.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import com.google.common.io.CharStreams;
import org.testng.annotations.Test;

public class TestXMLSchemaGenerator  {

    @Test(groups = "fast", enabled = false)
    public void test() throws IOException, TransformerException, JAXBException {
        final InputStream stream = XMLSchemaGenerator.xmlSchema(XmlTestClass.class);
        final String result = CharStreams.toString(new InputStreamReader(stream, "UTF-8"));
        System.out.println(result);
    }
}
