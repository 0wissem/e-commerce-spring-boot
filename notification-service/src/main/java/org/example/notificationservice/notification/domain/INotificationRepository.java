package org.example.notificationservice.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/** Port: persistence for notifications. */
public interface INotificationRepository {

    Notification save(Notification notification);

    /**
     * Inserts a NEW notification and fails with {@link DuplicateEventException} if one already
     * exists for the same event id. Separate from {@link #save} because "insert or tell me it's a
     * duplicate" is the idempotency contract, not a generic save.
     */
    Notification insert(Notification notification);

    Optional<Notification> findById(String id);

    Optional<Notification> findByEventId(String eventId);

    Page<Notification> findAll(Pageable pageable);
}
