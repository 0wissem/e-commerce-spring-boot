package org.example.notificationservice.notification.application;

public enum HandlingOutcome {
    /** First time we saw this event (or a previous attempt failed): the notification went out. */
    SENT,
    /** Already sent for this event id: redelivery acknowledged, nothing sent. */
    DUPLICATE
}
