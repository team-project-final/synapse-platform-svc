package com.synapse.platform.auth.mfa;

import com.synapse.platform.auth.domain.User;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.repository.UserRepository;
import com.synapse.platform.shared.crypto.FieldEncryptor;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TotpService {

    private static final int SECRET_CHARACTERS = 20;
    private static final int TIME_PERIOD_SECONDS = 30;
    private static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1;
    private static final String ISSUER = "Synapse";

    private final TotpCredentialRepository totpCredentialRepository;
    private final UserRepository userRepository;
    private final FieldEncryptor fieldEncryptor;

    public TotpService(
            TotpCredentialRepository totpCredentialRepository,
            UserRepository userRepository,
            FieldEncryptor fieldEncryptor) {
        this.totpCredentialRepository = totpCredentialRepository;
        this.userRepository = userRepository;
        this.fieldEncryptor = fieldEncryptor;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TotpSetupResponse setup(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedTokenException("Authentication required"));
        String secret = new DefaultSecretGenerator(SECRET_CHARACTERS).generate();
        String encrypted = fieldEncryptor.encrypt(secret);
        String[] parts = encrypted.split(":", 2);
        TotpCredential credential = totpCredentialRepository.findByUserId(userId)
                .orElseGet(() -> TotpCredential.create(userId, parts[1], parts[0]));
        credential.replaceSecret(parts[1], parts[0]);
        totpCredentialRepository.save(credential);
        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(ISSUER)
                .digits(6)
                .period(TIME_PERIOD_SECONDS)
                .build();
        return new TotpSetupResponse(qrData.getUri(), secret);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean verify(UUID userId, String code) {
        return totpCredentialRepository.findByUserId(userId)
                .map(credential -> verifyCredential(credential, code))
                .orElse(false);
    }

    private boolean verifyCredential(TotpCredential credential, String code) {
        String secret = fieldEncryptor.decrypt(credential.getSecretIv() + ":" + credential.getSecret());
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(),
                new SystemTimeProvider());
        verifier.setTimePeriod(TIME_PERIOD_SECONDS);
        verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_TIME_PERIOD_DISCREPANCY);
        boolean valid = verifier.isValidCode(secret, code);
        if (valid) {
            credential.enable();
        }
        return valid;
    }

    public record TotpSetupResponse(
            String otpAuthUri,
            String secret
    ) {
    }
}
