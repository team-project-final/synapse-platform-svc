package com.synapse.platform.notification.service;

import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.entity.Platform;
import com.synapse.platform.notification.exception.DeviceRegistrationLimitExceededException;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenService {

    private static final int DEVICE_REGISTRATION_LIMIT = 5;

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserApi userApi;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository, UserApi userApi) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.userApi = userApi;
    }

    @Transactional
    public void register(UUID userId, String token, Platform platform) {
        UUID tenantId = resolveTenantId(userId);
        boolean isNewToken = deviceTokenRepository.findByToken(token).isEmpty();
        if (isNewToken && deviceTokenRepository.countByUserId(userId) >= DEVICE_REGISTRATION_LIMIT) {
            throw new DeviceRegistrationLimitExceededException();
        }
        deviceTokenRepository.upsert(UUID.randomUUID(), tenantId, userId, token, platform.getValue());
    }

    @Transactional
    public void unregister(UUID userId, UUID deviceId) {
        DeviceToken deviceToken = deviceTokenRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found"));
        if (!deviceToken.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not owner");
        }
        deviceTokenRepository.deleteById(deviceId);
    }

    private UUID resolveTenantId(UUID userId) {
        return userApi.findById(userId)
                .map(UserInfo::defaultTenantId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
