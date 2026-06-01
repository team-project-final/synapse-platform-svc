package com.synapse.platform.user.service;

import com.synapse.platform.global.exception.BusinessException;

public class InvalidUserStatusFilterException extends BusinessException {

    public InvalidUserStatusFilterException(String status) {
        super("PLAT-ADMIN-002", 400, "Invalid user status filter: " + status);
    }
}
