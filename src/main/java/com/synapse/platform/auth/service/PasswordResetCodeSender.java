package com.synapse.platform.auth.service;

import com.synapse.platform.user.api.UserInfo;
import java.time.OffsetDateTime;

public interface PasswordResetCodeSender {

    void send(UserInfo user, String code, OffsetDateTime expiresAt);
}
