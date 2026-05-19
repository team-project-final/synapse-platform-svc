package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class UnauthorizedTokenException extends BusinessException {

    public UnauthorizedTokenException(String message) {
        super("PLAT-002", 401, message);
    }
}
