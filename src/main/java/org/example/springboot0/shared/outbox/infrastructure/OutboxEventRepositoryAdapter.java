package org.example.springboot0.shared.outbox.infrastructure;

import org.example.springboot0.shared.outbox.domain.IOutboxEventRepository;
import org.example.springboot0.shared.outbox.domain.OutboxEvent;
import org.example.springboot0.shared.outbox.domain.OutboxEventStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxEventRepositoryAdapter implements IOutboxEventRepository {

    private final OutboxEventJpaRepository jpa;

    public OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(OutboxEvent event) {
        jpa.save(event);
    }

    @Override
    public List<OutboxEvent> findPending() {
        return jpa.findByStatus(OutboxEventStatus.PENDING);
    }
}