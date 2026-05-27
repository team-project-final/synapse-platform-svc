package com.synapse.platform.auth.service;

public record LoginResult(
        String accessToken,
        String refreshToken
) {
}
