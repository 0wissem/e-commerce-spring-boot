package org.example.notificationservice.notification.infrastructure;

import org.example.notificationservice.notification.domain.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The email adapter against a REAL SMTP server (Mailpit in a container), then Mailpit's HTTP API
 * to read what actually arrived. The consumer test mocks the sender; this is the test that proves
 * the wire works — a mocked boundary can't catch a wrong port, a missing From, or an SMTP error.
 */
@Testcontainers
class EmailNotificationSenderTest {

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.27")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    private static Notification notification() {
        return Notification.stockLow("e1", "p1", "Mechanical Keyboard", 3, 5,
                Instant.parse("2026-09-08T10:00:00Z"), "stock-team@test.local");
    }

    @Test
    @DisplayName("sends a real email: recipient, sender, subject and body all arrive")
    void sendsEmailThroughSmtp() throws Exception {
        EmailNotificationSender sender = new EmailNotificationSender(
                mailSender(MAILPIT.getHost(), MAILPIT.getMappedPort(1025)), "no-reply@test.local");

        sender.send(notification());

        JsonNode messages = JsonMapper.builder().build().readTree(get("/api/v1/messages"));
        assertThat(messages.get("total").asInt()).isEqualTo(1);
        JsonNode message = messages.get("messages").get(0);
        assertThat(message.get("Subject").asString()).isEqualTo("Low stock: Mechanical Keyboard (3 left)");
        assertThat(message.get("From").get("Address").asString()).isEqualTo("no-reply@test.local");
        assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo("stock-team@test.local");
        assertThat(message.get("Snippet").asString()).contains("dropped to 3 unit(s)");
    }

    @Test
    @DisplayName("SMTP unreachable: throws instead of failing silently (so the event is retried)")
    void unreachableSmtp_throws() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();   // grab a free port, then close it: nothing listens
        }
        EmailNotificationSender sender = new EmailNotificationSender(
                mailSender("localhost", closedPort), "no-reply@test.local");

        assertThatThrownBy(() -> sender.send(notification())).isInstanceOf(MailException.class);
    }

    private static JavaMailSenderImpl mailSender(String host, int port) {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host);
        mail.setPort(port);
        Properties props = new Properties();
        props.put("mail.smtp.connectiontimeout", "2000");
        props.put("mail.smtp.timeout", "2000");
        mail.setJavaMailProperties(props);
        return mail;
    }

    private static String get(String path) throws Exception {
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
        return HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
                .body();
    }
}
