package io.synapse.platform.auth.exception;

import io.synapse.platform.common.exception.BusinessException;

public class OAuthProcessingException extends BusinessException {

    public OAuthProcessingException(String message) {
        super("PLAT-001", 400, message);
    }
}
