package org.example.notificationservice.notification.application;

import org.example.notificationservice.notification.application.dto.StockLowEvent;
import org.example.notificationservice.notification.domain.DuplicateEventException;
import org.example.notificationservice.notification.domain.INotificationRepository;
import org.example.notificationservice.notification.domain.INotificationSender;
import org.example.notificationservice.notification.domain.Notification;
import org.example.notificationservice.notification.domain.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UNIT test of the idempotent consumer logic: repository and sender mocked, no Spring, no Kafka. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private INotificationRepository repository;
    @Mock private INotificationSender sender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, sender, new NotificationMapper(), "stock-team@test.local");
    }

    private static StockLowEvent event(String eventId) {
        return new StockLowEvent(eventId, 1, "p1", "Keyboard", 4, 5, Instant.parse("2026-09-08T10:00:00Z"));
    }

    @Test
    @DisplayName("first delivery: inserts PENDING, sends, then marks SENT")
    void firstDelivery_sendsAndMarksSent() {
        when(repository.findByEventId("e1")).thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        HandlingOutcome outcome = service.handleStockLow(event("e1"));

        assertThat(outcome).isEqualTo(HandlingOutcome.SENT);
        verify(sender).send(any(Notification.class));
        verify(repository).insert(any(Notification.class));
    }

    @Test
    @DisplayName("redelivery of an already SENT event: nothing is sent again")
    void duplicateDelivery_isIgnored() {
        Notification alreadySent = Notification.stockLow("e1", "p1", "Keyboard", 4, 5, Instant.now(), "x@test");
        alreadySent.markSent(Instant.now());
        when(repository.findByEventId("e1")).thenReturn(Optional.of(alreadySent));

        HandlingOutcome outcome = service.handleStockLow(event("e1"));

        assertThat(outcome).isEqualTo(HandlingOutcome.DUPLICATE);
        verifyNoInteractions(sender);
        verify(repository, never()).insert(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("redelivery after a FAILED send: the PENDING row is reused and the send is retried")
    void redeliveryAfterFailure_retriesTheSend() {
        Notification pending = Notification.stockLow("e1", "p1", "Keyboard", 4, 5, Instant.now(), "x@test");
        pending.recordAttempt();   // one earlier attempt that failed
        when(repository.findByEventId("e1")).thenReturn(Optional.of(pending));

        HandlingOutcome outcome = service.handleStockLow(event("e1"));

        assertThat(outcome).isEqualTo(HandlingOutcome.SENT);
        assertThat(pending.getAttempts()).isEqualTo(2);
        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("sender fails: the exception propagates (so Kafka retries) and the row stays PENDING")
    void senderFailure_propagates_andLeavesPending() {
        when(repository.findByEventId("e1")).thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new MailSendException("SMTP down")).when(sender).send(any());

        assertThatThrownBy(() -> service.handleStockLow(event("e1"))).isInstanceOf(MailSendException.class);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(n ->
                n.getStatus() == NotificationStatus.PENDING && n.getAttempts() == 1));
    }

    @Test
    @DisplayName("two deliveries race past the lookup: the loser reads the winner's row instead of failing")
    void insertRaceLost_readsWinnersRow() {
        Notification winnersRow = Notification.stockLow("e1", "p1", "Keyboard", 4, 5, Instant.now(), "x@test");
        winnersRow.markSent(Instant.now());
        when(repository.findByEventId("e1"))
                .thenReturn(Optional.empty())            // our lookup: not there yet
                .thenReturn(Optional.of(winnersRow));    // after the insert lost: the winner's row
        when(repository.insert(any())).thenThrow(new DuplicateEventException("e1", null));

        HandlingOutcome outcome = service.handleStockLow(event("e1"));

        assertThat(outcome).isEqualTo(HandlingOutcome.DUPLICATE);
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("event without eventId is PERMANENTLY invalid: rejected before touching anything")
    void missingEventId_isInvalid() {
        StockLowEvent noId = new StockLowEvent(null, 1, "p1", "Keyboard", 4, 5, Instant.now());

        assertThatThrownBy(() -> service.handleStockLow(noId)).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(repository, sender);
    }

    @Test
    @DisplayName("event without productId is PERMANENTLY invalid")
    void missingProductId_isInvalid() {
        StockLowEvent noProduct = new StockLowEvent("e1", 1, " ", "Keyboard", 4, 5, Instant.now());

        assertThatThrownBy(() -> service.handleStockLow(noProduct)).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(repository, sender);
    }
}
