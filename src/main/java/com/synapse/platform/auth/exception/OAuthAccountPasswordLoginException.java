package com.synapse.platform.auth.exception;

import com.synapse.platform.global.exception.BusinessException;

public class OAuthAccountPasswordLoginException extends BusinessException {

    public OAuthAccountPasswordLoginException() {
        super("PLAT-009-003", 401, "이 이메일은 소셜 로그인으로 가입되었습니다");
    }
}
