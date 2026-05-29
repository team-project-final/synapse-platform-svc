package com.synapse.platform.auth.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.user.api.UserSessionsRevocationRequested;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserSessionRevocationListenerTest {

    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    @Test
    void handle_shouldDeleteRefreshTokensForUser() {
        UUID userId = UUID.randomUUID();

        new UserSessionRevocationListener(refreshTokenService)
                .handle(new UserSessionsRevocationRequested(userId));

        verify(refreshTokenService).delete(userId);
    }
}
