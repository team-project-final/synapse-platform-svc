package com.synapse.platform.auth.dto;

import java.util.List;

public record MfaBackupCodeResponse(
        List<String> codes
) {
    public MfaBackupCodeResponse {
        codes = List.copyOf(codes);
    }
}
