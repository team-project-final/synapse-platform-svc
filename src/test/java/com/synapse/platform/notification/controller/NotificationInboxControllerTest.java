package com.synapse.platform.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import com.synapse.platform.notification.dto.response.NotificationItemResponse;
import com.synapse.platform.notification.dto.response.NotificationPageResponse;
import com.synapse.platform.notification.dto.response.NotificationReadAllResponse;
import com.synapse.platform.notification.dto.response.NotificationUnreadCountResponse;
import com.synapse.platform.notification.service.NotificationInboxService;
import com.synapse.platform.notification.service.NotificationSettingsService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationInboxControllerTest {

    private final NotificationInboxService inboxService = org.mockito.Mockito.mock(NotificationInboxService.class);
    private final NotificationSettingsService settingsService =
            org.mockito.Mockito.mock(NotificationSettingsService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationInboxController(inboxService, settingsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturnPagedNotifications() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        given(inboxService.list(eq(userId), any(Pageable.class))).willReturn(new NotificationPageResponse(
                List.of(new NotificationItemResponse(
                        notificationId,
                        "ACHIEVEMENT_UNLOCKED",
                        "Level up",
                        "You reached level 2.",
                        false,
                        null,
                        Instant.parse("2026-06-09T05:00:00Z"),
                        Instant.parse("2026-06-09T05:00:01Z"))),
                0,
                10,
                1,
                1));

        mockMvc.perform(get("/api/v1/notifications?page=0&size=10")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.items[0].type").value("ACHIEVEMENT_UNLOCKED"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).list(eq(userId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void countUnread_shouldReturnCount() throws Exception {
        UUID userId = UUID.randomUUID();
        given(inboxService.countUnread(userId)).willReturn(new NotificationUnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markRead_shouldReturnUpdatedNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-06-09T05:10:00Z");
        given(inboxService.markRead(userId, notificationId)).willReturn(new NotificationItemResponse(
                notificationId,
                "CARD_REVIEW_DUE",
                "Review due",
                "A card is ready.",
                true,
                readAt,
                Instant.parse("2026-06-09T05:00:00Z"),
                Instant.parse("2026-06-09T05:00:01Z")));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").value(readAt.toString()));
    }

    @Test
    void markAllRead_shouldReturnUpdatedCount() throws Exception {
        UUID userId = UUID.randomUUID();
        given(inboxService.markAllRead(userId)).willReturn(new NotificationReadAllResponse(2));

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));
    }

    @Test
    void getSettings_shouldReturnPreferencesObject() throws Exception {
        UUID userId = UUID.randomUUID();
        given(settingsService.getSettings(userId)).willReturn(Map.of(
                "quietHours", Map.of("start", "22:00", "end", "08:00")));

        mockMvc.perform(get("/api/v1/notifications/settings")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHours.start").value("22:00"));
    }

    @Test
    void updateSettings_shouldDelegatePreferencesObject() throws Exception {
        UUID userId = UUID.randomUUID();
        Map<String, Object> preferences = Map.of(
                "quietHours", Map.of("start", "23:00", "end", "07:30"));
        given(settingsService.updateSettings(eq(userId), any())).willReturn(preferences);

        mockMvc.perform(put("/api/v1/notifications/settings")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferences)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHours.end").value("07:30"));

        verify(settingsService).updateSettings(userId, preferences);
    }

    @Test
    void list_invalidPageSize_shouldReturnBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/notifications?size=101")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAT-400"));
    }

    @Test
    void list_malformedPrincipal_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .principal(new TestingAuthenticationToken("not-a-uuid", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAT-401"));
    }
}
