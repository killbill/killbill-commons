package com.ning.billing.notificationq;

public class NotificationQueueException extends Exception {

    public NotificationQueueException() {
    }

    public NotificationQueueException(String message) {
        super(message);
    }

    public NotificationQueueException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationQueueException(Throwable cause) {
        super(cause);
    }

    public NotificationQueueException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
