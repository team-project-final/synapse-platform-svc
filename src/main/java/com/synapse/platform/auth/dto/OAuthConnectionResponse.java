package com.synapse.platform.auth.dto;

public record OAuthConnectionResponse(
        String provider,
        String email
) {
}
