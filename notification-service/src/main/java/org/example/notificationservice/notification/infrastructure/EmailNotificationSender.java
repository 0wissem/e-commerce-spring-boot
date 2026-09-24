package org.example.notificationservice.notification.infrastructure;

import org.example.notificationservice.notification.domain.INotificationSender;
import org.example.notificationservice.notification.domain.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/**
 * Email adapter. Locally the SMTP server is Mailpit, which accepts everything and shows it in a
 * web inbox instead of delivering it.
 *
 * Failures (SMTP down, timeout) surface as MailException and are left to propagate: the
 * notification stays PENDING and Kafka redelivers. SMTP timeouts are set in application.properties
 * so a hung mail server fails fast instead of blocking a consumer thread indefinitely.
 */
@Component
public class EmailNotificationSender implements INotificationSender {

    private final MailSender mailSender;
    private final String from;

    public EmailNotificationSender(MailSender mailSender, @Value("${notification.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(Notification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.getRecipient());
        message.setSubject(notification.subject());
        message.setText(notification.body());
        mailSender.send(message);
    }
}
