# Stripe Billing 구현 계획 및 완료 기록

## 목표

Stripe Checkout으로 유료 플랜 결제를 시작하고, Stripe Webhook으로 구독/결제 이력을 저장하며 tenant plan을 활성화 또는 비활성화한다.

실제 API 경로는 프로젝트의 기존 버전 규칙에 맞춰 `/api/v1/billing/...`을 사용한다.

## 구현 범위

- [x] Gradle에 Stripe Java SDK와 JaCoCo 검증 설정 추가
- [x] Stripe 런타임/테스트 설정 및 로컬 환경 변수 예시 추가
- [x] `subscriptions`, `payment_history`, `processed_events` Flyway 마이그레이션 추가
- [x] `auth.api` named interface에 `TenantApi` 경계 추가
- [x] `billing` Spring Modulith 모듈 추가
- [x] Billing 도메인, Repository, DTO, Stripe 설정 추가
- [x] Checkout Session 생성 API 추가
- [x] Stripe Webhook 검증 및 idempotency 처리 추가
- [x] `checkout.session.completed`, `invoice.paid`, `customer.subscription.deleted` 처리 추가
- [x] 현재 구독 조회 API 추가
- [x] Billing controller/service/repository/security 테스트 추가
- [x] Billing 패키지 JaCoCo 라인 커버리지 80% 기준 추가

## 주요 설계

Billing 모듈은 `UserApi`와 `TenantApi`만 사용한다. auth/user 모듈의 entity나 repository를 직접 import하지 않는다.

Webhook idempotency는 `processed_events.event_id`에 먼저 insert하고, 이미 처리된 event면 business mutation을 건너뛰는 방식으로 보장한다.

Stripe SDK 32.1.0 기준으로 `Invoice`의 subscription/payment intent 접근 API가 문서 예시와 달라서 다음 경로로 처리했다.

- subscription id: `invoice.getParent().getSubscriptionDetails().getSubscription()`
- payment intent id: `invoice.getPayments().getData().get(0).getPayment().getPaymentIntent()`

## 검증 계획

- [x] `.\gradlew.bat compileJava --no-daemon`
- [x] `.\gradlew.bat test --tests "io.synapse.platform.billing.*" --no-daemon`
- [x] `.\gradlew.bat test --tests "io.synapse.platform.PlatformModuleStructureTest" --no-daemon`
- [x] `.\gradlew.bat test --no-daemon`
- [x] `.\gradlew.bat build --no-daemon`
- [x] Billing 모듈의 auth/user 내부 구현 직접 import 없음 확인

## 제외 범위

- Refund 처리
- Plan upgrade/downgrade proration
- Invoice PDF 생성
- Billing UI
- 실제 Stripe Test Mode 결제 E2E 검증

실제 Stripe Test Mode 결제는 real Stripe key, price id, webhook endpoint가 필요하므로 이번 로컬 검증 범위에는 포함하지 않는다.

---

## 리뷰 후속 수정 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리뷰에서 발견된 Billing 보안/정합성 문제 4건을 수정해 checkout, webhook, subscription API가 운영 안전 기준을 만족하게 한다.

**Architecture:** Billing webhook만 인증 없이 열고, 나머지 billing API는 Spring Security에서 인증을 요구한다. Checkout 완료 webhook은 기존 ACTIVE subscription을 재사용하더라도 subscription plan과 tenant plan을 같은 값으로 갱신한다. Stripe redirect URL은 서버 allowlist로 제한하고, webhook signature 누락은 400 문제 응답으로 처리한다.

**Tech Stack:** Java 21, Spring Security, Spring MVC validation, Spring Boot configuration properties, Stripe Java 32.1.0, JUnit 5, Mockito, MockMvc.

### Task R1: Billing Security Matcher 축소

**Files:**
- Modify: `src/main/java/io/synapse/platform/auth/config/SecurityConfig.java`
- Modify: `src/test/java/io/synapse/platform/billing/BillingSecurityIntegrationTest.java`

- [ ] **Step 1: 보안 회귀 테스트를 먼저 보강한다**

`BillingSecurityIntegrationTest`에 subscription API가 인증 없이 접근되면 401을 반환하는 테스트를 추가한다.

```java
@Test
void subscription_withoutAuthenticationReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/billing/subscription"))
            .andExpect(status().isUnauthorized());
}
```

필요한 static import를 추가한다.

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

