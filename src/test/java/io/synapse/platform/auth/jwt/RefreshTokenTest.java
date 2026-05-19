package io.synapse.platform.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void hash_sameRawToken_shouldReturnSameSha256Hex() {
        String first = RefreshToken.hash("refresh-token");
        String second = RefreshToken.hash("refresh-token");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
        assertThat(first).isNotEqualTo("refresh-token");
    }
}
