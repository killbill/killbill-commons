/*
 * Copyright (C) 2012 The Guava Authors
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.utils.collect;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.stream.Collectors;

import org.killbill.commons.utils.Preconditions;

/**
 * Replacement of Guava {@code EvictingQueue}, some behavior may different from original one.
 */
public class EvictingQueue<E> implements Queue<E> {

    private final int maxSize;
    private final Queue<E> delegate;

    public EvictingQueue(final int maxSize) {
        this.maxSize = maxSize;
        this.delegate = new ArrayDeque<>();
    }

    /**
     * Returns the number of additional elements that this queue can accept without evicting; zero if
     * the queue is currently full.
     */
    public int remainingCapacity() {
        return maxSize - size();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] array) {
        return delegate.toArray(array);
    }

    @Override
    public boolean add(final E element) {
        Preconditions.checkNotNull(element);
        if (maxSize == 0) {
            return true;
        }
        if (size() == maxSize) {
            delegate.remove();
        }
        delegate.add(element);
        return true;
    }

    @Override
    public boolean remove(final Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> collection) {
        return delegate.containsAll(collection);
    }

    // Different implementation from original EvictingQueue.
    @Override
    public boolean addAll(final Collection<? extends E> collection) {
        if (collection.isEmpty()) {
            return false;
        }
        final int size = collection.size();
        if (size >= maxSize) {
            clear();
            collection.stream()
                      .skip(size - maxSize)
                      .collect(Collectors.toUnmodifiableList())
                      .forEach(this::add);
        } else {
            collection.forEach(this::add);
        }
        return true;
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean offer(final E e) {
        return add(e);
    }

    @Override
    public E remove() {
        return delegate.remove();
    }

    @Override
    public E poll() {
        return delegate.poll();
    }

    @Override
    public E element() {
        return delegate.element();
    }

    @Override
    public E peek() {
        return delegate.peek();
    }
}
