package com.synapse.platform.notification.dto.response;

import com.synapse.platform.notification.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Page;

public record NotificationPageResponse(
        List<NotificationItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public NotificationPageResponse {
        items = List.copyOf(items);
    }

    public static NotificationPageResponse from(Page<Notification> page) {
        return new NotificationPageResponse(
                page.getContent().stream()
                        .map(NotificationItemResponse::from)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public List<NotificationItemResponse> items() {
        return List.copyOf(items);
    }
}
