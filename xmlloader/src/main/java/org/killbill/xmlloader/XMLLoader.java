/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.net.URI;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.killbill.billing.catalog.api.InvalidConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.google.common.base.Strings;

public class XMLLoader {

    private static final String DISABLE_VALIDATION_PROP = "org.killbill.xmlloader.disable.validation";

    public static final Logger log = LoggerFactory.getLogger(XMLLoader.class);

    public static <T extends ValidatingConfig<T>> T getObjectFromString(final String uri, final Class<T> objectType) throws Exception {
        if (uri == null) {
            return null;
        }
        log.info("Initializing an object of class " + objectType.getName() + " from xml file at: " + uri);

        return getObjectFromStream(UriAccessor.accessUri(uri), objectType);
    }

    public static <T extends ValidatingConfig<T>> T getObjectFromUri(final URI uri, final Class<T> objectType) throws Exception {
        if (uri == null) {
            return null;
        }
        log.info("Initializing an object of class " + objectType.getName() + " from xml file at: " + uri);

        return getObjectFromStream(UriAccessor.accessUri(uri), objectType);
    }

    public static <T extends ValidatingConfig<T>> T getObjectFromStream(final InputStream stream, final Class<T> clazz) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException, ValidationException {
        if (stream == null) {
            return null;
        }

        final Object o = unmarshaller(clazz).unmarshal(stream);
        if (clazz.isInstance(o)) {
            @SuppressWarnings("unchecked") final T castObject = (T) o;
            try {
                initializeAndValidate(castObject);
            } catch (final ValidationException e) {
                e.getErrors().log(log);
                throw e;
            }
            return castObject;
        } else {
            return null;
        }
    }

    public static <T> T getObjectFromStreamNoValidation(final InputStream stream, final Class<T> clazz) throws SAXException, InvalidConfigException, JAXBException, IOException, TransformerException {
        final Object o = unmarshaller(clazz).unmarshal(stream);
        if (clazz.isInstance(o)) {
            @SuppressWarnings("unchecked") final T castObject = (T) o;
            return castObject;
        } else {
            return null;
        }
    }

    public static <T extends ValidatingConfig<T>> void initializeAndValidate(final T c) throws ValidationException {
        c.initialize(c);

        if (shouldDisableValidation()) {
            log.warn("Catalog validation has been disabled using property " + DISABLE_VALIDATION_PROP);
            return;
        }

        final ValidationErrors errs = c.validate(c, new ValidationErrors());
        if (!errs.isEmpty()) {
            throw new ValidationException(errs);
        }
    }

    public static Unmarshaller unmarshaller(final Class<?> clazz) throws JAXBException, SAXException, IOException, TransformerException {
        final JAXBContext context = JAXBContext.newInstance(clazz);

        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        final Unmarshaller um = context.createUnmarshaller();

        final Schema schema = factory.newSchema(new StreamSource(XMLSchemaGenerator.xmlSchema(clazz)));
        um.setSchema(schema);

        return um;
    }

    private static boolean shouldDisableValidation() {
        final String disableValidationProp = System.getProperty(DISABLE_VALIDATION_PROP);
        return (Strings.isNullOrEmpty(disableValidationProp) || Boolean.valueOf(disableValidationProp) == Boolean.FALSE) ? false : true;
    }
}
