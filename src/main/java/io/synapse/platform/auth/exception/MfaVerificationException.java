package io.synapse.platform.auth.exception;

import io.synapse.platform.shared.exception.BusinessException;

public class MfaVerificationException extends BusinessException {

    public MfaVerificationException(String message) {
        super("PLAT-003", 400, message);
    }
}
