package com.synapse.platform.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.entity.Platform;
import com.synapse.platform.notification.exception.DeviceRegistrationLimitExceededException;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import com.synapse.platform.notification.service.DeviceTokenService;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import jakarta.persistence.EntityNotFoundException;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private UserApi userApi;

    @Test
    void register_newTokenWhenUserHasFiveDevices_shouldThrowLimitExceeded() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(userApi.findById(userId)).willReturn(Optional.of(userInfo(userId, tenantId)));
        given(deviceTokenRepository.findByToken("new-token")).willReturn(Optional.empty());
        given(deviceTokenRepository.countByUserId(userId)).willReturn(5L);

        assertThatThrownBy(() -> service.register(userId, "new-token", Platform.ANDROID))
                .isInstanceOf(DeviceRegistrationLimitExceededException.class);
        verify(deviceTokenRepository, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void register_existingToken_shouldSkipLimitCheckAndUpsert() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(userApi.findById(userId)).willReturn(Optional.of(userInfo(userId, tenantId)));
        given(deviceTokenRepository.findByToken("existing-token"))
                .willReturn(Optional.of(deviceToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));

        service.register(userId, "existing-token", Platform.IOS);

        verify(deviceTokenRepository, never()).countByUserId(userId);
        verify(deviceTokenRepository).upsert(any(UUID.class), any(UUID.class), any(UUID.class), any(), any());
    }

    @Test
    void register_newToken_shouldResolveTenantAndUpsert() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(userApi.findById(userId)).willReturn(Optional.of(userInfo(userId, tenantId)));
        given(deviceTokenRepository.findByToken("new-token")).willReturn(Optional.empty());
        given(deviceTokenRepository.countByUserId(userId)).willReturn(4L);

        service.register(userId, "new-token", Platform.WEB);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(deviceTokenRepository).upsert(idCaptor.capture(), any(), any(), any(), any());
        verify(deviceTokenRepository).upsert(idCaptor.getValue(), tenantId, userId, "new-token", "web");
    }

    @Test
    void register_missingUser_shouldThrowEntityNotFoundException() {
        UUID userId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(userApi.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(userId, "new-token", Platform.WEB))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");
        verify(deviceTokenRepository, never()).findByToken(any());
    }

    @Test
    void unregister_ownDevice_shouldDelete() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(deviceTokenRepository.findById(deviceId))
                .willReturn(Optional.of(deviceToken(deviceId, UUID.randomUUID(), userId)));

        service.unregister(userId, deviceId);

        verify(deviceTokenRepository).deleteById(deviceId);
    }

    @Test
    void unregister_otherUsersDevice_shouldThrowAccessDeniedException() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(deviceTokenRepository.findById(deviceId))
                .willReturn(Optional.of(deviceToken(deviceId, UUID.randomUUID(), ownerId)));

        assertThatThrownBy(() -> service.unregister(requesterId, deviceId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not owner");
        verify(deviceTokenRepository, never()).deleteById(deviceId);
    }

    @Test
    void unregister_missingDevice_shouldThrowEntityNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        DeviceTokenService service = service();
        given(deviceTokenRepository.findById(deviceId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unregister(userId, deviceId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Device not found");
    }

    private DeviceTokenService service() {
        return new DeviceTokenService(deviceTokenRepository, userApi);
    }

    private static UserInfo userInfo(UUID userId, UUID tenantId) {
        return new UserInfo(userId, "user@example.com", "User", tenantId);
    }

    private static DeviceToken deviceToken(UUID deviceId, UUID tenantId, UUID userId) {
        DeviceToken deviceToken = newDeviceToken();
        ReflectionTestUtils.setField(deviceToken, "id", deviceId);
        ReflectionTestUtils.setField(deviceToken, "tenantId", tenantId);
        ReflectionTestUtils.setField(deviceToken, "userId", userId);
        ReflectionTestUtils.setField(deviceToken, "token", "token-" + deviceId);
        ReflectionTestUtils.setField(deviceToken, "platform", Platform.ANDROID);
        ReflectionTestUtils.setField(deviceToken, "isActive", true);
        return deviceToken;
    }

    private static DeviceToken newDeviceToken() {
        try {
            Constructor<DeviceToken> constructor = DeviceToken.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create DeviceToken test fixture", exception);
        }
    }
}
