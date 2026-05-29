package com.synapse.platform.auth.service;

import com.synapse.platform.user.api.UserSessionsRevocationRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class UserSessionRevocationListener {

    private final RefreshTokenService refreshTokenService;

    public UserSessionRevocationListener(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @EventListener
    public void handle(UserSessionsRevocationRequested event) {
        refreshTokenService.delete(event.userId());
    }
}
