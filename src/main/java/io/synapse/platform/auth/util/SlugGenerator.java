package io.synapse.platform.auth.util;

import io.synapse.platform.auth.repository.TenantRepository;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SlugGenerator {

    private static final int MAX_ATTEMPTS = 10;
    private static final int SUFFIX_LENGTH = 6;
    private static final char[] SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final TenantRepository tenantRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SlugGenerator(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public String generate(String email) {
        String base = baseFrom(email);
        if (!tenantRepository.existsBySlug(base)) {
            return base;
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = base + "_" + randomSuffix();
            if (!tenantRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to generate available slug");
    }

    private String baseFrom(String email) {
        String localPart = email == null ? "" : email.split("@", 2)[0];
        String base = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return base.isBlank() ? "user" : base;
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(SUFFIX_CHARS[secureRandom.nextInt(SUFFIX_CHARS.length)]);
        }
        return suffix.toString();
    }
}