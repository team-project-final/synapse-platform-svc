package com.synapse.platform.notification.exception;

import com.synapse.platform.global.exception.BusinessException;

public class SesEmailException extends BusinessException {

    public SesEmailException(String detail) {
        super("PLAT-NOTIFICATION-002", 500, "SES email send failed: " + detail);
    }
}
