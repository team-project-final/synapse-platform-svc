package com.synapse.platform.auth.controller;

import com.synapse.platform.auth.exception.MfaVerificationException;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.service.TotpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {

    private final TotpService totpService;

    public MfaController(TotpService totpService) {
        this.totpService = totpService;
    }

    @PostMapping("/setup")
    public TotpService.TotpSetupResponse setup(Authentication authentication) {
        return totpService.setup(currentUserId(authentication));
    }

    @PostMapping("/verify")
    public Map<String, Boolean> verify(
            Authentication authentication,
            @Valid @RequestBody MfaVerifyRequest request) {
        boolean verified = totpService.verify(currentUserId(authentication), request.code());
        if (!verified) {
            throw new MfaVerificationException("Invalid MFA code");
        }
        return Map.of("verified", true);
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedTokenException("Authentication required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedTokenException("Authentication required");
        }
    }

    public record MfaVerifyRequest(
            @NotBlank
            String code
    ) {
    }
}
