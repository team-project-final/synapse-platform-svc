package com.synapse.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import com.synapse.platform.billing.entity.PlanCode;
import com.synapse.platform.user.api.UserApi;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * Step 9 — 인증/결제 전체 E2E (인프로세스).
 *
 * <p>@SpringBootTest로 실제 앱 컨텍스트를 끝까지 구동하고, MockMvc로 HTTP 경로를 그대로 호출한다.
 * 외부 경계만 모킹한다:
 * <ul>
 *   <li>{@code StripeClient} — Stripe API(외부) 호출 (checkout session 생성)</li>
 *   <li>{@code RefreshTokenService} — Redis 백엔드(외부 인프라, 테스트 프로파일에 Redis 없음).
 *       기존 통합테스트(EmailPasswordAuthIntegrationTest)와 동일한 경계 처리.</li>
 * </ul>
 * Stripe Webhook 서명은 모킹하지 않고 테스트 시크릿(whsec_test)으로 실제 HMAC 서명을 계산해
 * 실제 {@code Webhook.constructEvent} 검증 경로를 통과시킨다.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
@DisplayName("Step9 인증/결제 E2E")
class AuthBillingE2ETest {

    private static final String WEBHOOK_SECRET = "whsec_test";
    private static final String PG_DB = "testdb";
    private static final String PG_USER = "test";
    private static final String PG_PASSWORD = "test";
    private static final int PG_PORT = 5432;

