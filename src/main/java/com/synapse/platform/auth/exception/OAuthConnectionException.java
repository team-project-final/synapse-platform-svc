package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class OAuthConnectionException extends BusinessException {

    private OAuthConnectionException(String errorCode, int status, String message) {
        super(errorCode, status, message);
    }

    public static OAuthConnectionException notFound(String provider) {
        return new OAuthConnectionException(
                "PLAT-OAUTH-001",
                404,
                "OAuth connection not found: " + provider);
    }

    public static OAuthConnectionException lastLoginMethod() {
        return new OAuthConnectionException(
                "PLAT-OAUTH-002",
                400,
                "Cannot remove the last login method");
    }
}
