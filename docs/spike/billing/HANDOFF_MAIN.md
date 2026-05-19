# HANDOFF — Step 4 Stripe 결제 샘플링 결과 → 메인 프로젝트

> **작성**: Director (Claude)
> **날짜**: 2026-05-19
> **근거 문서**:
> - 조사 1차: `STRIPE_RESEARCH_SUMMARY.md`
> - 조사 2차: `STRIPE_RESEARCH_SUMMARY_2.md`
> - 샘플링 결과: `SAMPLING_STEP4_STRIPE.md`

---

## 1. 의존성

```kotlin
// build.gradle.kts
implementation("com.stripe:stripe-java:32.1.0")
```

---

## 2. 환경변수 (필수)

```yaml
# application-dev.yml / application-prod.yml
stripe:
  api:
    key: ${STRIPE_API_KEY}
  webhook:
    secret: ${STRIPE_WEBHOOK_SECRET}
  plans:
    pro:
      price-id: ${STRIPE_PRO_PRICE_ID}
    team:
      price-id: ${STRIPE_TEAM_PRICE_ID}
```

| 변수명 | 설명 | 발급처 |
|--------|------|--------|
| `STRIPE_API_KEY` | Stripe Secret Key (Test: `sk_test_...`) | Stripe 대시보드 |
| `STRIPE_WEBHOOK_SECRET` | Webhook 서명 검증 키 (`whsec_...`) | Stripe CLI 또는 대시보드 |
| `STRIPE_PRO_PRICE_ID` | PRO 플랜 Price ID (`price_...`) | Stripe 대시보드 |
| `STRIPE_TEAM_PRICE_ID` | TEAM 플랜 Price ID (`price_...`) | Stripe 대시보드 |

---

## 3. StripeClient Bean 등록

> ⚠️ **주의**: 조사 문서의 `StripeClientOptions` 클래스는 stripe-java 32.1.0에 **존재하지 않음**.
> `StripeClient.builder()` 방식으로 작성할 것.

```java
@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String apiKey;

    @Bean
    public StripeClient stripeClient() {
        return StripeClient.builder()
                .setApiKey(apiKey)
                .build();
    }
}
```

---

## 4. Flyway 마이그레이션 (V24 ~ V26)

현재 마지막 마이그레이션: `V23__add_refresh_tokens_user_unique.sql`

### V24__create_subscriptions.sql

```sql
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    plan_code VARCHAR(20) NOT NULL,                    -- FREE, PRO, TEAM
    stripe_subscription_id VARCHAR(255),               -- Stripe subscription ID (sub_...)
    status VARCHAR(20) NOT NULL DEFAULT 'active',      -- active, canceled, past_due
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_tenant_id ON subscriptions (tenant_id);
```

### V25__create_payment_history.sql

```sql
CREATE TABLE payment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    subscription_id UUID REFERENCES subscriptions(id),  -- nullable FK (샘플링 결과 참고)
    stripe_payment_intent_id VARCHAR(255),
    amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'usd',
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_history_tenant_id ON payment_history (tenant_id);
CREATE INDEX idx_payment_history_subscription_id ON payment_history (subscription_id);
```

### V26__create_processed_events.sql

```sql
CREATE TABLE processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    payload JSONB
);

CREATE INDEX idx_processed_events_received_at ON processed_events (received_at);
```

---

## 5. BillingController 구현 포인트

### Checkout Session 생성

```java
@PostMapping("/billing/checkout")
public ResponseEntity<CheckoutSessionResponse> createCheckout(
        @RequestBody CheckoutSessionRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {  // JWT에서 tenantId 추출
    String url = billingService.createCheckoutSession(request.getPlanCode(), principal.getTenantId());
    return ResponseEntity.ok(new CheckoutSessionResponse(url));
}
```

### Webhook 수신

```java
@PostMapping("/billing/webhooks")
public ResponseEntity<Void> handleWebhook(
        @RequestBody byte[] payload,                          // raw body 직접 수신 (필터 방식 X)
        @RequestHeader("Stripe-Signature") String sigHeader) {
    billingService.handleWebhook(payload, sigHeader);
    return ResponseEntity.ok().build();
}
```

---

## 6. BillingService 구현 포인트

### Checkout Session 생성

```java
public String createCheckoutSession(PlanCode planCode, UUID tenantId) {
    String priceId = resolvePriceId(planCode);  // application.yml 매핑

    SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl("https://synapse.com/billing/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl("https://synapse.com/billing/cancel")
            .addLineItem(SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build())
            .putMetadata("tenant_id", tenantId.toString())
            .putMetadata("plan_code", planCode.name())
            .build();

    Session session = stripeClient.checkout().sessions().create(params);
    return session.getUrl();
}
```

### Webhook 서명 검증 + 이벤트 분기

```java
public void handleWebhook(byte[] payload, String sigHeader) {
    Event event;
    try {
        event = Webhook.constructEvent(new String(payload, StandardCharsets.UTF_8), sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
        throw new InvalidWebhookSignatureException();  // → 400
    }

    // 멱등성 체크
    int inserted = processedEventRepository.insertIfAbsent(event.getId(), event.getType());
    if (inserted == 0) return;  // 중복 이벤트 → 200 OK

    switch (event.getType()) {
        case "checkout.session.completed" -> handleCheckoutCompleted(event);
        case "invoice.paid"               -> handleInvoicePaid(event);
        case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
    }
}
```

### 멱등성 INSERT (Native Query)

```java
@Modifying
@Query(value = """
    INSERT INTO processed_events (event_id, event_type)
    VALUES (:eventId, :eventType)
    ON CONFLICT (event_id) DO NOTHING
    """, nativeQuery = true)
int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType);
```

---

## 7. SecurityConfig 수정 사항

```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/billing/webhooks").permitAll()  // Stripe Webhook 인증 면제
        .anyRequest().authenticated()
);
// 현재 프로젝트 CSRF 전역 disable 상태 → 별도 ignoringRequestMatchers 불필요
// 향후 CSRF 활성화 시: csrf.ignoringRequestMatchers("/billing/webhooks") 추가 필요
```

---

## 8. 로컬 테스트 방법 (Stripe CLI)

```bash
# 1. Stripe CLI 설치 후 로그인
stripe login

# 2. 로컬 Webhook 포워딩 (출력된 whsec_... 값을 STRIPE_WEBHOOK_SECRET에 설정)
stripe listen --forward-to localhost:8081/billing/webhooks

# 3. 이벤트 트리거
stripe trigger checkout.session.completed

# 테스트 카드
# 성공: 4242 4242 4242 4242
# 잔액 부족: 4000 0000 0000 0002
```

---

## 9. 미해결 사항 (메인 프로젝트에서 확정 필요)

| # | 항목 | 현황 | 권장 방향 |
|---|------|------|----------|
| 1 | tenant 식별 방식 | 샘플은 `X-Tenant-Id` 헤더로 우회 | JWT principal → `TenantMember` 조회로 tenantId 확정 |
| 2 | Stripe CLI 실수신 | 환경변수 없어 미수행 | 실제 API Key 발급 후 로컬 CLI 테스트 필수 |
| 3 | payment_history.subscription_id | nullable FK로 처리 | Webhook 처리 시 subscription row 조회/생성 결과 id 연결 로직 추가 |
| 4 | processed_events cleanup | 설계만 존재 | 30일 이상 이벤트 삭제 배치 또는 pg_cron 설정 필요 |
