package com.synapse.platform.auth;

import java.util.List;

public final class AuthRoles {

    public static final List<String> DEFAULT_USER_ROLES = List.of("ROLE_USER");
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AuthRoles() {
    }
}
