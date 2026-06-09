package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.entity.OAuthIdentity;
import com.synapse.platform.auth.exception.OAuthConnectionException;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.user.api.UserApi;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthConnectionServiceTest {

    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;

    @Mock
    private UserApi userApi;

    @Test
    void unlink_passwordUser_shouldDeleteConnection() {
        UUID userId = UUID.randomUUID();
        OAuthIdentity identity = OAuthIdentity.of(userId, "google", "google-1", "user@gmail.com");
        given(oauthIdentityRepository.findAllByUserIdForUpdate(userId)).willReturn(List.of(identity));
        given(userApi.hasPasswordLogin(userId)).willReturn(true);

        service().unlink(userId, "google");

        verify(oauthIdentityRepository).delete(identity);
    }

    @Test
    void unlink_lastOAuthWithoutPassword_shouldThrow() {
        UUID userId = UUID.randomUUID();
        OAuthIdentity identity = OAuthIdentity.of(userId, "google", "google-1", "user@gmail.com");
        given(oauthIdentityRepository.findAllByUserIdForUpdate(userId)).willReturn(List.of(identity));
        given(userApi.hasPasswordLogin(userId)).willReturn(false);

        assertThatThrownBy(() -> service().unlink(userId, "google"))
                .isInstanceOf(OAuthConnectionException.class);

        verify(oauthIdentityRepository, never()).delete(identity);
    }

    @Test
    void unlink_missingProvider_shouldThrowNotFound() {
        UUID userId = UUID.randomUUID();
        given(oauthIdentityRepository.findAllByUserIdForUpdate(userId)).willReturn(List.of());

        assertThatThrownBy(() -> service().unlink(userId, "github"))
                .isInstanceOf(OAuthConnectionException.class);
    }

    private OAuthConnectionService service() {
        return new OAuthConnectionService(oauthIdentityRepository, userApi);
    }
}
