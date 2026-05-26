package com.synapse.platform.user.api;

import java.time.OffsetDateTime;

public record LoginFailureResult(
        int failedLoginCount,
        OffsetDateTime lockedUntil
) {

    public boolean locked() {
        return lockedUntil != null;
    }
}
