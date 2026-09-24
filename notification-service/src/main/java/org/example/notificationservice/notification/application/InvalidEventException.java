package org.example.notificationservice.notification.application;

/**
 * The event parsed but is unusable (missing id, missing product). A PERMANENT failure: retrying
 * the same bytes can never succeed, so the error handler sends it straight to the dead-letter
 * topic without burning the retry budget.
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
