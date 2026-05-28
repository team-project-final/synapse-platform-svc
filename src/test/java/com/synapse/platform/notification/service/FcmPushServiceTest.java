package com.synapse.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.entity.Platform;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private BatchResponse batchResponse;

    @Test
    void sendToUser_activeDevices_shouldSendMulticastAndReturnSuccessCount() throws Exception {
        UUID userId = UUID.randomUUID();
        given(deviceTokenRepository.findByUserId(userId))
                .willReturn(List.of(deviceToken("token-1", true), deviceToken("token-2", true)));
        given(batchResponse.getFailureCount()).willReturn(0);
        given(batchResponse.getSuccessCount()).willReturn(2);
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(batchResponse);

        try (MockedStatic<FirebaseMessaging> firebase = Mockito.mockStatic(FirebaseMessaging.class)) {
            firebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            int sent = service().sendToUser(userId, "Title", "Body", Map.of("type", "CARD_REVIEW_DUE"));

            assertThat(sent).isEqualTo(2);
            verify(firebaseMessaging).sendEachForMulticast(any());
        }
    }

    @Test
    void sendToUser_noActiveDevices_shouldReturnZeroWithoutCallingFirebase() throws Exception {
        UUID userId = UUID.randomUUID();
        given(deviceTokenRepository.findByUserId(userId))
                .willReturn(List.of(deviceToken("inactive-token", false)));

        try (MockedStatic<FirebaseMessaging> firebase = Mockito.mockStatic(FirebaseMessaging.class)) {
            int sent = service().sendToUser(userId, "Title", "Body", Map.of());

            assertThat(sent).isZero();
            firebase.verify(FirebaseMessaging::getInstance, never());
        }
    }

    @Test
    void sendToUser_firebaseThrows_shouldWrapRuntimeException() throws Exception {
        UUID userId = UUID.randomUUID();
        given(deviceTokenRepository.findByUserId(userId)).willReturn(List.of(deviceToken("token", true)));
        given(firebaseMessaging.sendEachForMulticast(any())).willThrow(new IllegalStateException("fcm down"));

        try (MockedStatic<FirebaseMessaging> firebase = Mockito.mockStatic(FirebaseMessaging.class)) {
            firebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            assertThatThrownBy(() -> service().sendToUser(userId, "Title", "Body", Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("FCM multicast failed");
        }
    }

    private FcmPushService service() {
        return new FcmPushService(deviceTokenRepository);
    }

    private static DeviceToken deviceToken(String token, boolean active) throws Exception {
        Constructor<DeviceToken> constructor = DeviceToken.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        DeviceToken deviceToken = constructor.newInstance();
        ReflectionTestUtils.setField(deviceToken, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(deviceToken, "tenantId", UUID.randomUUID());
        ReflectionTestUtils.setField(deviceToken, "userId", UUID.randomUUID());
        ReflectionTestUtils.setField(deviceToken, "token", token);
        ReflectionTestUtils.setField(deviceToken, "platform", Platform.ANDROID);
        ReflectionTestUtils.setField(deviceToken, "isActive", active);
        return deviceToken;
    }
}
