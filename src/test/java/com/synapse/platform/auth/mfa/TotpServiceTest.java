package com.synapse.platform.auth.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.domain.User;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.repository.UserRepository;
import com.synapse.platform.shared.crypto.FieldEncryptor;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TotpServiceTest {

    private static final String AES_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final TotpCredentialRepository totpCredentialRepository = mock(TotpCredentialRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(AES_KEY);
    private final TotpService totpService = new TotpService(
            totpCredentialRepository,
            userRepository,
            fieldEncryptor);

    @Test
    void setup_validUser_shouldReturnSecretAndOtpAuthUriAndStoreEncryptedSecret() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.of(user()));
        given(totpCredentialRepository.findByUserId(userId)).willReturn(Optional.empty());

        // When
        TotpService.TotpSetupResponse response = totpService.setup(userId);

        // Then
        ArgumentCaptor<TotpCredential> captor = ArgumentCaptor.forClass(TotpCredential.class);
        verify(totpCredentialRepository).save(captor.capture());
        TotpCredential saved = captor.getValue();
        assertThat(response.secret()).isNotBlank();
        assertThat(response.otpAuthUri())
                .startsWith("otpauth://totp/")
                .contains("issuer=Synapse");
        assertThat(saved.getSecret()).isNotEqualTo(response.secret());
        assertThat(saved.getSecretIv()).isNotBlank();
        assertThat(fieldEncryptor.decrypt(saved.getSecretIv() + ":" + saved.getSecret()))
                .isEqualTo(response.secret());
    }

    @Test
    void setup_existingCredential_shouldReplaceSecretAndDisableCredential() {
        // Given
        UUID userId = UUID.randomUUID();
        TotpCredential credential = encryptedCredential(userId, "JBSWY3DPEHPK3PXP");
        credential.enable();
        given(userRepository.findById(userId)).willReturn(Optional.of(user()));
        given(totpCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));

        // When
        TotpService.TotpSetupResponse response = totpService.setup(userId);

        // Then
        ArgumentCaptor<TotpCredential> captor = ArgumentCaptor.forClass(TotpCredential.class);
        verify(totpCredentialRepository).save(captor.capture());
        TotpCredential saved = captor.getValue();
        assertThat(saved).isSameAs(credential);
        assertThat(saved.isEnabled()).isFalse();
        assertThat(fieldEncryptor.decrypt(saved.getSecretIv() + ":" + saved.getSecret()))
                .isEqualTo(response.secret());
    }

    @Test
    void setup_missingUser_shouldThrowUnauthorizedTokenException() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> totpService.setup(userId))
                .isInstanceOf(UnauthorizedTokenException.class);
    }

    @Test
    void verify_validCode_shouldEnableCredentialAndReturnTrue() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String secret = "JBSWY3DPEHPK3PXP";
        TotpCredential credential = encryptedCredential(userId, secret);
        given(totpCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        String validCode = currentCode(secret);

        // When
        boolean result = totpService.verify(userId, validCode);

        // Then
        assertThat(result).isTrue();
        assertThat(credential.isEnabled()).isTrue();
    }

    @Test
    void verify_invalidCode_shouldReturnFalseAndKeepCredentialDisabled() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String secret = "JBSWY3DPEHPK3PXP";
        TotpCredential credential = encryptedCredential(userId, secret);
        given(totpCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        String validCode = currentCode(secret);
        String invalidCode = validCode.equals("000000") ? "111111" : "000000";

        // When
        boolean result = totpService.verify(userId, invalidCode);

        // Then
        assertThat(result).isFalse();
        assertThat(credential.isEnabled()).isFalse();
    }

    private User user() {
        return User.ofOAuth("user@example.com", "user", "User", "https://example.com/avatar.png");
    }

    private TotpCredential encryptedCredential(UUID userId, String secret) {
        String encrypted = fieldEncryptor.encrypt(secret);
        String[] parts = encrypted.split(":", 2);
        return TotpCredential.create(userId, parts[1], parts[0]);
    }

    private String currentCode(String secret) throws Exception {
        long currentBucket = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        return new DefaultCodeGenerator().generate(secret, currentBucket);
    }
}
