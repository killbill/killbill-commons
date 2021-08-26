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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationObjectFactory
{
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationObjectFactory.class);
    private static final ConcurrentMap<Class<?>, Factory> factories = new ConcurrentHashMap<Class<?>, Factory>();
    private final ConfigSource config;
    private final Bully bully;

    public ConfigurationObjectFactory(Properties props)
    {
        this(new SimplePropertyConfigSource(props));
    }

    public ConfigurationObjectFactory(ConfigSource config)
    {
        this.config = config;
        this.bully = new Bully();
    }

    public void addCoercible(final Coercible<?> coercible)
    {
        this.bully.addCoercible(coercible);
    }


    public <T> T buildWithReplacements(Class<T> configClass, Map<String, String> mappedReplacements)
    {
        return internalBuild(configClass, mappedReplacements);
    }

    public <T> T build(Class<T> configClass)
    {
        return internalBuild(configClass, null);
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_OF_PUTIFABSENT_IGNORED")
    private <T> T internalBuild(Class<T> configClass, Map<String, String> mappedReplacements)
    {
        final List<Callback> callbacks = new ArrayList<Callback>();
        final Map<Method, Integer> slots = new HashMap<Method, Integer>();
        callbacks.add(NoOp.INSTANCE);

        int count = 1;

        // Hook up a toString method that prints out the settings for that bean if possible.
        final Method toStringMethod = findToStringMethod(configClass);
        if (toStringMethod != null) {
            slots.put(toStringMethod, count++);
            callbacks.add(new ConfigMagicBeanToString(callbacks));
        }

        // Now hook up the actual value interceptors.
        for (final Method method : configClass.getMethods()) {
            if (method.isAnnotationPresent(Config.class)) {
                final Config annotation = method.getAnnotation(Config.class);
                slots.put(method, count++);

                if (method.getParameterTypes().length > 0) {
                    if (mappedReplacements != null) {
                        throw new RuntimeException("Replacements are not supported for parameterized config methods");
                    }
                    buildParameterized(callbacks, method, annotation);
                }
                else {
                    buildSimple(callbacks, method, annotation, mappedReplacements, null);
                }
            }
            else if (method.isAnnotationPresent(ConfigReplacements.class)) {
                final ConfigReplacements annotation = method.getAnnotation(ConfigReplacements.class);

                slots.put(method, count++);

                if (ConfigReplacements.DEFAULT_VALUE.equals(annotation.value())) {
                    Map<String, String> fixedMap = mappedReplacements == null ?
                            Collections.<String, String>emptyMap() : Collections.unmodifiableMap(mappedReplacements);

                    callbacks.add(new ConfigMagicFixedValue(method, "annotation: @ConfigReplacements", fixedMap, false));
                } else {
                    buildSimple(callbacks, method, null, mappedReplacements, annotation);
                }
            }
            else if (Modifier.isAbstract(method.getModifiers())) {
                throw new AbstractMethodError(String.format("Method [%s] is abstract and lacks an @Config annotation",
                                                            method.toGenericString()));
            }
        }


        if (factories.containsKey(configClass)) {
            Factory f = factories.get(configClass);
            return configClass.cast(f.newInstance(callbacks.toArray(new Callback[callbacks.size()])));
        }
        else {
            Enhancer e = new Enhancer();
            e.setSuperclass(configClass);
            e.setCallbackFilter(new ConfigMagicCallbackFilter(slots));
            e.setCallbacks(callbacks.toArray(new Callback[callbacks.size()]));
            T rt = configClass.cast(e.create());
            factories.putIfAbsent(configClass, (Factory) rt);
            return rt;
        }
    }

    private void buildSimple(List<Callback> callbacks, Method method, Config annotation,
                             Map<String, String> mappedReplacements, ConfigReplacements mapAnnotation)
    {
        String assignedFrom = null;
        String[] propertyNames = new String[0];
        String value = null;

        // Annotation will be null for an @ConfigReplacements, in which case "value" will
        // be preset and ready to be defaulted + bullied
        if (annotation != null) {
            propertyNames = annotation.value();

            if (propertyNames == null || propertyNames.length == 0) {
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
                    assignedFrom = "property: '" + propertyName + "'";
                    logger.info("Assigning value [{}] for [{}] on [{}#{}()]",
                                new Object[] { value, propertyName, method.getDeclaringClass().getName(), method.getName() });
                    break;
                }
            }
        } else {
            if (mapAnnotation == null) {
                throw new IllegalStateException("Neither @Config nor @ConfigReplacements provided, this should not be possible!");
            }
            String key = mapAnnotation.value();
            value = mappedReplacements == null ? null : mappedReplacements.get(key);

            if (value != null) {
                assignedFrom = "@ConfigReplacements: key '" + key + "'";
                logger.info("Assigning mappedReplacement value [{}] for [{}] on [{}#{}()]",
                            new Object[] { value, key, method.getDeclaringClass().getName(), method.getName() });
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
                assignedFrom = "annotation: @Default";

                logger.info("Assigning default value [{}] for {} on [{}#{}()]",
                            new Object[] { value, propertyNames, method.getDeclaringClass().getName(), method.getName() });
            }
            else if (hasDefaultNull) {
                logger.info("Assigning null default value for {} on [{}#{}()]",
                            new Object[] { propertyNames, method.getDeclaringClass().getName(), method.getName() });
                assignedFrom = "annotation: @DefaultNull";
            }
            else {
                // Final try: Is the method is actually callable?
                if (!Modifier.isAbstract(method.getModifiers())) {
                    useMethod = true;
                    assignedFrom = "method: '" + method.getName() + "()'";
                    logger.info("Using method itself for {} on [{}#{}()]",
                                new Object[] { propertyNames, method.getDeclaringClass().getName(), method.getName() });
                }
                else {
                    throw new IllegalArgumentException(String.format("No value present for '%s' in [%s]",
                            prettyPrint(propertyNames, mappedReplacements),
                            method.toGenericString()));
                }
            }
        }

        final Object finalValue = bully.coerce(method.getGenericReturnType(), value, method.getAnnotation(Separator.class));
        callbacks.add(new ConfigMagicFixedValue(method, assignedFrom, finalValue, useMethod));
    }

    @SuppressFBWarnings("WMI_WRONG_MAP_ITERATOR")
    private String applyReplacements(String propertyName, Map<String, String> mappedReplacements)
    {
        for (String key : mappedReplacements.keySet()) {
            String token = makeToken(key);
            String replacement = mappedReplacements.get(key);
            propertyName = propertyName.replace(token, replacement);
        }
        return propertyName;
    }

    private void buildParameterized(List<Callback> callbacks, Method method, Config annotation)
    {
        String defaultValue = null;

        final boolean hasDefault = method.isAnnotationPresent(Default.class);
        final boolean hasDefaultNull = method.isAnnotationPresent(DefaultNull.class);

        if (hasDefault && hasDefaultNull) {
            throw new IllegalArgumentException(String.format("@Default and @DefaultNull present in [%s]", method.toGenericString()));
        }

        if (hasDefault) {
            defaultValue = method.getAnnotation(Default.class).value();
        }
        else if (!hasDefaultNull) {
            throw new IllegalArgumentException(String.format("No value present for '%s' in [%s]",
                    prettyPrint(annotation.value(), null),
                    method.toGenericString()));
        }

        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final List<String> paramTokenList = new ArrayList<String>();
        for (Annotation[] parameterTab : parameterAnnotations) {
            for (Annotation parameter : parameterTab) {
                if (parameter.annotationType().equals(Param.class)) {
                    Param paramAnnotation = (Param) parameter;
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

        if (annotationValues == null || annotationValues.length == 0) {
            throw new IllegalArgumentException("Method " +
                                               method.toGenericString() +
                                               " declares config annotation but no field name!");
        }

        callbacks.add(new ConfigMagicMethodInterceptor(method,
                                                       config,
                                                       annotationValues,
                                                       paramTokenList,
                                                       bully,
                                                       bulliedDefaultValue));
    }

    private String makeToken(String temp)
    {
        return "${" + temp + "}";
    }

    private String prettyPrint(String[] values, final Map<String, String> mappedReplacements)
    {
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
        if (mappedReplacements != null && mappedReplacements.size() > 0) {
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

    private static final class ConfigMagicFixedValue implements MethodInterceptor
    {
        private final Method method;
        private final String assignedFrom;

        private final Handler handler;

        private ConfigMagicFixedValue(final Method method, final String assignedFrom, final Object value, final boolean callSuper)
        {
            this.method = method;
            this.assignedFrom = assignedFrom;

            // This is a workaround for broken cglib
            if (callSuper) {
                this.handler = new InvokeSuperHandler();
            }
            else {
                handler = new FixedValueHandler(value);
            }
        }

        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable
        {
            return handler.handle(methodProxy, o, objects);
        }

        private static interface Handler
        {
            Object handle(MethodProxy m, Object o, Object[] args) throws Throwable;
        }

        private static class InvokeSuperHandler implements Handler
        {
            public Object handle(MethodProxy m, Object o, Object[] args) throws Throwable
            {
                return m.invokeSuper(o, args);
            }
        }

        private static class FixedValueHandler implements Handler
        {
            private final Object finalValue;

            public FixedValueHandler(final Object finalValue)
            {
                this.finalValue = finalValue;
            }

            public Object handle(MethodProxy m, Object o, Object[] args) throws Throwable
            {
                return finalValue;
            }

            private transient String toStringValue = null;

            @Override
            public String toString()
            {
                if (toStringValue == null) {
                    final StringBuilder sb = new StringBuilder("value: ");
                    if (finalValue != null) {
                        sb.append(finalValue.toString());
                        sb.append(", class: ");
                        sb.append(finalValue.getClass().getName());
                    }
                    else {
                        sb.append("<null>");
                    }
                    toStringValue = sb.toString();
                }

                return toStringValue;
            }
        }

        private transient String toStringValue = null;

        @Override
        public String toString()
        {
            if (toStringValue == null) {
                final StringBuilder sb = new StringBuilder(method.getName());
                sb.append("(): ");
                sb.append(assignedFrom);
                sb.append(", ");
                sb.append(handler.toString());
                toStringValue = sb.toString();
            }

            return toStringValue;
        }
    }


    private static final class ConfigMagicCallbackFilter implements CallbackFilter
    {
        private final Map<Method, Integer> slots;

        private ConfigMagicCallbackFilter(final Map<Method, Integer> slots)
        {
            this.slots = slots;
        }

        public int accept(Method method)
        {
            return slots.containsKey(method) ? slots.get(method) : 0;
        }
    }

    private static final class ConfigMagicMethodInterceptor implements MethodInterceptor
    {
        private final Method method;
        private final ConfigSource config;
        private final String[] properties;
        private final Bully bully;
        private final Object defaultValue;
        private final List<String> paramTokenList;

        private ConfigMagicMethodInterceptor(final Method method,
                                             final ConfigSource config,
                                             final String[] properties,
                                             final List<String> paramTokenList,
                                             final Bully bully,
                                             final Object defaultValue)
        {
            this.method = method;
            this.config = config;
            this.properties = properties;
            this.paramTokenList = paramTokenList;
            this.bully = bully;
            this.defaultValue = defaultValue;
        }

        public Object intercept(final Object o,
                                final Method method,
                                final Object[] args,
                                final MethodProxy methodProxy) throws Throwable
        {
            for (String property : properties) {
                if (args.length == paramTokenList.size()) {
                    for (int i = 0; i < args.length; ++i) {
                        property = property.replace(paramTokenList.get(i), String.valueOf(args[i]));
                    }
                    String value = config.getString(property);
                    if (value != null) {
                        logger.info("Assigning value [{}] for [{}] on [{}#{}()]",
                                    new Object[] { value, property, method.getDeclaringClass().getName(), method.getName() });
                        return bully.coerce(method.getGenericReturnType(), value, method.getAnnotation(Separator.class));
                    }
                }
                else {
                    throw new IllegalStateException("Argument list doesn't match @Param list");
                }
            }
            logger.info("Assigning default value [{}] for {} on [{}#{}()]",
                        new Object[] { defaultValue, properties, method.getDeclaringClass().getName(), method.getName() });
            return defaultValue;
        }

        private transient String toStringValue = null;

        @Override
        public String toString()
        {
            if (toStringValue == null) {
                toStringValue = method.getName() + ": " + super.toString();
            }

            return toStringValue;
        }
    }

    private Method findToStringMethod(final Class<?> clazz)
    {
        try {
            return clazz.getMethod("toString", new Class [] {});
        }
        catch (NoSuchMethodException nsme) {
            try {
                return Object.class.getMethod("toString", new Class [] {});
            }
            catch (NoSuchMethodException nsme2) {
                throw new IllegalStateException("Could not intercept toString method!", nsme);
            }
        }
    }

    private static final class ConfigMagicBeanToString implements MethodInterceptor
    {
        private final List<Callback> callbacks;

        private transient String toStringValue = null;

        private ConfigMagicBeanToString(final List<Callback> callbacks)
        {
            this.callbacks = callbacks;
        }

        public Object intercept(final Object o,
                                final Method method,
                                final Object[] args,
                                final MethodProxy methodProxy) throws Throwable
        {
            if (toStringValue == null) {
                final StringBuilder sb = new StringBuilder();

                for (int i = 2; i < callbacks.size(); i++) {
                    sb.append(callbacks.get(i).toString());

                    if (i < callbacks.size() - 1) {
                        sb.append("\n");
                    }
                }
                toStringValue = sb.toString();
            }

            return toStringValue;
        }
    }
}