    // billing 웹훅 멱등성은 PostgreSQL 전용 ON CONFLICT 네이티브 SQL을 쓰므로 H2 불가 → 실제 PG.
    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withEnv("POSTGRES_DB", PG_DB)
            .withEnv("POSTGRES_USER", PG_USER)
            .withEnv("POSTGRES_PASSWORD", PG_PASSWORD)
            .withExposedPorts(PG_PORT)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AuthBillingE2ETest::pgUrl);
        registry.add("spring.datasource.username", () -> PG_USER);
        registry.add("spring.datasource.password", () -> PG_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(pgUrl(), PG_USER, PG_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static String pgUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(), postgres.getMappedPort(PG_PORT), PG_DB);
    }

    /** Stripe API는 외부 경계 — deep-stub mock으로 대체(checkout session 생성만 stub). */
    @TestConfiguration
    static class StripeStubConfig {
        @Bean
        @Primary
        StripeClient stripeClient() {
            return Mockito.mock(StripeClient.class, Mockito.RETURNS_DEEP_STUBS);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserApi userApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StripeClient stripeClient;

    @MockitoBean
    private com.synapse.platform.auth.service.RefreshTokenService refreshTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 공유 PG 컨테이너 — 테스트 간 격리를 위해 데이터 테이블만 비운다(flyway 이력·plan 시드 보존).
        jdbcTemplate.execute("""
                DO $$
                DECLARE r RECORD;
                BEGIN
                  FOR r IN (SELECT tablename FROM pg_tables
                            WHERE schemaname = 'public'
                              AND tablename NOT IN ('flyway_schema_history', 'plan_quotas')) LOOP
                    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE';
                  END LOOP;
                END $$;
                """);

        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // 외부 경계 stub: Redis 백엔드 refresh token 저장/검증/회전은 통과시킨다.
        given(refreshTokenService.isValid(any(UUID.class), any(String.class))).willReturn(true);

        // 외부 경계 stub: Stripe checkout session 생성은 URL을 가진 세션을 반환한다.
        Session session = new Session();
        session.setUrl("https://checkout.stripe.test/session");
        try {
            given(stripeClient.checkout().sessions().create(
                    any(com.stripe.param.checkout.SessionCreateParams.class))).willReturn(session);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 시나리오 1: 회원가입 → 로그인 → JWT 발급 ────────────────────────────────

    @Test
    @DisplayName("회원가입 → 로그인 → JWT 발급/검증 + refresh 쿠키")
    void signup_login_issuesJwtAndRefreshCookie() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        String accessToken = accessToken(login);
        assertThat(accessToken).isNotBlank();

        // 발급된 JWT로 보호 엔드포인트(MFA setup) 접근 → 인증 통과 확인
        mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());
    }

    // ── 시나리오 2: MFA 등록 → TOTP 검증 ──────────────────────────────────────

    @Test
    @DisplayName("MFA setup → TOTP 검증")
    void mfa_setup_then_verify() throws Exception {
        String accessToken = signupAndLogin(uniqueEmail());

        MvcResult setup = mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists())
                .andReturn();

        String secret = JsonPath.read(setup.getResponse().getContentAsString(), "$.secret");
        String code = currentTotpCode(secret);

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    // ── 시나리오 3: Stripe Checkout → Webhook → 구독 활성화 ────────────────────

    @Test
    @DisplayName("Stripe Checkout → Webhook(checkout.session.completed) → 구독 ACTIVE")
    void checkout_then_webhook_activatesSubscription() throws Exception {
        String email = uniqueEmail();
        String accessToken = signupAndLogin(email);
        UUID tenantId = tenantIdOf(email);

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "PRO",
                                  "successUrl": "https://app.example.com/s",
                                  "cancelUrl": "https://app.example.com/c"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").exists());

        String payload = checkoutCompletedPayload(tenantId, PlanCode.PRO, "evt_" + UUID.randomUUID());
        mockMvc.perform(post("/api/v1/billing/webhooks")
                        .header("Stripe-Signature", sign(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── 시나리오 4: 토큰 갱신 ─────────────────────────────────────────────────

    @Test
    @DisplayName("Refresh 쿠키로 Access Token 갱신")
    void tokenRefresh_returnsNewAccessToken() throws Exception {
        String email = uniqueEmail();
        signupAndLogin(email);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andReturn();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(login))
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:8088"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ── 시나리오 5: 전체 Happy Path 연속 실행 ─────────────────────────────────

    @Test
    @DisplayName("전체 E2E 연속: 가입→로그인→MFA→결제→구독→갱신")
    void fullHappyPath() throws Exception {
        String email = uniqueEmail();
        String accessToken = signupAndLogin(email);
        UUID tenantId = tenantIdOf(email);

        // MFA
        MvcResult setup = mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk()).andReturn();
        String secret = JsonPath.read(setup.getResponse().getContentAsString(), "$.secret");
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + currentTotpCode(secret) + "\"}"))
                .andExpect(status().isOk());

        // 결제
        mockMvc.perform(post("/api/v1/billing/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planCode": "PRO",
                                  "successUrl": "https://app.example.com/s",
                                  "cancelUrl": "https://app.example.com/c"
                                }
                                """))
                .andExpect(status().isOk());
        String payload = checkoutCompletedPayload(tenantId, PlanCode.PRO, "evt_" + UUID.randomUUID());
        mockMvc.perform(post("/api/v1/billing/webhooks")
                        .header("Stripe-Signature", sign(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/billing/subscription")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 갱신
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andReturn();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(login))
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:8088"))
                .andExpect(status().isOk());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk())
                .andReturn();
        return accessToken(login);
    }

    private UUID tenantIdOf(String email) {
        return userApi.findByEmail(email).orElseThrow().defaultTenantId();
    }

    private static Cookie refreshCookie(MvcResult login) {
        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=");
        String value = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
        return new Cookie("refresh_token", value);
    }

    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@example.com";
    }

    private static String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"P@ssw0rd!\"}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String currentTotpCode(String secret) throws Exception {
        long bucket = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        return new DefaultCodeGenerator().generate(secret, bucket);
    }

    private static String sign(String payload) throws Exception {
        long timestamp = Webhook.Util.getTimeNow();
        String signed = timestamp + "." + payload;
        return "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signed);
    }

    private static String checkoutCompletedPayload(UUID tenantId, PlanCode planCode, String eventId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-04-22.dahlia",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "customer": "cus_test",
                      "subscription": "sub_test",
                      "metadata": { "tenant_id": "%s", "plan_code": "%s" }
                    }
                  }
                }
                """.formatted(eventId, tenantId, planCode.name());
    }
}
