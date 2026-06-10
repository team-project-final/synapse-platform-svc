package com.synapse.platform.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.user.api.UserApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationSettingsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserApi userApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationSettingsService(UserApi userApi) {
        this.userApi = userApi;
    }

    public Map<String, Object> getSettings(UUID userId) {
        return parsePreferences(userApi.getNotificationPreferences(userId));
    }

    public Map<String, Object> updateSettings(UUID userId, Map<String, Object> preferences) {
        String json = toJson(mergeWithDefaults(preferences == null ? Map.of() : preferences));
        return parsePreferences(userApi.updateNotificationPreferences(userId, json));
    }

    private Map<String, Object> parsePreferences(String preferencesJson) {
        if (preferencesJson == null || preferencesJson.isBlank() || "{}".equals(preferencesJson.trim())) {
            return defaultPreferences();
        }
        try {
            Map<String, Object> preferences = objectMapper.readValue(preferencesJson, MAP_TYPE);
            return mergeWithDefaults(preferences);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid notification preferences", exception);
        }
    }

    private static Map<String, Object> mergeWithDefaults(Map<String, Object> preferences) {
        Map<String, Object> merged = defaultPreferences();
        Object categories = preferences.get("categories");
        if (categories instanceof Map<?, ?> categoryMap) {
            merged.put("categories", mergeCategories(categoryMap));
        } else if (categories != null) {
            throw badRequest("categories must be an object");
        }

        Object quietHours = preferences.get("quietHours");
        if (quietHours instanceof Map<?, ?> quietHoursMap) {
            merged.put("quietHours", mergeQuietHours(quietHoursMap));
        } else if (quietHours != null) {
            throw badRequest("quietHours must be an object");
        }
        return merged;
    }

    private static Map<String, Object> mergeCategories(Map<?, ?> categories) {
        Map<String, Object> merged = castObjectMap(defaultPreferences().get("categories"));
        for (Map.Entry<?, ?> entry : categories.entrySet()) {
            if (!(entry.getKey() instanceof String category)) {
                throw badRequest("category keys must be strings");
            }
            if (!(entry.getValue() instanceof Map<?, ?> channelMap)) {
                throw badRequest("category value must be an object");
            }
            Map<String, Object> base = castObjectMap(merged.getOrDefault(
                    category,
                    channelPreferences(true, true, true)));
            for (Map.Entry<?, ?> channelEntry : channelMap.entrySet()) {
                if (!(channelEntry.getKey() instanceof String channel)) {
                    throw badRequest("channel keys must be strings");
                }
                if (!(channelEntry.getValue() instanceof Boolean)) {
                    throw badRequest("channel value must be boolean");
                }
                base.put(channel, channelEntry.getValue());
            }
            merged.put(category, base);
        }
        return merged;
    }

    private static Map<String, Object> mergeQuietHours(Map<?, ?> quietHours) {
        Map<String, Object> merged = castObjectMap(defaultPreferences().get("quietHours"));
        for (Map.Entry<?, ?> entry : quietHours.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw badRequest("quietHours keys must be strings");
            }
            if (!"start".equals(key) && !"end".equals(key)) {
                continue;
            }
            if (!(entry.getValue() instanceof String)) {
                throw badRequest("quietHours value must be string");
            }
            merged.put(key, entry.getValue());
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castObjectMap(Object value) {
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private String toJson(Map<String, Object> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid notification preferences", exception);
        }
    }

    private static Map<String, Object> defaultPreferences() {
        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("reviewReminder", channelPreferences(true, false, true));
        categories.put("communityActivity", channelPreferences(true, true, true));
        categories.put("achievement", channelPreferences(true, false, true));
        categories.put("system", channelPreferences(false, true, true));

        Map<String, Object> quietHours = new LinkedHashMap<>();
        quietHours.put("start", "22:00");
        quietHours.put("end", "08:00");

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("categories", categories);
        preferences.put("quietHours", quietHours);
        return preferences;
    }

    private static Map<String, Object> channelPreferences(boolean push, boolean email, boolean inApp) {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("push", push);
        preferences.put("email", email);
        preferences.put("inApp", inApp);
        return preferences;
    }
}
