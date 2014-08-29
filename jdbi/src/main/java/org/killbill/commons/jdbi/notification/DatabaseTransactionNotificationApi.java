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

package org.killbill.commons.jdbi.notification;

import java.util.Observable;
import java.util.Observer;

public class DatabaseTransactionNotificationApi {

    private final NotificationTransactionHandlerObservable observable;

    public DatabaseTransactionNotificationApi() {
        this.observable = new NotificationTransactionHandlerObservable();
    }

    public void registerForNotification(final Observer observer) {
        observable.addObserver(observer);
    }

    public void unregisterForNotification(final Observer observer) {
        observable.deleteObserver(observer);
    }

    //
    // Dispatch the event the observers right after the rollback/commit occurred.
    // Of course there is window of doom during which we could crash at this time, so observer
    // must know that it could happen and that state on disk (when commit occured) should then be retrieved.
    //
    public void dispatchNotification(final DatabaseTransactionEvent event) {
        //
        // Observer/Observable pattern is very poorly implemented...
        // The setChanged is need to for the dispatch to occur; but two threads racing each other
        // could end up in events being lost because the first one dispatching the event will reset the changed
        // to false which will end up in the second dispatch to be skipped... This is quite lame!
        //
        // As a result we synchronize both operations but this is sub optimal because then dispatch occurs
        // with lock being held. Yack...
        //
        // So, observers should not attempt lengthy operations so as to not end up serializing all operations,
        // which fortunately is our use case, but beware..
        synchronized (observable) {
            observable.setChanged();
            observable.notifyObservers(event);
        }
    }


    public static class NotificationTransactionHandlerObservable extends Observable {
        // Make the method visible...
        @Override
        public void setChanged() {
            super.setChanged();
        }
    }
}
