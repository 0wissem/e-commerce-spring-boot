package org.example.springboot0.shared.outbox.infrastructure;

import org.example.springboot0.shared.outbox.domain.OutboxEvent;
import org.example.springboot0.shared.outbox.domain.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatus(OutboxEventStatus status);
}