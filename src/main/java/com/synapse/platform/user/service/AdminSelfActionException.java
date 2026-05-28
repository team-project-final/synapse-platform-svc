package com.synapse.platform.user.service;

import com.synapse.platform.global.exception.BusinessException;

public class AdminSelfActionException extends BusinessException {

    public AdminSelfActionException() {
        super("PLAT-ADMIN-001", 400, "Admin cannot suspend or delete self");
    }
}
