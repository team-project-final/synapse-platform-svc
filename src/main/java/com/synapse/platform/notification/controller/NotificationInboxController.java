package com.synapse.platform.notification.controller;

import com.synapse.platform.notification.dto.response.NotificationItemResponse;
import com.synapse.platform.notification.dto.response.NotificationPageResponse;
import com.synapse.platform.notification.dto.response.NotificationReadAllResponse;
import com.synapse.platform.notification.dto.response.NotificationUnreadCountResponse;
import com.synapse.platform.notification.service.NotificationInboxService;
import com.synapse.platform.notification.service.NotificationSettingsService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationInboxController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationInboxService notificationInboxService;
    private final NotificationSettingsService notificationSettingsService;

    public NotificationInboxController(
            NotificationInboxService notificationInboxService,
            NotificationSettingsService notificationSettingsService) {
        this.notificationInboxService = notificationInboxService;
        this.notificationSettingsService = notificationSettingsService;
    }

    @GetMapping
    public NotificationPageResponse list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationInboxService.list(currentUserId(authentication), pageable(page, size));
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse countUnread(Authentication authentication) {
        return notificationInboxService.countUnread(currentUserId(authentication));
    }

    @PutMapping("/{id}/read")
    public NotificationItemResponse markRead(
            Authentication authentication,
            @PathVariable UUID id) {
        return notificationInboxService.markRead(currentUserId(authentication), id);
    }

    @PostMapping("/read-all")
    public NotificationReadAllResponse markAllRead(Authentication authentication) {
        return notificationInboxService.markAllRead(currentUserId(authentication));
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(Authentication authentication) {
        return notificationSettingsService.getSettings(currentUserId(authentication));
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(
            Authentication authentication,
            @RequestBody Map<String, Object> preferences) {
        return notificationSettingsService.updateSettings(currentUserId(authentication), preferences);
    }

    private Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
