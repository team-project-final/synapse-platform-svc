package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.auth.entity.TenantMemberId;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.RefreshTokenRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class EmailPasswordAuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private OAuthIdentityRepository oauthIdentityRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        tenantMemberRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        oauthIdentityRepository.deleteAll();
        userSettingsRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        reset(refreshTokenService);
    }

    @Test
    void signup_success_shouldReturnCreatedAndStoreBCryptHash() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("user@example.com", "P@ssw0rd!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andReturn();

        User saved = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(result.getResponse().getContentAsString()).contains(saved.getId().toString());
        assertThat(saved.getPasswordHash()).isNotEqualTo("P@ssw0rd!");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("P@ssw0rd!", saved.getPasswordHash())).isTrue();
        assertThat(userSettingsRepository.findById(saved.getId())).isPresent();
        assertThat(saved.getDefaultTenantId()).isNotNull();
        assertThat(tenantRepository.findById(saved.getDefaultTenantId())).isPresent();
        assertThat(tenantMemberRepository.findById(new TenantMemberId(saved.getDefaultTenantId(), saved.getId())))
                .isPresent();
    }

    @Test
    void signup_duplicateEmail_shouldReturnConflict() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("duplicate@example.com", "P@ssw0rd!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("duplicate@example.com", "P@ssw0rd!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAT-009-001"));
    }

    @Test
    void signup_badPassword_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("user@example.com", "password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_success_shouldReturnAccessTokenAndSetRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("login@example.com", "P@ssw0rd!")))
                .andExpect(status().isCreated());
        User saved = userRepository.findByEmail("login@example.com").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("login@example.com", "P@ssw0rd!")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                        Matchers.containsString("refresh_token="),
                        Matchers.containsString("Path=/api/v1/auth"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        verify(refreshTokenService).save(any(UUID.class), any(String.class));
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void login_badPassword_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("bad-login@example.com", "P@ssw0rd!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("bad-login@example.com", "Wrong1!!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAT-009-002"));
    }

    @Test
    void login_fiveFailures_shouldLockAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("lock@example.com", "P@ssw0rd!")))
                .andExpect(status().isCreated());

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSignupJson("lock@example.com", "Wrong1!!")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("lock@example.com", "Wrong1!!")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("PLAT-009-004"));
        User saved = userRepository.findByEmail("lock@example.com").orElseThrow();
        assertThat(saved.getFailedLoginCount()).isEqualTo(5);
        assertThat(saved.getLockedUntil()).isAfter(OffsetDateTime.now());
    }

    @Test
    void login_lockedAccount_shouldReturnLocked() throws Exception {
        User user = userRepository.save(User.ofEmailPassword(
                "locked@example.com",
                "locked",
                passwordEncoder.encode("P@ssw0rd!"),
                null));
        ReflectionTestUtils.setField(user, "lockedUntil", OffsetDateTime.now().plusMinutes(10));
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("locked@example.com", "P@ssw0rd!")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("PLAT-009-004"));
    }

    @Test
    void login_oAuthAccount_shouldReturnUnauthorizedWithOAuthMessage() throws Exception {
        userRepository.save(User.ofOAuth(
                "oauth@example.com",
                "oauth",
                "OAuth User",
                "https://example.com/avatar.png"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("oauth@example.com", "P@ssw0rd!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PLAT-009-003"))
                .andExpect(jsonPath("$.detail").value("이 이메일은 소셜 로그인으로 가입되었습니다"));
    }

    private String validSignupJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
