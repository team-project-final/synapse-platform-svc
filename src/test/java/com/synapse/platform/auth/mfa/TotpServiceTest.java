package com.synapse.platform.auth.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.shared.crypto.FieldEncryptor;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
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

    private final MfaCredentialRepository mfaCredentialRepository = mock(MfaCredentialRepository.class);
    private final UserApi userApi = mock(UserApi.class);
    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(AES_KEY);
    private final TotpService totpService = new TotpService(
            mfaCredentialRepository,
            userApi,
            fieldEncryptor);

    @Test
    void setup_validUser_shouldReturnSecretAndOtpAuthUriAndStoreEncryptedSecret() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.of(user(userId)));
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.empty());

        // When
        TotpService.TotpSetupResponse response = totpService.setup(userId);

        // Then
        ArgumentCaptor<MfaCredential> captor = ArgumentCaptor.forClass(MfaCredential.class);
        verify(mfaCredentialRepository).save(captor.capture());
        MfaCredential saved = captor.getValue();
        assertThat(response.secret()).isNotBlank();
        assertThat(response.otpAuthUri())
                .startsWith("otpauth://totp/")
                .contains("issuer=Synapse");
        assertThat(saved.getSecretEnc()).isNotEqualTo(response.secret());
        assertThat(fieldEncryptor.decrypt(saved.getSecretEnc())).isEqualTo(response.secret());
    }

    @Test
    void setup_existingCredential_shouldReplaceSecretAndDisableCredential() {
        // Given
        UUID userId = UUID.randomUUID();
        MfaCredential credential = encryptedCredential(userId, "JBSWY3DPEHPK3PXP");
        credential.activate();
        given(userApi.findById(userId)).willReturn(Optional.of(user(userId)));
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));

        // When
        TotpService.TotpSetupResponse response = totpService.setup(userId);

        // Then
        ArgumentCaptor<MfaCredential> captor = ArgumentCaptor.forClass(MfaCredential.class);
        verify(mfaCredentialRepository).save(captor.capture());
        MfaCredential saved = captor.getValue();
        assertThat(saved).isSameAs(credential);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getVerifiedAt()).isNull();
        assertThat(fieldEncryptor.decrypt(saved.getSecretEnc())).isEqualTo(response.secret());
    }

    @Test
    void setup_missingUser_shouldThrowUnauthorizedTokenException() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> totpService.setup(userId))
                .isInstanceOf(UnauthorizedTokenException.class);
    }

    @Test
    void verify_validCode_shouldEnableCredentialAndReturnTrue() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String secret = "JBSWY3DPEHPK3PXP";
        MfaCredential credential = encryptedCredential(userId, secret);
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        String validCode = currentCode(secret);

        // When
        boolean result = totpService.verify(userId, validCode);

        // Then
        assertThat(result).isTrue();
        assertThat(credential.isActive()).isTrue();
        assertThat(credential.getVerifiedAt()).isNotNull();
    }

    @Test
    void verify_invalidCode_shouldReturnFalseAndKeepCredentialDisabled() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String secret = "JBSWY3DPEHPK3PXP";
        MfaCredential credential = encryptedCredential(userId, secret);
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        String validCode = currentCode(secret);
        String invalidCode = validCode.equals("000000") ? "111111" : "000000";

        // When
        boolean result = totpService.verify(userId, invalidCode);

        // Then
        assertThat(result).isFalse();
        assertThat(credential.isActive()).isFalse();
        assertThat(credential.getVerifiedAt()).isNull();
    }

    private UserInfo user(UUID userId) {
        return new UserInfo(userId, "user@example.com", "User", UUID.randomUUID());
    }

    private MfaCredential encryptedCredential(UUID userId, String secret) {
        return MfaCredential.create(userId, fieldEncryptor.encrypt(secret));
    }

    private String currentCode(String secret) throws Exception {
        long currentBucket = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        return new DefaultCodeGenerator().generate(secret, currentBucket);
    }
}
