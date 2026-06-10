package com.synapse.platform.auth.service;

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
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TotpService {

    private static final int SECRET_CHARACTERS = 20;
    private static final int TIME_PERIOD_SECONDS = 30;
    private static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1;
    private static final String ISSUER = "Synapse";
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_RAW_LENGTH = 8;
    private static final char[] BACKUP_CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final MfaCredentialRepository mfaCredentialRepository;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final UserApi userApi;
    private final FieldEncryptor fieldEncryptor;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService(
            MfaCredentialRepository mfaCredentialRepository,
            MfaBackupCodeRepository mfaBackupCodeRepository,
            UserApi userApi,
            FieldEncryptor fieldEncryptor,
            PasswordEncoder passwordEncoder) {
        this.mfaCredentialRepository = mfaCredentialRepository;
        this.mfaBackupCodeRepository = mfaBackupCodeRepository;
        this.userApi = userApi;
        this.fieldEncryptor = fieldEncryptor;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TotpSetupResponse setup(UUID userId) {
        UserInfo user = userApi.findById(userId)
                .orElseThrow(() -> new UnauthorizedTokenException("Authentication required"));
        String secret = new DefaultSecretGenerator(SECRET_CHARACTERS).generate();
        String secretEnc = fieldEncryptor.encrypt(secret);
        MfaCredential credential = mfaCredentialRepository.findByUserId(userId)
                .orElseGet(() -> MfaCredential.create(userId, secretEnc));
        credential.replaceSecret(secretEnc);
        revokeUnusedBackupCodes(userId);
        mfaCredentialRepository.save(credential);
        QrData qrData = new QrData.Builder()
                .label(user.email())
                .secret(secret)
                .issuer(ISSUER)
                .digits(6)
                .period(TIME_PERIOD_SECONDS)
                .build();
        return new TotpSetupResponse(qrData.getUri(), secret);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean verify(UUID userId, String code) {
        return mfaCredentialRepository.findByUserId(userId)
                .map(credential -> verifyCredential(credential, code))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<String> generateBackupCodes(UUID userId) {
        ensureActiveTotpForUpdate(userId);
        revokeUnusedBackupCodes(userId);

        Set<String> rawCodes = new LinkedHashSet<>();
        while (rawCodes.size() < BACKUP_CODE_COUNT) {
            rawCodes.add(generateBackupCode());
        }

        List<MfaBackupCode> entities = rawCodes.stream()
                .map(rawCode -> MfaBackupCode.create(userId, passwordEncoder.encode(normalizeBackupCode(rawCode))))
                .toList();
        mfaBackupCodeRepository.saveAll(entities);
        return new ArrayList<>(rawCodes);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean verifyBackupCode(UUID userId, String rawCode) {
        ensureActiveTotp(userId);
        String normalizedCode = normalizeBackupCode(rawCode);
        if (normalizedCode.isBlank()) {
            return false;
        }
        for (MfaBackupCode backupCode : mfaBackupCodeRepository.findAllUnusedByUserIdForUpdate(userId)) {
            if (passwordEncoder.matches(normalizedCode, backupCode.getCodeHash())) {
                backupCode.markUsed();
                return true;
            }
        }
        return false;
    }

    private boolean verifyCredential(MfaCredential credential, String code) {
        String secret = fieldEncryptor.decrypt(credential.getSecretEnc());
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(),
                new SystemTimeProvider());
        verifier.setTimePeriod(TIME_PERIOD_SECONDS);
        verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_TIME_PERIOD_DISCREPANCY);
        boolean valid = verifier.isValidCode(secret, code);
        if (valid) {
            credential.activate();
        }
        return valid;
    }

    private void ensureActiveTotp(UUID userId) {
        MfaCredential credential = mfaCredentialRepository.findByUserId(userId)
                .orElseThrow(() -> new MfaVerificationException("MFA credential is not active"));
        if (!credential.isActive()) {
            throw new MfaVerificationException("MFA credential is not active");
        }
    }

    private void ensureActiveTotpForUpdate(UUID userId) {
        MfaCredential credential = mfaCredentialRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MfaVerificationException("MFA credential is not active"));
        if (!credential.isActive()) {
            throw new MfaVerificationException("MFA credential is not active");
        }
    }

    private void revokeUnusedBackupCodes(UUID userId) {
        mfaBackupCodeRepository.findAllUnusedByUserIdForUpdate(userId)
                .forEach(MfaBackupCode::markUsed);
    }

    private String generateBackupCode() {
        StringBuilder builder = new StringBuilder(BACKUP_CODE_RAW_LENGTH + 1);
        for (int index = 0; index < BACKUP_CODE_RAW_LENGTH; index++) {
            if (index == 4) {
                builder.append('-');
            }
            builder.append(BACKUP_CODE_ALPHABET[secureRandom.nextInt(BACKUP_CODE_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private String normalizeBackupCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.trim()
                .replace("-", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }

    public record TotpSetupResponse(
            String otpAuthUri,
            String secret
    ) {
    }
}
