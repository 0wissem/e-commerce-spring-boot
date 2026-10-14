package org.example.notificationservice.notification.infrastructure.messaging;

/**
 * Our OWN dead-letter headers.
 *
 * Spring's kafka_dlt-exception-* headers are written by the blocking DefaultErrorHandler but not by
 * the retry-topic recoverer, which forwards only kafka_original-topic — switching to non-blocking
 * retries silently emptied the "why did this die?" field. Rather than depend on which framework
 * component happens to be in the path, the service stamps the two things an operator needs and owns
 * the names. They survive a Spring Kafka upgrade.
 */
public final class FailureHeaders {

    public static final String FAILURE_CLASS = "x-failure-class";
    public static final String FAILURE_MESSAGE = "x-failure-message";

    private FailureHeaders() {
    }
}
