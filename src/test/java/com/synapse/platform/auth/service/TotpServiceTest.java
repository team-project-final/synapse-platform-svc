package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.entity.MfaBackupCode;
import com.synapse.platform.auth.entity.MfaCredential;
import com.synapse.platform.auth.exception.MfaVerificationException;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.repository.MfaBackupCodeRepository;
import com.synapse.platform.auth.repository.MfaCredentialRepository;
import com.synapse.platform.global.crypto.FieldEncryptor;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class TotpServiceTest {

    private static final String AES_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final MfaCredentialRepository mfaCredentialRepository = mock(MfaCredentialRepository.class);
    private final MfaBackupCodeRepository mfaBackupCodeRepository = mock(MfaBackupCodeRepository.class);
    private final UserApi userApi = mock(UserApi.class);
    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(AES_KEY);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TotpService totpService = new TotpService(
            mfaCredentialRepository,
            mfaBackupCodeRepository,
            userApi,
            fieldEncryptor,
            passwordEncoder);

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
        MfaBackupCode oldCode = MfaBackupCode.create(userId, "encoded-old-code");
        given(userApi.findById(userId)).willReturn(Optional.of(user(userId)));
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        given(mfaBackupCodeRepository.findAllUnusedByUserIdForUpdate(userId)).willReturn(List.of(oldCode));

        // When
        TotpService.TotpSetupResponse response = totpService.setup(userId);

        // Then
        ArgumentCaptor<MfaCredential> captor = ArgumentCaptor.forClass(MfaCredential.class);
        verify(mfaCredentialRepository).save(captor.capture());
        MfaCredential saved = captor.getValue();
        assertThat(saved).isSameAs(credential);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getVerifiedAt()).isNull();
        assertThat(oldCode.isUsed()).isTrue();
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

    @Test
    void generateBackupCodes_activeTotp_shouldReturnRawCodesAndStoreOnlyHashes() {
        // Given
        UUID userId = UUID.randomUUID();
        MfaCredential credential = encryptedCredential(userId, "JBSWY3DPEHPK3PXP");
        credential.activate();
        MfaBackupCode oldCode = MfaBackupCode.create(userId, "encoded-old-code");
        given(mfaCredentialRepository.findByUserIdForUpdate(userId)).willReturn(Optional.of(credential));
        given(mfaBackupCodeRepository.findAllUnusedByUserIdForUpdate(userId))
                .willReturn(List.of(oldCode));
        given(passwordEncoder.encode(anyString())).willAnswer(invocation -> "encoded-" + invocation.getArgument(0));

        // When
        List<String> rawCodes = totpService.generateBackupCodes(userId);

        // Then
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mfaBackupCodeRepository).saveAll(captor.capture());
        @SuppressWarnings("unchecked")
        List<MfaBackupCode> savedCodes = captor.getValue();

        assertThat(rawCodes).hasSize(10);
        assertThat(rawCodes).allMatch(code -> code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertThat(savedCodes).hasSize(10);
        assertThat(savedCodes)
                .zipSatisfy(rawCodes, (saved, raw) -> assertThat(saved.getCodeHash()).isNotEqualTo(raw));
        assertThat(savedCodes).allSatisfy(saved -> assertThat(saved.getCodeHash()).startsWith("encoded-"));
        assertThat(oldCode.isUsed()).isTrue();
    }

    @Test
    void generateBackupCodes_inactiveTotp_shouldThrowMfaVerificationException() {
        // Given
        UUID userId = UUID.randomUUID();
        given(mfaCredentialRepository.findByUserIdForUpdate(userId))
                .willReturn(Optional.of(encryptedCredential(userId, "JBSWY3DPEHPK3PXP")));

        // When & Then
        assertThatThrownBy(() -> totpService.generateBackupCodes(userId))
                .isInstanceOf(MfaVerificationException.class);
    }

    @Test
    void verifyBackupCode_validCode_shouldMarkUsedAndReturnTrue() {
        // Given
        UUID userId = UUID.randomUUID();
        MfaCredential credential = encryptedCredential(userId, "JBSWY3DPEHPK3PXP");
        credential.activate();
        MfaBackupCode backupCode = MfaBackupCode.create(userId, "encoded-backup-code");
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        given(mfaBackupCodeRepository.findAllUnusedByUserIdForUpdate(userId)).willReturn(List.of(backupCode));
        given(passwordEncoder.matches("ABCDEFGH", "encoded-backup-code")).willReturn(true);

        // When
        boolean result = totpService.verifyBackupCode(userId, "ABCD-EFGH");

        // Then
        assertThat(result).isTrue();
        assertThat(backupCode.isUsed()).isTrue();
    }

    @Test
    void verifyBackupCode_unknownCode_shouldReturnFalse() {
        // Given
        UUID userId = UUID.randomUUID();
        MfaCredential credential = encryptedCredential(userId, "JBSWY3DPEHPK3PXP");
        credential.activate();
        given(mfaCredentialRepository.findByUserId(userId)).willReturn(Optional.of(credential));
        given(mfaBackupCodeRepository.findAllUnusedByUserIdForUpdate(userId)).willReturn(List.of());

        // When
        boolean result = totpService.verifyBackupCode(userId, "ABCD-EFGH");

        // Then
        assertThat(result).isFalse();
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
