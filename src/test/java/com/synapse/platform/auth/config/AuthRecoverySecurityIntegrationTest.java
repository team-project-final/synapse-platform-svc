package com.synapse.platform.auth.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.auth.service.PasswordResetResult;
import com.synapse.platform.auth.service.PasswordResetService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthRecoverySecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void passwordResetRequest_withoutAuthentication_shouldBePermitted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetVerify_withoutAuthentication_shouldBePermitted() throws Exception {
        given(passwordResetService.verify(anyString(), anyString()))
                .willReturn(new PasswordResetResult(
                        "reset-token",
                        OffsetDateTime.parse("2026-06-10T12:15:00+09:00")));

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetConfirm_withoutAuthentication_shouldBePermitted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resetToken": "reset-token",
                                  "newPassword": "Newpass1!"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void mfaBackupCodes_withoutAuthentication_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mfa/backup-codes"))
                .andExpect(status().isUnauthorized());
    }
}
