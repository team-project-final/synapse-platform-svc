package com.synapse.platform.auth.service;

import com.synapse.platform.user.api.UserInfo;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = PasswordResetCodeSender.class, ignored = NoopPasswordResetCodeSender.class)
public class NoopPasswordResetCodeSender implements PasswordResetCodeSender {

    @Override
    public void send(UserInfo user, String code, OffsetDateTime expiresAt) {
        // Local fallback. Production delivery is provided by KafkaPasswordResetCodeSender.
    }
}
