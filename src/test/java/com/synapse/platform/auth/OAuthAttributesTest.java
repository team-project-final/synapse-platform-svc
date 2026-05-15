package com.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.auth.oauth.OAuthAttributes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuthAttributesTest {

    @Test
    void of_googleAttributes_shouldMapSubEmailNamePicture() {
        // Given
        Map<String, Object> attributes = Map.of(
                "sub", "google-123",
                "email", "user@example.com",
                "name", "Test User",
                "picture", "https://example.com/avatar.png");

        // When
        OAuthAttributes result = OAuthAttributes.of("google", attributes);

        // Then
        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.providerId()).isEqualTo("google-123");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.name()).isEqualTo("Test User");
        assertThat(result.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(result.nameAttributeKey()).isEqualTo("sub");
    }

    @Test
    void of_githubAttributes_shouldMapIdEmailLoginAvatarUrl() {
        // Given
        Map<String, Object> attributes = Map.of(
                "id", 12345,
                "email", "dev@example.com",
                "login", "devuser",
                "avatar_url", "https://example.com/dev.png");

        // When
        OAuthAttributes result = OAuthAttributes.of("github", attributes);

        // Then
        assertThat(result.provider()).isEqualTo("github");
        assertThat(result.providerId()).isEqualTo("12345");
        assertThat(result.email()).isEqualTo("dev@example.com");
        assertThat(result.name()).isEqualTo("devuser");
        assertThat(result.avatarUrl()).isEqualTo("https://example.com/dev.png");
        assertThat(result.nameAttributeKey()).isEqualTo("id");
    }

    @Test
    void of_githubAttributes_emailNull_shouldKeepEmailNull() {
        // Given
        Map<String, Object> attributes = Map.of(
                "id", 12345,
                "login", "devuser",
                "avatar_url", "https://example.com/dev.png");

        // When
        OAuthAttributes result = OAuthAttributes.of("github", attributes);

        // Then
        assertThat(result.email()).isNull();
    }

    @Test
    void of_unknownProvider_shouldThrowIllegalArgumentException() {
        // Given
        Map<String, Object> attributes = Map.of("id", "unknown-1");

        // When & Then
        assertThatThrownBy(() -> OAuthAttributes.of("unknown", attributes))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
