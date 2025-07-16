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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.matcher.ElementMatchers;

class ConfigurationObjectFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationObjectFactory.class);

    private final ConfigSource config;
    private final Bully bully;

    public ConfigurationObjectFactory(final Properties props) {
        this(new SimplePropertyConfigSource(props));
    }

    public ConfigurationObjectFactory(final ConfigSource config) {
        this.config = config;
        this.bully = new Bully();
    }

    public void addCoercible(final Coercible<?> coercible) {
        this.bully.addCoercible(coercible);
    }

    public <T> T buildWithReplacements(final Class<T> configClass, final Map<String, String> mappedReplacements) {
        return internalBuild(configClass, mappedReplacements);
    }

    public <T> T build(final Class<T> configClass) {
        return internalBuild(configClass, null);
    }

    private <T> T internalBuild(final Class<T> configClass, @Nullable final Map<String, String> mappedReplacements) {
        Builder<T> bbBuilder = new ByteBuddy().subclass(configClass);

        // Hook up the actual value interceptors.
        for (final Method method : configClass.getMethods()) {
            if (method.isAnnotationPresent(Config.class)) {
                final Config annotation = method.getAnnotation(Config.class);

                if (method.getParameterTypes().length > 0) {
                    if (mappedReplacements != null) {
                        throw new RuntimeException("Replacements are not supported for parameterized config methods");
                    }
                    bbBuilder = buildParameterized(bbBuilder, method, annotation);
                } else {
                    bbBuilder = buildSimple(bbBuilder, method, annotation, mappedReplacements, null);
                }
            } else if (method.isAnnotationPresent(ConfigReplacements.class)) {
                final ConfigReplacements annotation = method.getAnnotation(ConfigReplacements.class);

                if (ConfigReplacements.DEFAULT_VALUE.equals(annotation.value())) {
                    final Map<String, String> fixedMap = mappedReplacements == null ?
                                                         Collections.<String, String>emptyMap() : Collections.unmodifiableMap(mappedReplacements);

                    bbBuilder = bbBuilder.method(ElementMatchers.is(method)).intercept(FixedValue.value(fixedMap));
                } else {
                    bbBuilder = buildSimple(bbBuilder, method, null, mappedReplacements, annotation);
                }
            } else if (Modifier.isAbstract(method.getModifiers())) {
                throw new AbstractMethodError(String.format("Method [%s] is abstract and lacks an @Config annotation",
                                                            method.toGenericString()));
            }
        }

        final Class<?> loaded = bbBuilder.make()
                                         .load(configClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                                         .getLoaded();

        try {
            return configClass.cast(loaded.getConstructor().newInstance());
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Failed to instantiate proxy class for " + configClass.getName(), e);
        }
    }

    private <T> Builder<T> buildSimple(final Builder<T> bbBuilder,
                                       final Method method,
                                       final Config annotation,
                                       final Map<String, String> mappedReplacements,
                                       final ConfigReplacements mapAnnotation) {
        String[] propertyNames = new String[0];
        String value = null;

        // Annotation will be null for an @ConfigReplacements, in which case "value" will
        // be preset and ready to be defaulted + bullied
        if (annotation != null) {
            propertyNames = annotation.value();

            if (propertyNames.length == 0) {
                throw new IllegalArgumentException("Method " +
                                                   method.toGenericString() +
                                                   " declares config annotation but no field name!");
            }

            for (String propertyName : propertyNames) {
                if (mappedReplacements != null) {
                    propertyName = applyReplacements(propertyName, mappedReplacements);
                }
                value = config.getString(propertyName);

                // First value found wins
                if (value != null) {
                    logger.info("Assigning value [{}] for [{}] on [{}#{}()]",
                                value, propertyName, method.getDeclaringClass().getName(), method.getName());
                    break;
                }
            }
        } else {
            if (mapAnnotation == null) {
                throw new IllegalStateException("Neither @Config nor @ConfigReplacements provided, this should not be possible!");
            }
            final String key = mapAnnotation.value();
            value = mappedReplacements == null ? null : mappedReplacements.get(key);

            if (value != null) {
                logger.info("Assigning mappedReplacement value [{}] for [{}] on [{}#{}()]",
                            value, key, method.getDeclaringClass().getName(), method.getName());
            }
        }

        final boolean hasDefault = method.isAnnotationPresent(Default.class);
        final boolean hasDefaultNull = method.isAnnotationPresent(DefaultNull.class);

        if (hasDefault && hasDefaultNull) {
            throw new IllegalArgumentException(String.format("@Default and @DefaultNull present in [%s]", method.toGenericString()));
        }

        boolean useMethod = false;

        //
        // This is how the value logic works if no value has been set by the config:
        //
        // - if the @Default annotation is present, use its value.
        // - if the @DefaultNull annotation is present, accept null as the value
        // - otherwise, check whether the method is not abstract. If it is not, mark the callback that it should call the method and
        //   ignore the passed in value (which will be null)
        // - if all else fails, throw an exception.
        //
        if (value == null) {
            if (hasDefault) {
                value = method.getAnnotation(Default.class).value();

                logger.info("Assigning default value [{}] for {} on [{}#{}()]",
                            value, propertyNames, method.getDeclaringClass().getName(), method.getName());
            } else if (hasDefaultNull) {
                logger.info("Assigning null default value for {} on [{}#{}()]",
                            propertyNames, method.getDeclaringClass().getName(), method.getName());
            } else {
                // Final try: Is the method is actually callable?
                if (!Modifier.isAbstract(method.getModifiers())) {
                    useMethod = true;
                    logger.info("Using method itself for {} on [{}#{}()]",
                                propertyNames, method.getDeclaringClass().getName(), method.getName());
                } else {
                    throw new IllegalArgumentException(String.format("No value present for '%s' in [%s]",
                                                                     prettyPrint(propertyNames, mappedReplacements),
                                                                     method.toGenericString()));
                }
            }
        }

        if (useMethod) {
            return bbBuilder.method(ElementMatchers.is(method)).intercept(SuperMethodCall.INSTANCE);
        } else {
            if (value == null) {
                return bbBuilder.method(ElementMatchers.is(method)).intercept(FixedValue.nullValue());
            } else {
                final Object finalValue = bully.coerce(method.getGenericReturnType(), value, method.getAnnotation(Separator.class));
                return bbBuilder.method(ElementMatchers.is(method)).intercept(FixedValue.value(finalValue));
            }
        }
    }

    private String applyReplacements(String propertyName, final Map<String, String> mappedReplacements) {
        for (final Entry<String, String> entry : mappedReplacements.entrySet()) {
            final String token = makeToken(entry.getKey());
            final String replacement = entry.getValue();
            propertyName = propertyName.replace(token, replacement);
        }
        return propertyName;
    }

    private <T> Builder<T> buildParameterized(final Builder<T> bbBuilder,
                                              final Method method,
                                              final Config annotation) {
        String defaultValue = null;

        final boolean hasDefault = method.isAnnotationPresent(Default.class);
        final boolean hasDefaultNull = method.isAnnotationPresent(DefaultNull.class);

        if (hasDefault && hasDefaultNull) {
            throw new IllegalArgumentException(String.format("@Default and @DefaultNull present in [%s]", method.toGenericString()));
        }

        if (hasDefault) {
            defaultValue = method.getAnnotation(Default.class).value();
        } else if (!hasDefaultNull) {
            throw new IllegalArgumentException(String.format("No value present for '%s' in [%s]",
                                                             prettyPrint(annotation.value(), null),
                                                             method.toGenericString()));
        }

        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final List<String> paramTokenList = new ArrayList<String>();
        for (final Annotation[] parameterTab : parameterAnnotations) {
            for (final Annotation parameter : parameterTab) {
                if (parameter.annotationType().equals(Param.class)) {
                    final Param paramAnnotation = (Param) parameter;
                    paramTokenList.add(makeToken(paramAnnotation.value()));
                    break;
                }
            }
        }

        if (paramTokenList.size() != method.getParameterTypes().length) {
            throw new RuntimeException(String.format("Method [%s] is missing one or more @Param annotations",
                                                     method.toGenericString()));
        }

        final Object bulliedDefaultValue = bully.coerce(method.getGenericReturnType(), defaultValue, method.getAnnotation(Separator.class));
        final String[] annotationValues = annotation.value();

        if (annotationValues.length == 0) {
            throw new IllegalArgumentException("Method " +
                                               method.toGenericString() +
                                               " declares config annotation but no field name!");
        }

        final ConfigMagicMethodInterceptor invocationHandler = new ConfigMagicMethodInterceptor(method,
                                                                                                config,
                                                                                                annotationValues,
                                                                                                paramTokenList,
                                                                                                bully,
                                                                                                bulliedDefaultValue);
        return bbBuilder.method(ElementMatchers.is(method)).intercept(InvocationHandlerAdapter.of(invocationHandler));
    }

    private String makeToken(final String temp) {
        return "${" + temp + "}";
    }

    private String prettyPrint(final String[] values, final Map<String, String> mappedReplacements) {
        if (values == null || values.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder("[");

        for (int i = 0; i < values.length; i++) {
            sb.append(values[i]);
            if (i < (values.length - 1)) {
                sb.append(", ");
            }
        }
        sb.append(']');
        if (mappedReplacements != null && !mappedReplacements.isEmpty()) {
            sb.append(" translated to [");
            for (int i = 0; i < values.length; i++) {
                sb.append(applyReplacements(values[i], mappedReplacements));
                if (i < (values.length - 1)) {
                    sb.append(", ");
                }
            }
            sb.append("]");
        }

        return sb.toString();
    }

    private static final class ConfigMagicMethodInterceptor implements InvocationHandler {

        private final Method method;
        private final ConfigSource config;
        private final String[] properties;
        private final Bully bully;
        private final Object defaultValue;
        private final List<String> paramTokenList;
        private transient String toStringValue = null;

        private ConfigMagicMethodInterceptor(final Method method,
                                             final ConfigSource config,
                                             final String[] properties,
                                             final List<String> paramTokenList,
                                             final Bully bully,
                                             final Object defaultValue) {
            this.method = method;
            this.config = config;
            this.properties = properties;
            this.paramTokenList = paramTokenList;
            this.bully = bully;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object invoke(final Object o,
                             final Method method,
                             final Object[] args) {
            for (String property : properties) {
                if (args.length == paramTokenList.size()) {
                    for (int i = 0; i < args.length; ++i) {
                        property = property.replace(paramTokenList.get(i), String.valueOf(args[i]));
                    }
                    final String value = config.getString(property);
                    if (value != null) {
                        logger.info("Assigning value [{}] for [{}] on [{}#{}()]",
                                    value, property, method.getDeclaringClass().getName(), method.getName());
                        return bully.coerce(method.getGenericReturnType(), value, method.getAnnotation(Separator.class));
                    }
                } else {
                    throw new IllegalStateException("Argument list doesn't match @Param list");
                }
            }
            logger.info("Assigning default value [{}] for {} on [{}#{}()]",
                        defaultValue, properties, method.getDeclaringClass().getName(), method.getName());
            return defaultValue;
        }

        @Override
        public String toString() {
            if (toStringValue == null) {
                toStringValue = method.getName() + ": " + super.toString();
            }

            return toStringValue;
        }
    }
}
