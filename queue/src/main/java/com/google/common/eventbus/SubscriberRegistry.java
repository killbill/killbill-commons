/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.eventbus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.CheckForNull;

import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.Primitives;
import org.killbill.commons.utils.TypeToken;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterators;
import org.killbill.commons.utils.collect.MultiValueHashMap;
import org.killbill.commons.utils.collect.MultiValueMap;

/**
 * Registry of subscribers to a single event bus.
 *
 * @author Colin Decker
 */
final class SubscriberRegistry {

    /**
     * All registered subscribers, indexed by event type.
     *
     * <p>The {@link CopyOnWriteArraySet} values make it easy and relatively lightweight to get an
     * immutable snapshot of all current subscribers to an event without any locking.
     */
    private final ConcurrentMap<Class<?>, CopyOnWriteArraySet<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /** The event bus this registry belongs to. */
    private final EventBus bus;

    SubscriberRegistry(final EventBus bus) {
        this.bus = Preconditions.checkNotNull(bus);
    }

    /** Registers all subscriber methods on the given listener object. */
    void register(final Object listener) {
        final MultiValueMap<Class<?>, Subscriber> listenerMethods = findAllSubscribers(listener);

        for (final Entry<Class<?>, List<Subscriber>> entry : listenerMethods.entrySet()) {
            final Class<?> eventType = entry.getKey();
            final Collection<Subscriber> eventMethodsInListener = entry.getValue();

            CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(eventType);

            if (eventSubscribers == null) {
                final CopyOnWriteArraySet<Subscriber> newSet = new CopyOnWriteArraySet<>();
                eventSubscribers = Objects.requireNonNullElse(subscribers.putIfAbsent(eventType, newSet), newSet);
            }

            eventSubscribers.addAll(eventMethodsInListener);
        }
    }

    /** Unregisters all subscribers on the given listener object. */
    void unregister(final Object listener) {
        final MultiValueMap<Class<?>, Subscriber> listenerMethods = findAllSubscribers(listener);

        for (final Entry<Class<?>, List<Subscriber>> entry : listenerMethods.entrySet()) {
            final Class<?> eventType = entry.getKey();
            final Collection<Subscriber> listenerMethodsForType = entry.getValue();

            final CopyOnWriteArraySet<Subscriber> currentSubscribers = subscribers.get(eventType);
            if (currentSubscribers == null || !currentSubscribers.removeAll(listenerMethodsForType)) {
                // if removeAll returns true, all we really know is that at least one subscriber was
                // removed... however, barring something very strange we can assume that if at least one
                // subscriber was removed, all subscribers on listener for that event type were... after
                // all, the definition of subscribers on a particular class is totally static
                throw new IllegalArgumentException("missing event subscriber for an annotated method. Is " + listener + " registered?");
            }

            // don't try to remove the set if it's empty; that can't be done safely without a lock
            // anyway, if the set is empty it'll just be wrapping an array of length 0
        }
    }

    @VisibleForTesting
    Set<Subscriber> getSubscribersForTesting(final Class<?> eventType) {
        return Objects.requireNonNullElse(subscribers.get(eventType), Collections.emptySet());
    }

    /**
     * Gets an iterator representing an immutable snapshot of all subscribers to the given event at
     * the time this method is called.
     */
    Iterator<Subscriber> getSubscribers(final Object event) {
        final Set<Class<?>> eventTypes = flattenHierarchy(event.getClass());

        final List<Iterator<Subscriber>> subscriberIterators = new ArrayList<>(eventTypes.size());

        for (final Class<?> eventType : eventTypes) {
            final CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(eventType);
            if (eventSubscribers != null) {
                // eager no-copy snapshot
                subscriberIterators.add(eventSubscribers.iterator());
            }
        }

        return Iterators.concat(subscriberIterators.iterator());
    }

