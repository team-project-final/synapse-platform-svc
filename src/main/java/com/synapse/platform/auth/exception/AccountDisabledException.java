package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class AccountDisabledException extends BusinessException {

    public AccountDisabledException(String status) {
        super("PLAT-009-005", 401, "Account is " + status);
    }
}
