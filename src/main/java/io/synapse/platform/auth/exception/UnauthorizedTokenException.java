package io.synapse.platform.auth.exception;

import io.synapse.platform.shared.exception.BusinessException;

public class UnauthorizedTokenException extends BusinessException {

    public UnauthorizedTokenException(String message) {
        super("PLAT-002", 401, message);
    }
}
