package com.synapse.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.user.api.UserApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserApi userApi;

    @Test
    void getSettings_emptyPreferences_shouldReturnDefaults() {
        UUID userId = UUID.randomUUID();
        NotificationSettingsService service = service();
        given(userApi.getNotificationPreferences(userId)).willReturn("{}");

        Map<String, Object> result = service.getSettings(userId);

        assertThat(result).containsKeys("categories", "quietHours");
        assertThat(result.get("categories")).isInstanceOf(Map.class);
        assertThat(result.get("quietHours")).isEqualTo(Map.of("start", "22:00", "end", "08:00"));
    }

    @Test
    void getSettings_existingPartialPreferences_shouldMergeWithDefaults() {
        UUID userId = UUID.randomUUID();
        NotificationSettingsService service = service();
        given(userApi.getNotificationPreferences(userId)).willReturn("""
                {
                  "quietHours": {
                    "start": "23:00",
                    "end": "07:00"
                  }
                }
                """);

        Map<String, Object> result = service.getSettings(userId);

        assertThat(result.get("quietHours")).isEqualTo(Map.of("start", "23:00", "end", "07:00"));
        assertThat(result.get("categories")).isInstanceOf(Map.class);
    }

    @Test
    void updateSettings_partialPreferences_shouldPersistMergedJsonThroughUserApi() throws Exception {
        UUID userId = UUID.randomUUID();
        NotificationSettingsService service = service();
        Map<String, Object> preferences = Map.of(
                "quietHours", Map.of("start", "23:00", "end", "07:30"));
        given(userApi.updateNotificationPreferences(eq(userId), anyString()))
                .willAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> result = service.updateSettings(userId, preferences);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(userApi).updateNotificationPreferences(eq(userId), jsonCaptor.capture());
        Map<String, Object> saved = objectMapper.readValue(
                jsonCaptor.getValue(),
                new TypeReference<>() {
                });
        assertThat(saved.get("quietHours")).isEqualTo(Map.of("start", "23:00", "end", "07:30"));
        assertThat(saved.get("categories")).isInstanceOf(Map.class);
        assertThat(result).isEqualTo(saved);
    }

    @Test
    void updateSettings_invalidCategoryValue_shouldThrowBadRequest() {
        UUID userId = UUID.randomUUID();
        NotificationSettingsService service = service();

        assertThatThrownBy(() -> service.updateSettings(userId, Map.of("categories", "bad")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("categories must be an object");
    }

    private NotificationSettingsService service() {
        return new NotificationSettingsService(userApi);
    }
}
