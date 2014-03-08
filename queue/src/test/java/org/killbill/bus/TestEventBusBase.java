/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.bus;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.eventbus.Subscribe;

public class TestEventBusBase {

    protected static final Logger log = LoggerFactory.getLogger(TestEventBusBase.class);

    private final PersistentBus eventBus;

    public TestEventBusBase(final PersistentBus eventBus) {
        this.eventBus = eventBus;
    }

    public static class MyEvent implements BusEvent  {

        private final String name;
        private final Long value;
        private final String type;
        private final Long searchKey1;
        private final Long searchKey2;
        private final UUID userToken;

        @JsonCreator
        public MyEvent(@JsonProperty("name") final String name,
                       @JsonProperty("value") final Long value,
                       @JsonProperty("type") final String type,
                       @JsonProperty("searchKey1") final Long searchKey1,
                       @JsonProperty("searchKey2") final Long searchKey2,
                       @JsonProperty("userToken") final UUID userToken) {
            this.name = name;
            this.value = value;
            this.type = type;
            this.searchKey2 = searchKey2;
            this.searchKey1 = searchKey1;
            this.userToken = userToken;
        }

        public String getName() {
            return name;
        }

        public Long getValue() {
            return value;
        }

        public String getType() {
            return type;
        }

        @Override
        public Long getSearchKey1() {
            return searchKey1;
        }

        @Override
        public Long getSearchKey2() {
            return searchKey2;
        }

        @Override
        public UUID getUserToken() {
            return userToken;
        }
    }


    public static final class MyOtherEvent implements BusEvent {

        private final String name;
        private final Double value;
        private final String type;
        private final Long searchKey1;
        private final Long searchKey2;
        private final UUID userToken;

        @JsonCreator
        public MyOtherEvent(@JsonProperty("name") final String name,
                            @JsonProperty("value") final Double value,
                            @JsonProperty("type") final String type,
                            @JsonProperty("searchKey1") final Long searchKey1,
                            @JsonProperty("searchKey2") final Long searchKey2,
                            @JsonProperty("userToken") final UUID userToken) {
            this.name = name;
            this.value = value;
            this.type = type;
            this.searchKey2 = searchKey2;
            this.searchKey1 = searchKey1;
            this.userToken = userToken;
        }


        public String getName() {
            return name;
        }

        public Double getValue() {
            return value;
        }

        public String getType() {
            return type;
        }

        @Override
        public Long getSearchKey1() {
            return searchKey1;
        }

        @Override
        public Long getSearchKey2() {
            return searchKey2;
        }

        @Override
        public UUID getUserToken() {
            return userToken;
        }
    }

    public static class MyEventHandlerException extends RuntimeException {

        private static final long serialVersionUID = 156337823L;

        public MyEventHandlerException(final String msg) {
            super(msg);
        }
    }

    public static class MyEventHandler {

        private final int expectedEvents;
        private final int nbThrowExceptions;

        private volatile int gotEvents;
        private volatile int gotExceptions;

        public MyEventHandler(final int exp, final int nbThrowExceptions) {
            this.expectedEvents = exp;
            this.nbThrowExceptions = nbThrowExceptions;
            this.gotEvents = 0;
            this.gotExceptions = 0;
        }

        public synchronized int getEvents() {
            return gotEvents;
        }

        @Subscribe
        public synchronized void processMyEvent(final MyEvent event) {

            //log.debug("Got event {} {}", event.name, event.value);

            if (gotExceptions < nbThrowExceptions) {
                gotExceptions++;
                throw new MyEventHandlerException("FAIL");
            }
            gotEvents++;
        }


        public synchronized boolean waitForCompletion(final long timeoutMs) {

            final long ini = System.currentTimeMillis();
            long remaining = timeoutMs;
            while (gotEvents < expectedEvents && remaining > 0) {
                try {
                    wait(1000);
                    if (gotEvents == expectedEvents) {
                        break;
                    }
                    remaining = timeoutMs - (System.currentTimeMillis() - ini);
                } catch (InterruptedException ignore) {
                }
            }
            return (gotEvents == expectedEvents);
        }
    }

    public void testSimpleWithExceptionAndRetrySuccess() {
        try {
            final MyEventHandler handler = new MyEventHandler(1, 1);
            eventBus.register(handler);

            eventBus.post(new MyEvent("my-event", 1L, "MY_EVENT_TYPE", 1L, 2L, UUID.randomUUID()));
            final boolean completed = handler.waitForCompletion(5000);
            Assert.assertEquals(completed, true);
        } catch (Exception ignored) {
        }
    }

    public void testSimpleWithExceptionAndFail() {
        try {
            final MyEventHandler handler = new MyEventHandler(1, 4);
            eventBus.register(handler);

            eventBus.post(new MyEvent("my-event", 1L, "MY_EVENT_TYPE", 1L, 2L, UUID.randomUUID()));
            final boolean completed = handler.waitForCompletion(5000);
            Assert.assertEquals(completed, false);
        } catch (Exception ignored) {
        }
    }


    public void testSimple() {
        try {
            final int nbEvents = 5;
            final MyEventHandler handler = new MyEventHandler(nbEvents, 0);
            eventBus.register(handler);

            for (int i = 0; i < nbEvents; i++) {
                eventBus.post(new MyEvent("my-event", (long) i, "MY_EVENT_TYPE", 1L, 2L, UUID.randomUUID()));
            }
            final boolean completed = handler.waitForCompletion(10000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("", e);
        }
    }

    public void testDifferentType() {
        try {
            final MyEventHandler handler = new MyEventHandler(1, 0);
            eventBus.register(handler);

            for (int i = 0; i < 5; i++) {
                eventBus.post(new MyOtherEvent("my-other-event", (double) i, "MY_EVENT_TYPE", 1L, 2L, UUID.randomUUID()));
            }
            eventBus.post(new MyEvent("my-event", 11l, "MY_EVENT_TYPE", 1L, 2L, UUID.randomUUID()));

            final boolean completed = handler.waitForCompletion(10000);
            Assert.assertEquals(completed, true);
        } catch (Exception e) {
            Assert.fail("", e);
        }
    }
}
