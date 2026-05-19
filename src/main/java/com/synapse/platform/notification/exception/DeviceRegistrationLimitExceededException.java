package com.synapse.platform.notification.exception;

import com.synapse.platform.global.exception.BusinessException;

public class DeviceRegistrationLimitExceededException extends BusinessException {

    public DeviceRegistrationLimitExceededException() {
        super("PLAT-NOTIFICATION-001", 409, "Device registration limit exceeded (max 5)");
    }
}
