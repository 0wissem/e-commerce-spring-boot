package org.example.notificationservice.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One notification we owe someone, and whether it has gone out.
 *
 * The row is written BEFORE the email is sent (status PENDING) and flipped to SENT after. That
 * ordering is what makes redelivery safe in both failure directions:
 *
 *   crash after the email, before SENT is saved -> redelivered, still PENDING -> email sent again
 *   crash before the email                      -> redelivered, still PENDING -> email sent
 *
 * So the guarantee is at-least-once email, never zero. Exactly-once is impossible here: SMTP
 * cannot take part in our database transaction. Choosing "maybe twice" over "maybe never" is
 * the right call for an alert.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private String id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private int threshold;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
        // for JPA
    }

    public static Notification stockLow(String eventId, String productId, String productName,
                                         int stockQuantity, int threshold, Instant occurredAt,
                                         String recipient) {
        Notification n = new Notification();
        n.id = UUID.randomUUID().toString();
        n.eventId = eventId;
        n.type = NotificationType.STOCK_LOW;
        n.status = NotificationStatus.PENDING;
        n.productId = productId;
        n.productName = productName;
        n.stockQuantity = stockQuantity;
        n.threshold = threshold;
        n.occurredAt = occurredAt;
        n.recipient = recipient;
        n.createdAt = Instant.now();
        return n;
    }

    public void recordAttempt() {
        attempts++;
    }

    public void markSent(Instant when) {
        if (status == NotificationStatus.SENT) {
            throw new IllegalStateException("Notification " + id + " is already sent");
        }
        status = NotificationStatus.SENT;
        sentAt = when;
    }

    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }

    public String subject() {
        return "Low stock: " + (productName != null ? productName : productId)
                + " (" + stockQuantity + " left)";
    }

    public String body() {
        return """
                Stock for "%s" (id %s) dropped to %d unit(s), at or below the alert threshold of %d.

                Detected at %s. Consider restocking.
                """.formatted(productName, productId, stockQuantity, threshold, occurredAt);
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public NotificationType getType() { return type; }
    public NotificationStatus getStatus() { return status; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getStockQuantity() { return stockQuantity; }
    public int getThreshold() { return threshold; }
    public String getRecipient() { return recipient; }
    public int getAttempts() { return attempts; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
