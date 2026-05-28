package org.example.springboot0.shared.outbox.domain;

import java.util.List;

public interface IOutboxEventRepository {
    void save(OutboxEvent event);
    List<OutboxEvent> findPending();
}