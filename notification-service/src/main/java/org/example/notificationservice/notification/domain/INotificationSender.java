package org.example.notificationservice.notification.domain;

/**
 * Port: deliver a notification to a human. Email today; SMS or Slack would be new adapters,
 * not changes to the service.
 *
 * Implementations throw on failure. The caller relies on that to leave the notification PENDING
 * and let Kafka redeliver.
 */
public interface INotificationSender {
    void send(Notification notification);
}
