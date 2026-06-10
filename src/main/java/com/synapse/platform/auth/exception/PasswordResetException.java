package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class PasswordResetException extends BusinessException {

    private PasswordResetException(String message) {
        super("PLAT-AUTH-070", 400, message);
    }

    public static PasswordResetException invalidOrExpired() {
        return new PasswordResetException("Password reset request is invalid or expired");
    }
}
