package org.example.notificationservice.notification.api;

import org.example.notificationservice.notification.application.INotificationService;
import org.example.notificationservice.notification.application.dto.NotificationResponse;
import org.example.notificationservice.notification.domain.NotificationStatus;
import org.example.notificationservice.notification.domain.NotificationType;
import org.example.notificationservice.shared.exception.GlobalExceptionHandler;
import org.example.notificationservice.shared.exception.ResourceNotFoundException;
import org.example.notificationservice.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer test with standalone MockMvc (controller + advice, no Spring context). */
class NotificationControllerTest {

    private final INotificationService service = mock(INotificationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static NotificationResponse sample() {
        return new NotificationResponse("n1", "e1", NotificationType.STOCK_LOW, NotificationStatus.SENT,
                "p1", "Keyboard", 4, 5, "stock-team@test.local", 1,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    @DisplayName("GET /api/notifications -> 200 with a page of notifications")
    void list_returnsPage() throws Exception {
        when(service.getAll(0, 20)).thenReturn(new PageResponse<>(List.of(sample()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].productId").value("p1"))
                .andExpect(jsonPath("$.data.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/notifications?size=500 -> 400, the page size is capped")
    void list_rejectsHugePage() throws Exception {
        mockMvc.perform(get("/api/notifications").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/notifications/{id} unknown -> 404")
    void getById_notFound() throws Exception {
        when(service.getById("nope")).thenThrow(new ResourceNotFoundException("Notification", "nope"));

        mockMvc.perform(get("/api/notifications/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
