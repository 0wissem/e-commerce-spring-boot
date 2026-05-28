package org.example.springboot0.shared.outbox.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private String id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(String id, String eventType, String source, String payload) {
        this.id = id;
        this.eventType = eventType;
        this.source = source;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public String getId()                  { return id; }
    public String getEventType()           { return eventType; }
    public String getSource()              { return source; }
    public String getPayload()             { return payload; }
    public OutboxEventStatus getStatus()   { return status; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    public void markSent() {
        this.status = OutboxEventStatus.SENT;
    }
}