    /**
     * A thread-safe cache that contains the mapping from each class to all methods in that class and
     * all super-classes, that are annotated with {@code @Subscribe}. The cache is shared across all
     * instances of this class; this greatly improves performance if multiple EventBus instances are
     * created and objects of the same class are registered on all of them.
     */
    private static final ConcurrentMap<Class<?>, List<Method>> subscriberMethodsCache = new ConcurrentHashMap<>();

    /**
     * Returns all subscribers for the given listener grouped by the type of event they subscribe to.
     */
    private MultiValueMap<Class<?>, Subscriber> findAllSubscribers(final Object listener) {
        final MultiValueMap<Class<?>, Subscriber> methodsInListener = new MultiValueHashMap<>();
        final Class<?> clazz = listener.getClass();

        for (final Method method : subscriberMethodsCache.getOrDefault(clazz, getAnnotatedMethodsNotCached(clazz))) {
            final Class<?>[] parameterTypes = method.getParameterTypes();
            final Class<?> eventType = parameterTypes[0];
            methodsInListener.putElement(eventType, Subscriber.create(bus, listener, method));
        }

        return methodsInListener;
    }

    private static List<Method> getAnnotatedMethodsNotCached(final Class<?> clazz) {
        final Set<? extends Class<?>> supertypes = TypeToken.getRawTypes(clazz);
        final Map<MethodIdentifier, Method> identifiers = new HashMap<>();
        for (final Class<?> supertype : supertypes) {
            for (final Method method : supertype.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Subscribe.class) && !method.isSynthetic()) {
                    // TODO(cgdecker): Should check for a generic parameter type and error out
                    final Class<?>[] parameterTypes = method.getParameterTypes();
                    Preconditions.checkArgument(parameterTypes.length == 1,
                                                "Method %s has @Subscribe annotation but has %s parameters. Subscriber methods must have exactly 1 parameter.",
                                                method,
                                                parameterTypes.length);

                    Preconditions.checkArgument(!parameterTypes[0].isPrimitive(),
                                                "@Subscribe method %s's parameter is %s. Subscriber methods cannot accept primitives. Consider changing the parameter to %s.",
                                                method,
                                                parameterTypes[0].getName(),
                                                Primitives.wrap(parameterTypes[0]).getSimpleName());

                    final MethodIdentifier ident = new MethodIdentifier(method);
                    if (!identifiers.containsKey(ident)) {
                        identifiers.put(ident, method);
                    }
                }
            }
        }
        return List.copyOf(identifiers.values());
    }

    /** Global cache of classes to their flattened hierarchy of supertypes. */
    private static final ConcurrentMap<Class<?>, Set<Class<?>>> flattenHierarchyCache = new ConcurrentHashMap<>();

    /**
     * Flattens a class's type hierarchy into a set of {@code Class} objects including all
     * superclasses (transitively) and all interfaces implemented by these superclasses.
     */
    @VisibleForTesting
    static Set<Class<?>> flattenHierarchy(final Class<?> concreteClass) {
        // Note Issue: 1615: Originally, flattenHierarchyCache data type was "LoadingCache" from Guava:
        // https://github.com/google/guava/blob/master/guava/src/com/google/common/eventbus/SubscriberRegistry.java#L219
        // CacheLoader used ImmutableSet as return value. Somehow ImmutableSet maintains its order, where
        // HashSet isn't. This is why we have LinkedHashSet here.
        return flattenHierarchyCache.getOrDefault(concreteClass, new LinkedHashSet<>(TypeToken.getRawTypes(concreteClass)));
    }

    private static final class MethodIdentifier {

        private final String name;
        private final List<Class<?>> parameterTypes;

        MethodIdentifier(final Method method) {
            this.name = method.getName();
            this.parameterTypes = Arrays.asList(method.getParameterTypes());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, parameterTypes);
        }

        @Override
        public boolean equals(@CheckForNull final Object o) {
            if (o instanceof MethodIdentifier) {
                final MethodIdentifier ident = (MethodIdentifier) o;
                return name.equals(ident.name) && parameterTypes.equals(ident.parameterTypes);
            }
            return false;
        }
    }
}
