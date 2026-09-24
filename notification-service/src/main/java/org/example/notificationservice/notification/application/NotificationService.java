package org.example.notificationservice.notification.application;

import org.example.notificationservice.notification.application.dto.NotificationResponse;
import org.example.notificationservice.notification.application.dto.StockLowEvent;
import org.example.notificationservice.notification.domain.DuplicateEventException;
import org.example.notificationservice.notification.domain.INotificationRepository;
import org.example.notificationservice.notification.domain.INotificationSender;
import org.example.notificationservice.notification.domain.Notification;
import org.example.notificationservice.shared.exception.ResourceNotFoundException;
import org.example.notificationservice.shared.response.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * The IDEMPOTENT CONSUMER.
 *
 * Kafka guarantees at-least-once delivery, so this method must give the same result whether an
 * event arrives once or five times. Duplicates are normal, not exotic: a producer retry, a
 * consumer that crashed after sending the email but before committing its offset, a rebalance.
 *
 * DELIBERATELY NOT @Transactional. Sending an email inside a DB transaction would pin a connection
 * for the whole SMTP round trip — the exact HTTP-inside-a-transaction bug fixed in order-service.
 * Each repository call below is its own short transaction; the email happens between them.
 */
@Service
public class NotificationService implements INotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final INotificationRepository repository;
    private final INotificationSender sender;
    private final NotificationMapper mapper;
    private final String stockLowRecipient;

    public NotificationService(INotificationRepository repository,
                               INotificationSender sender,
                               NotificationMapper mapper,
                               @Value("${notification.stock-low.recipient}") String stockLowRecipient) {
        this.repository = repository;
        this.sender = sender;
        this.mapper = mapper;
        this.stockLowRecipient = stockLowRecipient;
    }

    @Override
    public HandlingOutcome handleStockLow(StockLowEvent event) {
        validate(event);

        Notification notification = findOrCreate(event);
        if (notification.isSent()) {
            log.info("Duplicate delivery of event {} ignored (already sent at {})",
                    event.eventId(), notification.getSentAt());
            return HandlingOutcome.DUPLICATE;
        }

        notification.recordAttempt();
        repository.save(notification);

        // May throw. The row stays PENDING, the exception reaches the Kafka error handler, the
        // record is redelivered after a backoff, and we come back here and try again.
        sender.send(notification);

        notification.markSent(Instant.now());
        repository.save(notification);
        log.info("Low-stock notification {} sent for product {} (attempt {})",
                notification.getId(), notification.getProductId(), notification.getAttempts());
        return HandlingOutcome.SENT;
    }

    /**
     * Check, then insert, then — if the insert loses a race — read the winner.
     *
     * The lookup alone is not enough: two deliveries of the same event can both see "absent" and
     * both try to insert. The unique constraint lets exactly one win; the loser lands here and
     * continues with the row the winner created.
     */
    private Notification findOrCreate(StockLowEvent event) {
        return repository.findByEventId(event.eventId()).orElseGet(() -> {
            Notification fresh = Notification.stockLow(event.eventId(), event.productId(),
                    event.productName(), event.stockQuantity(), event.threshold(),
                    event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                    stockLowRecipient);
            try {
                return repository.insert(fresh);
            } catch (DuplicateEventException raceLost) {
                return repository.findByEventId(event.eventId()).orElseThrow(() -> raceLost);
            }
        });
    }

    private void validate(StockLowEvent event) {
        if (event == null) {
            throw new InvalidEventException("Empty stock.low event");
        }
        if (isBlank(event.eventId())) {
            throw new InvalidEventException("stock.low event without eventId: cannot be deduplicated");
        }
        if (isBlank(event.productId())) {
            throw new InvalidEventException("stock.low event " + event.eventId() + " without productId");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public PageResponse<NotificationResponse> getAll(int page, int size) {
        return PageResponse.from(repository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(mapper::toResponse));
    }

    @Override
    public NotificationResponse getById(String id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }
}
