package org.example.springboot0.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class LowStockNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LowStockNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.notification.recipient}")
    private String recipient;

    @Value("${app.notification.low-stock-threshold}")
    private int threshold;

    public LowStockNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void notifyIfLowStock(String productName, int currentStock) {
        if (currentStock < threshold) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(recipient);
                message.setSubject("⚠️ Low Stock Alert: " + productName);
                message.setText(
                        "Product: " + productName + "\n" +
                        "Current stock: " + currentStock + " units\n" +
                        "Threshold: " + threshold + " units\n\n" +
                        "Please restock as soon as possible."
                );
                mailSender.send(message);
                log.info("Low stock alert sent for product: {}", productName);
            } catch (Exception e) {
                log.error("Failed to send low stock alert for product: {}", productName, e);
            }
        }
    }
}
