package com.synapse.platform.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.service.TotpService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class MfaControllerTest {

    private final TotpService totpService = mock(TotpService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MfaController(totpService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void setup_withoutJwt_shouldReturnUnauthorizedProblem() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/setup"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void setup_malformedPrincipal_shouldReturnUnauthorizedProblem() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .principal(new TestingAuthenticationToken("not-a-uuid", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void setup_authenticatedUser_shouldReturnOtpAuthUriAndSecret() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(totpService.setup(userId)).willReturn(new TotpService.TotpSetupResponse(
                "otpauth://totp/user@example.com?secret=BASE32&issuer=Synapse",
                "BASE32"));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpAuthUri").value(
                        "otpauth://totp/user@example.com?secret=BASE32&issuer=Synapse"))
                .andExpect(jsonPath("$.secret").value("BASE32"));
    }

    @Test
    void verify_validCode_shouldReturnOk() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(totpService.verify(userId, "123456")).willReturn(true);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "123456"))))
                .andExpect(status().isOk());
    }

    @Test
    void verify_invalidCode_shouldReturnBadRequestProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(totpService.verify(userId, "000000")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PLAT-003"));
    }

    @Test
    void backupCodes_authenticatedUser_shouldReturnGeneratedCodes() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(totpService.generateBackupCodes(userId))
                .willReturn(List.of("ABCD-EFGH", "JKLM-NPQR"));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/backup-codes")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes[0]").value("ABCD-EFGH"))
                .andExpect(jsonPath("$.codes[1]").value("JKLM-NPQR"));

        verify(totpService).generateBackupCodes(userId);
    }

    @Test
    void backup_validCode_shouldReturnOk() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(totpService.verifyBackupCode(userId, "ABCD-EFGH")).willReturn(true);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/backup")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "ABCD-EFGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void backup_invalidCode_shouldReturnBadRequestProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(totpService.verifyBackupCode(userId, "BAD-CODE")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/mfa/backup")
                        .principal(new TestingAuthenticationToken(userId.toString(), null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "BAD-CODE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PLAT-003"));
    }
}
