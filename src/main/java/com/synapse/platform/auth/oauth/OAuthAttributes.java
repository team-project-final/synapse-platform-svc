package com.synapse.platform.auth.oauth;

import java.util.Map;

public record OAuthAttributes(
        String provider,
        String providerId,
        String email,
        String name,
        String avatarUrl
) {

    public static OAuthAttributes of(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> ofGoogle(attributes);
            case "github" -> ofGithub(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 provider: " + provider);
        };
    }

    private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
        return new OAuthAttributes(
                "google",
                String.valueOf(attributes.get("sub")),
                String.valueOf(attributes.get("email")),
                String.valueOf(attributes.get("name")),
                String.valueOf(attributes.getOrDefault("picture", "")));
    }

    private static OAuthAttributes ofGithub(Map<String, Object> attributes) {
        String email = attributes.get("email") == null ? null : String.valueOf(attributes.get("email"));
        return new OAuthAttributes(
                "github",
                String.valueOf(attributes.get("id")),
                email,
                String.valueOf(attributes.get("login")),
                String.valueOf(attributes.getOrDefault("avatar_url", "")));
    }

    public String nameAttributeKey() {
        return switch (provider) {
            case "google" -> "sub";
            case "github" -> "id";
            default -> throw new IllegalArgumentException("지원하지 않는 provider: " + provider);
        };
    }
}
