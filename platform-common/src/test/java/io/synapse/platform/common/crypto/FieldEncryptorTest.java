package io.synapse.platform.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldEncryptorTest {

    private static final String VALID_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encrypt_validPlainText_shouldReturnIvAndCipherText() {
        // Given
        FieldEncryptor encryptor = new FieldEncryptor(VALID_KEY);

        // When
        String encrypted = encryptor.encrypt("secret-value");

        // Then
        assertThat(encrypted).contains(":");
        assertThat(encrypted).doesNotContain("secret-value");
        assertThat(encrypted.split(":")).hasSize(2);
    }

    @Test
    void decrypt_encryptedValue_shouldReturnOriginalPlainText() {
        // Given
        FieldEncryptor encryptor = new FieldEncryptor(VALID_KEY);
        String encrypted = encryptor.encrypt("secret-value");

        // When
        String decrypted = encryptor.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo("secret-value");
    }

    @Test
    void constructor_shortKey_shouldThrowIllegalArgumentException() {
        // Given
        String invalidKey = Base64.getEncoder().encodeToString("short-key".getBytes(StandardCharsets.UTF_8));

        // When & Then
        assertThatThrownBy(() -> new FieldEncryptor(invalidKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encrypt_samePlainTextTwice_shouldUseDifferentIv() {
        // Given
        FieldEncryptor encryptor = new FieldEncryptor(VALID_KEY);

        // When
        String first = encryptor.encrypt("secret-value");
        String second = encryptor.encrypt("secret-value");

        // Then
        assertThat(first).isNotEqualTo(second);
        assertThat(first.split(":")[0]).isNotEqualTo(second.split(":")[0]);
    }
}
