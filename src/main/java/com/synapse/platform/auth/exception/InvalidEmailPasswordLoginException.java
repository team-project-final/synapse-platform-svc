package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class InvalidEmailPasswordLoginException extends BusinessException {

    public InvalidEmailPasswordLoginException() {
        super("PLAT-009-002", 401, "이메일 또는 비밀번호가 올바르지 않습니다");
    }
}
