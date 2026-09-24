package org.example.notificationservice.notification.domain;

/** The unique constraint on event_id rejected an insert: another delivery got there first. */
public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String eventId, Throwable cause) {
        super("Notification already exists for event " + eventId, cause);
    }
}
