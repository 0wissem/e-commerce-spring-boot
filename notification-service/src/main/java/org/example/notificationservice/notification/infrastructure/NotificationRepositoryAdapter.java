package org.example.notificationservice.notification.infrastructure;

import org.example.notificationservice.notification.domain.DuplicateEventException;
import org.example.notificationservice.notification.domain.INotificationRepository;
import org.example.notificationservice.notification.domain.Notification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class NotificationRepositoryAdapter implements INotificationRepository {

    private static final String EVENT_ID_CONSTRAINT = "uq_notifications_event_id";

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification save(Notification notification) {
        return jpa.save(notification);
    }

    /**
     * saveAndFlush, not save: the INSERT must hit the database NOW so the unique-constraint
     * violation surfaces here, where it can be translated — not later at some unrelated commit.
     *
     * Only the event_id constraint means "duplicate". Any other integrity violation is a real bug
     * and is rethrown untouched.
     */
    @Override
    public Notification insert(Notification notification) {
        try {
            return jpa.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(EVENT_ID_CONSTRAINT)) {
                throw new DuplicateEventException(notification.getEventId(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<Notification> findById(String id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Notification> findByEventId(String eventId) {
        return jpa.findByEventId(eventId);
    }

    @Override
    public Page<Notification> findAll(Pageable pageable) {
        return jpa.findAll(pageable);
    }
}
