package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException() {
        super("PLAT-009-001", 409, "이미 가입된 이메일입니다");
    }
}
