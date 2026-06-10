package com.synapse.platform.user.service;

import com.synapse.platform.global.exception.BusinessException;

public class UserSelfServiceException extends BusinessException {

    private UserSelfServiceException(String errorCode, int status, String message) {
        super(errorCode, status, message);
    }

    public static UserSelfServiceException passwordLoginUnavailable() {
        return new UserSelfServiceException(
                "PLAT-USER-001",
                400,
                "Password login is not enabled for this account");
    }

    public static UserSelfServiceException invalidCurrentPassword() {
        return new UserSelfServiceException(
                "PLAT-USER-002",
                400,
                "Current password is invalid");
    }
}
