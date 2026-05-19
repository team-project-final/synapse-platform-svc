package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class OAuthProcessingException extends BusinessException {

    public OAuthProcessingException(String message) {
        super("PLAT-001", 400, message);
    }
}