- [ ] **Step 2: 실패 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingSecurityIntegrationTest" --no-daemon
```

Expected before implementation: 현재 설정은 `/api/v1/billing/**` 전체가 `permitAll()`이므로 subscription 요청이 controller까지 도달해 401을 낼 수 있다. 이 테스트가 이미 통과하더라도 보안 matcher가 넓은 상태이므로 Step 3을 진행한다.

- [ ] **Step 3: webhook만 permitAll로 좁히고 billing 인증 실패는 401로 고정한다**

`SecurityConfig`에 API용 entry point를 billing 경로에만 적용한다. 기존 OAuth 보호 페이지의 redirect 동작은 유지한다.

```java
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
```

`filterChain` 설정에서 `authorizeHttpRequests`와 `exceptionHandling`을 다음 형태로 정리한다.

```java
.exceptionHandling(exception -> exception
        .defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                new AntPathRequestMatcher("/api/v1/billing/**")))
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
                "/actuator/**",
                "/oauth2/**",
                "/login/**",
                "/api/v1/auth/callback",
                "/api/v1/auth/refresh",
                "/api/v1/billing/webhooks").permitAll()
        .anyRequest().authenticated())
```

- [ ] **Step 4: 보안 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingSecurityIntegrationTest" --no-daemon
```

Expected: checkout unauthenticated 401, subscription unauthenticated 401, webhook unauthenticated 200.

### Task R2: 기존 ACTIVE Subscription의 Plan 동기화

**Files:**
- Modify: `src/main/java/io/synapse/platform/billing/domain/Subscription.java`
- Modify: `src/main/java/io/synapse/platform/billing/BillingService.java`
- Modify: `src/test/java/io/synapse/platform/billing/BillingServiceTest.java`
- Modify: `src/test/java/io/synapse/platform/billing/domain/BillingDomainTest.java`

- [ ] **Step 1: 기존 subscription 재사용 시 plan이 바뀌는 실패 테스트를 추가한다**

`BillingServiceTest`에 기존 PRO subscription이 있는 tenant가 TEAM checkout webhook을 받으면 subscription과 tenant plan이 모두 TEAM으로 갱신되는 테스트를 추가한다.

```java
@Test
void handleWebhook_checkoutCompletedUpdatesExistingSubscriptionPlan() throws Exception {
    UUID tenantId = UUID.randomUUID();
    Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_old");
    subscription.activate("sub_old", java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMonths(1));
    String payload = checkoutCompletedPayload(tenantId, PlanCode.TEAM, "evt_checkout_existing");
    given(processedEventRepository.insertIfAbsent("evt_checkout_existing", "checkout.session.completed")).willReturn(1);
    given(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
            .willReturn(Optional.of(subscription));

    billingService.handleWebhook(payload.getBytes(StandardCharsets.UTF_8), signature(payload));

    assertThat(subscription.getPlanCode()).isEqualTo(PlanCode.TEAM);
    assertThat(subscription.getStripeCustomerId()).isEqualTo("cus_test");
    assertThat(subscription.getStripeSubscriptionId()).isEqualTo("sub_test");
    verify(subscriptionRepository).save(subscription);
    verify(tenantApi).activatePlan(tenantId, "team");
}
```

- [ ] **Step 2: 실패 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingServiceTest" --no-daemon
```

Expected before implementation: `subscription.getPlanCode()`가 `PRO`로 남아 실패한다.

- [ ] **Step 3: Subscription 활성화 메서드가 plan/customer를 함께 갱신하게 한다**

`Subscription`에 기존 메서드를 대체하는 overload를 추가한다.

```java
public void activate(
        PlanCode planCode,
        String stripeCustomerId,
        String stripeSubscriptionId,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd) {
    this.planCode = planCode;
    this.stripeCustomerId = stripeCustomerId;
    this.stripeSubscriptionId = stripeSubscriptionId;
    this.status = SubscriptionStatus.ACTIVE;
    this.currentPeriodStart = periodStart;
    this.currentPeriodEnd = periodEnd;
    this.canceledAt = null;
    this.updatedAt = OffsetDateTime.now();
}
```

기존 테스트 호환을 위해 기존 `activate(String, OffsetDateTime, OffsetDateTime)`는 새 overload를 호출하게 유지한다.

```java
public void activate(String stripeSubscriptionId, OffsetDateTime periodStart, OffsetDateTime periodEnd) {
    activate(this.planCode, this.stripeCustomerId, stripeSubscriptionId, periodStart, periodEnd);
}
```

- [ ] **Step 4: BillingService에서 새 activate overload를 사용한다**

`handleCheckoutCompleted`의 activate 호출을 다음처럼 변경한다.

```java
subscription.activate(
        planCode,
        session.getCustomer(),
        session.getSubscription(),
        OffsetDateTime.now(),
        OffsetDateTime.now().plusMonths(1));
```

- [ ] **Step 5: domain 테스트에 plan/customer 갱신 검증을 추가한다**

`BillingDomainTest`의 subscription 활성화 테스트에 아래 검증을 추가한다.

```java
subscription.activate(
        PlanCode.TEAM,
        "cus_new",
        "sub_new",
        java.time.OffsetDateTime.now(),
        java.time.OffsetDateTime.now().plusMonths(1));

assertThat(subscription.getPlanCode()).isEqualTo(PlanCode.TEAM);
assertThat(subscription.getStripeCustomerId()).isEqualTo("cus_new");
assertThat(subscription.getStripeSubscriptionId()).isEqualTo("sub_new");
assertThat(subscription.getCanceledAt()).isNull();
```

- [ ] **Step 6: Billing service/domain 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingServiceTest" --tests "io.synapse.platform.billing.domain.BillingDomainTest" --no-daemon
```

Expected: pass.

### Task R3: Stripe-Signature 누락을 400으로 처리

**Files:**
- Modify: `src/main/java/io/synapse/platform/billing/BillingController.java`
- Modify: `src/main/java/io/synapse/platform/billing/BillingService.java`
- Modify: `src/test/java/io/synapse/platform/billing/BillingControllerTest.java`
- Modify: `src/test/java/io/synapse/platform/billing/BillingServiceTest.java`

- [ ] **Step 1: Controller 테스트에 헤더 누락 케이스를 추가한다**

```java
@Test
void handleWebhook_missingSignatureReturnsBadRequestProblem() throws Exception {
    mockMvc.perform(post("/api/v1/billing/webhooks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BILLING-002"));
}
```

- [ ] **Step 2: Service 테스트에 blank signature 케이스를 추가한다**

```java
@Test
void handleWebhook_blankSignatureThrowsBillingExceptionBeforeMutation() {
    assertThatThrownBy(() -> billingService.handleWebhook("{}".getBytes(StandardCharsets.UTF_8), " "))
            .isInstanceOf(BillingException.class)
            .extracting("errorCode")
            .isEqualTo("BILLING-002");
    verifyNoInteractions(processedEventRepository, subscriptionRepository, tenantApi);
}
```

- [ ] **Step 3: 실패 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingControllerTest" --tests "io.synapse.platform.billing.BillingServiceTest" --no-daemon
```

Expected before implementation: controller 헤더 누락 케이스가 500 또는 400이 아닌 응답으로 실패한다.

- [ ] **Step 4: Controller에서 signature header를 optional로 받는다**

```java
@PostMapping("/webhooks")
public ResponseEntity<Void> handleWebhook(
        @RequestBody byte[] payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
    billingService.handleWebhook(payload, sigHeader);
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 5: Service에서 null/blank signature를 `BILLING-002`로 변환한다**

`handleWebhook`에서 payload 문자열 생성 전에 guard를 추가한다.

```java
if (sigHeader == null || sigHeader.isBlank()) {
    throw new BillingException("BILLING-002", 400, "Invalid Stripe signature");
}
```

- [ ] **Step 6: Controller/Service 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingControllerTest" --tests "io.synapse.platform.billing.BillingServiceTest" --no-daemon
```

Expected: pass.

### Task R4: Checkout Redirect URL Allowlist 검증

**Files:**
- Modify: `src/main/java/io/synapse/platform/billing/config/StripeProperties.java`
- Modify: `src/main/java/io/synapse/platform/billing/BillingService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Modify: `.env.example`
- Modify: `src/test/java/io/synapse/platform/billing/BillingServiceTest.java`

- [ ] **Step 1: 허용되지 않은 successUrl을 거부하는 실패 테스트를 추가한다**

```java
@Test
void createCheckoutSession_rejectsUntrustedSuccessUrl() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
            userId, "billing@example.com", "Billing User", tenantId)));

    CheckoutSessionRequest request = new CheckoutSessionRequest(
            PlanCode.PRO,
            "https://evil.example.com/success",
            "https://app.example.com/cancel");

    assertThatThrownBy(() -> billingService.createCheckoutSession(userId, request))
            .isInstanceOf(BillingException.class)
            .extracting("errorCode")
            .isEqualTo("BILLING-007");
    verifyNoInteractions(stripeClient);
}
```

- [ ] **Step 2: 허용되지 않은 cancelUrl을 거부하는 실패 테스트를 추가한다**

```java
@Test
void createCheckoutSession_rejectsUntrustedCancelUrl() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    given(userApi.findById(userId)).willReturn(Optional.of(new UserInfo(
            userId, "billing@example.com", "Billing User", tenantId)));

    CheckoutSessionRequest request = new CheckoutSessionRequest(
            PlanCode.PRO,
            "https://app.example.com/success",
            "https://evil.example.com/cancel");

    assertThatThrownBy(() -> billingService.createCheckoutSession(userId, request))
            .isInstanceOf(BillingException.class)
            .extracting("errorCode")
            .isEqualTo("BILLING-007");
    verifyNoInteractions(stripeClient);
}
```

- [ ] **Step 3: StripeProperties에 checkout allowlist 설정을 추가한다**

```java
import java.util.List;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(Webhook webhook, Plans plans, Checkout checkout) {
    public record Webhook(String secret) {
    }

    public record Plans(Plan pro, Plan team, Plan enterprise) {
    }

    public record Plan(String priceId) {
    }

    public record Checkout(List<String> allowedRedirectOrigins) {
    }
}
```

- [ ] **Step 4: application 설정을 추가한다**

`src/main/resources/application.yml`:

```yaml
stripe:
  checkout:
    allowed-redirect-origins: ${STRIPE_CHECKOUT_ALLOWED_REDIRECT_ORIGINS:http://localhost:3000}
```

`src/test/resources/application.yml`:

```yaml
stripe:
  checkout:
    allowed-redirect-origins:
      - https://app.example.com
```

`.env.example`:

```dotenv
STRIPE_CHECKOUT_ALLOWED_REDIRECT_ORIGINS=http://localhost:3000
```

- [ ] **Step 5: BillingService 생성자 테스트 설정을 갱신한다**

`BillingServiceTest#setUp`에서 `StripeProperties` 생성자를 다음 형태로 맞춘다.

```java
new StripeProperties(
        new StripeProperties.Webhook(WEBHOOK_SECRET),
        new StripeProperties.Plans(
                new StripeProperties.Plan("price_pro"),
                new StripeProperties.Plan("price_team"),
                new StripeProperties.Plan("price_enterprise")),
        new StripeProperties.Checkout(java.util.List.of("https://app.example.com")))
```

- [ ] **Step 6: BillingService에서 redirect URL 검증을 Stripe 호출 전에 수행한다**

필요한 import:

```java
import java.net.URI;
import java.net.URISyntaxException;
```

`createCheckoutSession`에서 Stripe API 호출 전 guard를 추가한다.

```java
validateRedirectUrl(request.successUrl());
validateRedirectUrl(request.cancelUrl());
```

아래 private 메서드를 추가한다.

```java
private void validateRedirectUrl(String redirectUrl) {
    URI uri;
    try {
        uri = new URI(redirectUrl);
    } catch (URISyntaxException exception) {
        throw new BillingException("BILLING-007", 400, "Invalid checkout redirect URL");
    }
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
        throw new BillingException("BILLING-007", 400, "Invalid checkout redirect URL");
    }
    if (uri.getHost() == null || uri.getUserInfo() != null) {
        throw new BillingException("BILLING-007", 400, "Invalid checkout redirect URL");
    }
    String origin = uri.getScheme() + "://" + uri.getHost()
            + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    if (!stripeProperties.checkout().allowedRedirectOrigins().contains(origin)) {
        throw new BillingException("BILLING-007", 400, "Invalid checkout redirect URL");
    }
}
```

- [ ] **Step 7: BillingService 테스트 통과 확인**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.BillingServiceTest" --no-daemon
```

Expected: pass.

### Task R5: 최종 검증

**Files:**
- No planned edits.

- [ ] **Step 1: Billing 테스트 전체 실행**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.billing.*" --no-daemon
```

Expected: pass.

- [ ] **Step 2: Modulith 구조 검증**

Run:

```powershell
.\gradlew.bat test --tests "io.synapse.platform.PlatformModuleStructureTest" --no-daemon
```

Expected: pass.

- [ ] **Step 3: 전체 테스트 실행**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: pass. Director에게 전달할 핵심 결과다.

- [ ] **Step 4: 전체 build 실행**

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Expected: pass. Checkstyle, SpotBugs, JaCoCo coverage verification 포함.

- [ ] **Step 5: Done When 갱신**

아래 항목을 최종 보고에 포함한다.

- Billing webhook 외 API가 Spring Security에서 인증 필요로 보호됨
- 기존 ACTIVE subscription의 plan과 tenant plan이 같은 값으로 갱신됨
- Stripe-Signature 누락/blank가 400 `BILLING-002`로 처리됨
- Checkout redirect URL이 configured origin allowlist로 제한됨
- `.\gradlew.bat test --no-daemon` 결과 성공

## 리뷰 후속 플랜 Self-Review

**Spec coverage:** 리뷰에서 나온 4개 항목을 각각 Task R1-R4에 매핑했다. 보안 matcher, subscription plan 정합성, webhook signature 누락, checkout redirect allowlist가 모두 포함됐다.

**Placeholder scan:** `TBD`, `TODO`, “나중에 구현” 같은 placeholder는 없다. 각 작업은 수정 파일, 테스트 코드, 구현 코드, 실행 명령을 포함한다.

**Type consistency:** `BillingException` error code는 기존 패턴을 유지했고, 새 redirect 검증은 `BILLING-007`로 분리했다. `StripeProperties` 생성자 변경은 main/test 양쪽 설정과 unit test setup 갱신을 같은 Task R4에 포함했다.
