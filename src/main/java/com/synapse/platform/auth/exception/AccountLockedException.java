package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class AccountLockedException extends BusinessException {

    public AccountLockedException() {
        super("PLAT-009-004", 423, "계정이 잠겼습니다. 잠시 후 다시 시도하세요");
    }
}
