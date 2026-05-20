# HANDOFF — TO: Worker (Codex)

> Step 4: Stripe Checkout 결제 및 Webhook 플랜 활성화
> 작성자: Director (Claude) / 2026-05-19

---

## 목표

billing 모듈을 처음부터 구현한다.
Stripe Checkout으로 유료 플랜을 결제하고, Webhook으로 tenant.plan이 활성화되어야 한다.

---

## 현재 코드베이스 상태

- base package: `io.synapse.platform`
- 모듈: auth, user, notification, admin, shared (billing 패키지 없음 — 신규 생성 필요)
- Stripe SDK: build.gradle.kts에 없음 — 추가 필요
- Flyway 현황: V1~V23 완료, 다음은 V24, V25, V26
- tenants 테이블: V2에서 생성, `plan VARCHAR(50) NOT NULL DEFAULT 'free'`
- UserInfo: `id, email, displayName, defaultTenantId` (UUID)
- JWT 인증: `JwtAuthenticationFilter` → `Authentication.getPrincipal()` = userId (String)
- 기존 패턴: `UuidCreator.getTimeOrderedEpoch()` for UUID, `@PrePersist` for timestamps

---

## 작업 목록 (순서 준수)

### 0. build.gradle.kts 수정

#### Stripe SDK 추가

```kotlin
implementation("com.stripe:stripe-java:32.1.0")
```

> **[스파이크 확인]** 32.1.0이 2026-05 기준 안정 최신. Spring Boot 4.0.0(Jakarta EE 11)과 호환 확인됨. 32.2.0-beta.2는 베타 제외.

#### JaCoCo 추가 (커버리지 80% 검증용)

```kotlin
plugins {
    // 기존 플러그인에 추가
    jacoco
}

// 파일 맨 아래에 추가
jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("io.synapse.platform.billing*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

---

### 1. Flyway V24 — subscriptions 테이블

파일: `src/main/resources/db/migration/V24__create_subscriptions.sql`

```sql
CREATE TABLE subscriptions (
    id                     UUID         PRIMARY KEY,
    tenant_id              UUID         NOT NULL REFERENCES tenants(id),
    plan_code              VARCHAR(20)  NOT NULL,
    stripe_customer_id     VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    current_period_start   TIMESTAMPTZ,
    current_period_end     TIMESTAMPTZ,
    canceled_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_subscriptions_tenant_active
    ON subscriptions(tenant_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_subscriptions_stripe_sub_id
    ON subscriptions(stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;
CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id);
```

> **[수정 D-024]** status 기본값과 partial index를 UPPERCASE로 통일. Java `@Enumerated(EnumType.STRING)`이 `ACTIVE`를 저장하므로 DDL도 동일하게 맞춘다.

---

### 1-b. 수정 사항 (스파이크 결과 반영)

- `stripe_customer_id` → nullable로 변경 (checkout.session.completed webhook에서 채움)
- `DEFAULT gen_random_uuid()` → `id UUID PRIMARY KEY` + `@PrePersist`로 애플리케이션에서 생성 (기존 패턴 준수)
- status UPPERCASE 이미 반영됨 (D-024)

---

### 2. Flyway V25 — payment_history 테이블

파일: `src/main/resources/db/migration/V25__create_payment_history.sql`

```sql
CREATE TABLE payment_history (
    id                       UUID         PRIMARY KEY,
    tenant_id                UUID         NOT NULL REFERENCES tenants(id),
    subscription_id          UUID         REFERENCES subscriptions(id),
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    amount                   INTEGER      NOT NULL,
    currency                 VARCHAR(3)   NOT NULL DEFAULT 'usd',
    status                   VARCHAR(20)  NOT NULL,
    paid_at                  TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_history_tenant_id       ON payment_history(tenant_id);
CREATE INDEX idx_payment_history_subscription_id ON payment_history(subscription_id);
```

---

### 2-b. Flyway V26 — processed_events 테이블 (멱등성)

파일: `src/main/resources/db/migration/V26__create_processed_events.sql`

```sql
CREATE TABLE processed_events (
    event_id    VARCHAR(255) PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_processed_events_received_at ON processed_events(received_at);
```

> **[스파이크 결과 D-025 대체]** `payment_history.stripe_payment_intent_id` UNIQUE 방식 대신
> `processed_events` 테이블로 전체 이벤트 멱등성을 통합 처리한다.
> `event.id`(`evt_...`)를 키로 사용 — 모든 이벤트 타입에 일관 적용.

---

### 3. auth 모듈 — TenantApi @NamedInterface 추가

billing 모듈이 tenant.plan을 업데이트할 때 auth 모듈 경계를 통해 접근한다.

#### 3-1. `src/main/java/io/synapse/platform/auth/api/package-info.java`

```java
@org.springframework.modulith.NamedInterface("tenant-api")
package io.synapse.platform.auth.api;
```

#### 3-2. `src/main/java/io/synapse/platform/auth/api/TenantInfo.java`

```java
package io.synapse.platform.auth.api;

import java.util.UUID;

public record TenantInfo(UUID id, String plan, String status) {}
```

#### 3-3. `src/main/java/io/synapse/platform/auth/api/TenantApi.java`

```java
package io.synapse.platform.auth.api;

import java.util.Optional;
import java.util.UUID;

public interface TenantApi {
    Optional<TenantInfo> findById(UUID tenantId);
    void activatePlan(UUID tenantId, String planCode);
}
```

#### 3-4. `src/main/java/io/synapse/platform/auth/TenantService.java`

기존 TenantRepository를 주입받아 TenantApi 구현체를 만든다.

```java
package io.synapse.platform.auth;

import io.synapse.platform.auth.api.TenantApi;
import io.synapse.platform.auth.api.TenantInfo;
import io.synapse.platform.auth.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantService implements TenantApi {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<TenantInfo> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> new TenantInfo(t.getId(), t.getPlan(), t.getStatus()));
    }

    @Override
    @Transactional
    public void activatePlan(UUID tenantId, String planCode) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.activatePlan(planCode);
            tenantRepository.save(tenant);
        });
    }
}
```

#### 3-5. Tenant 엔티티에 메서드/getter 추가 (`auth/domain/Tenant.java`)

기존 `Tenant.java`에 다음을 추가한다:

```java
// getter 추가
public String getPlan() { return plan; }
public String getStatus() { return status; }

// activatePlan 메서드 추가
public void activatePlan(String planCode) {
    this.plan = planCode;
    this.updatedAt = OffsetDateTime.now();
}
```

---

### 4. billing 모듈 생성

#### 4-1. `src/main/java/io/synapse/platform/billing/package-info.java`

```java
@org.springframework.modulith.ApplicationModule
package io.synapse.platform.billing;
```

#### 4-2. `src/main/java/io/synapse/platform/billing/domain/PlanCode.java`

```java
package io.synapse.platform.billing.domain;

public enum PlanCode {
    FREE, PRO, TEAM, ENTERPRISE;

    public String value() { return name().toLowerCase(); }
}
```

#### 4-3. `src/main/java/io/synapse/platform/billing/domain/SubscriptionStatus.java`

```java
package io.synapse.platform.billing.domain;

public enum SubscriptionStatus {
    ACTIVE, CANCELED, PAST_DUE, TRIALING
}
```

#### 4-4. `src/main/java/io/synapse/platform/billing/domain/Subscription.java`

```java
package io.synapse.platform.billing.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanCode planCode;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start")
    private OffsetDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Subscription() {}

    public static Subscription create(UUID tenantId, PlanCode planCode, String stripeCustomerId) {
        Subscription s = new Subscription();
        s.tenantId = tenantId;
        s.planCode = planCode;
        s.stripeCustomerId = stripeCustomerId;
        return s;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidCreator.getTimeOrderedEpoch();
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    public void activate(String stripeSubscriptionId, OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public PlanCode getPlanCode() { return planCode; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public SubscriptionStatus getStatus() { return status; }
    public OffsetDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public OffsetDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public OffsetDateTime getCanceledAt() { return canceledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

#### 4-5. `src/main/java/io/synapse/platform/billing/domain/PaymentHistory.java`

```java
package io.synapse.platform.billing.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_history")
public class PaymentHistory {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private String currency = "usd";

    @Column(nullable = false)
    private String status;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PaymentHistory() {}

    public static PaymentHistory of(UUID tenantId, UUID subscriptionId,
                                     String stripePaymentIntentId, int amount,
                                     String currency, String status, OffsetDateTime paidAt) {
        PaymentHistory ph = new PaymentHistory();
        ph.tenantId = tenantId;
        ph.subscriptionId = subscriptionId;
        ph.stripePaymentIntentId = stripePaymentIntentId;
        ph.amount = amount;
        ph.currency = currency;
        ph.status = status;
        ph.paidAt = paidAt;
        return ph;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidCreator.getTimeOrderedEpoch();
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

---

### 4-f. `src/main/java/io/synapse/platform/billing/repository/ProcessedEventRepository.java`

```java
package io.synapse.platform.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<io.synapse.platform.billing.domain.ProcessedEvent, String> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, event_type)
            VALUES (:eventId, :eventType)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType);
}
```

#### `src/main/java/io/synapse/platform/billing/domain/ProcessedEvent.java`

```java
package io.synapse.platform.billing.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected ProcessedEvent() {}

    @PrePersist
    void prePersist() { receivedAt = OffsetDateTime.now(); }
}
```

---

### 5. Repository

#### `src/main/java/io/synapse/platform/billing/repository/SubscriptionRepository.java`

```java
package io.synapse.platform.billing.repository;

import io.synapse.platform.billing.domain.Subscription;
import io.synapse.platform.billing.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
```

#### `src/main/java/io/synapse/platform/billing/repository/PaymentHistoryRepository.java`

```java
package io.synapse.platform.billing.repository;

import io.synapse.platform.billing.domain.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {
}
```

---

### 6. StripeProperties — 설정 바인딩

#### `src/main/java/io/synapse/platform/billing/config/StripeProperties.java`

```java
package io.synapse.platform.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        Webhook webhook,
        Plans plans
) {
    public record Webhook(String secret) {}
    public record Plans(Plan pro, Plan team, Plan enterprise) {}
    public record Plan(String priceId) {}
}
```

#### `src/main/java/io/synapse/platform/billing/config/StripeConfig.java`

> **[스파이크 수정]** `Stripe.apiKey` 정적 setter 대신 `StripeClient.builder()` Bean 등록.
> v32.x에서 `StripeClientOptions` 클래스는 존재하지 않음 — builder 방식 사용.

```java
package io.synapse.platform.billing.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(@Value("${stripe.api.key}") String apiKey) {
        return StripeClient.builder()
                .setApiKey(apiKey)
                .build();
    }
}
```

#### application.yml (dev 프로파일)에 추가

> **[스파이크 확인]** 키 네이밍 스파이크 결과 기준으로 통일.

```yaml
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
    enterprise:
      price-id: ${STRIPE_ENTERPRISE_PRICE_ID}
```

---

### 7. DTO

#### `src/main/java/io/synapse/platform/billing/dto/CheckoutSessionRequest.java`

```java
package io.synapse.platform.billing.dto;

import io.synapse.platform.billing.domain.PlanCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutSessionRequest(
        @NotNull PlanCode planCode,
        @NotBlank @Size(max = 500) String successUrl,
        @NotBlank @Size(max = 500) String cancelUrl
) {}
```

#### `src/main/java/io/synapse/platform/billing/dto/CheckoutSessionResponse.java`

```java
package io.synapse.platform.billing.dto;

public record CheckoutSessionResponse(String checkoutUrl) {}
```

#### `src/main/java/io/synapse/platform/billing/dto/SubscriptionResponse.java`

```java
package io.synapse.platform.billing.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String planCode,
        String status,
        OffsetDateTime currentPeriodEnd,
        String stripeSubscriptionId
) {}
```

---

### 8. BillingService

`src/main/java/io/synapse/platform/billing/BillingService.java`

의존성: SubscriptionRepository, PaymentHistoryRepository, ProcessedEventRepository, UserApi, TenantApi, StripeProperties, StripeClient

```java
package io.synapse.platform.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.synapse.platform.auth.api.TenantApi;
import io.synapse.platform.billing.config.StripeProperties;
import io.synapse.platform.billing.domain.*;
import io.synapse.platform.billing.dto.*;
import io.synapse.platform.billing.exception.BillingException;
import io.synapse.platform.billing.repository.*;
import io.synapse.platform.user.api.UserApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final UserApi userApi;
    private final TenantApi tenantApi;
    private final StripeProperties stripeProperties;
    private final StripeClient stripeClient;

    public BillingService(
            SubscriptionRepository subscriptionRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            ProcessedEventRepository processedEventRepository,
            UserApi userApi,
            TenantApi tenantApi,
            StripeProperties stripeProperties,
            StripeClient stripeClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.processedEventRepository = processedEventRepository;
        this.userApi = userApi;
        this.tenantApi = tenantApi;
        this.stripeProperties = stripeProperties;
        this.stripeClient = stripeClient;
    }

    // [스파이크 수정] Session.create() 정적 호출 → stripeClient.checkout().sessions().create()
    public CheckoutSessionResponse createCheckoutSession(UUID userId, CheckoutSessionRequest request) {
        UUID tenantId = resolveTenantId(userId);
        String priceId = resolvePriceId(request.planCode());
        try {
            Session session = stripeClient.checkout().sessions().create(
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                            .addLineItem(SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build())
                            .setSuccessUrl(request.successUrl())
                            .setCancelUrl(request.cancelUrl())
                            .putMetadata("tenant_id", tenantId.toString())
                            .putMetadata("plan_code", request.planCode().name())
                            .build());
            return new CheckoutSessionResponse(session.getUrl());
        } catch (StripeException e) {
            throw new BillingException("BILLING-001", 502, "Failed to create checkout session");
        }
    }

    // [스파이크 수정] byte[] raw body + UTF-8 변환. 멱등성은 processed_events ON CONFLICT로 통합 처리.
    @Transactional
    public void handleWebhook(byte[] rawPayload, String sigHeader) {
        String payload = new String(rawPayload, java.nio.charset.StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeProperties.webhook().secret());
        } catch (SignatureVerificationException e) {
            throw new BillingException("BILLING-002", 400, "Invalid Stripe signature");
        }

        // 멱등성: event.id 기준 INSERT-FIRST → 중복이면 0 반환 → early return
        int inserted = processedEventRepository.insertIfAbsent(event.getId(), event.getType());
        if (inserted == 0) return;

        switch (event.getType()) {
            case "checkout.session.completed"    -> handleCheckoutCompleted(event);
            case "invoice.paid"                  -> handleInvoicePaid(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            default -> { }
        }
    }

    public SubscriptionResponse getSubscription(UUID userId) {
        UUID tenantId = resolveTenantId(userId);
        return subscriptionRepository
                .findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .map(s -> new SubscriptionResponse(
                        s.getId(), s.getPlanCode().name(), s.getStatus().name(),
                        s.getCurrentPeriodEnd(), s.getStripeSubscriptionId()))
                .orElseThrow(() -> new BillingException("BILLING-003", 404, "No active subscription found"));
    }

    private void handleCheckoutCompleted(Event event) {
        // 역할: Subscription 생성/활성화 + tenant.plan 업데이트
        // payment_history 저장은 invoice.paid 에서 처리 (D-024)
        Session session = (Session) deserialize(event);
        UUID tenantId = UUID.fromString(session.getMetadata().get("tenant_id"));
        PlanCode planCode = PlanCode.valueOf(session.getMetadata().get("plan_code"));

        Subscription subscription = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseGet(() -> {
                    Subscription s = Subscription.create(tenantId, planCode, session.getCustomer());
                    return subscriptionRepository.save(s);
                });

        subscription.activate(
                session.getSubscription(), OffsetDateTime.now(), OffsetDateTime.now().plusMonths(1));
        subscriptionRepository.save(subscription);

        tenantApi.activatePlan(tenantId, planCode.value());
    }

    private void handleInvoicePaid(Event event) {
        // 역할: 결제 이력 저장 (subscription mode 실제 결제 확인 지점)
        // 멱등성은 상위 handleWebhook의 processed_events INSERT-FIRST로 이미 보장됨
        com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) deserialize(event);
        String paymentIntentId = invoice.getPaymentIntent();
        String stripeSubscriptionId = invoice.getSubscription();
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            paymentHistoryRepository.save(PaymentHistory.of(
                    sub.getTenantId(), sub.getId(), paymentIntentId,
                    invoice.getAmountPaid() != null ? invoice.getAmountPaid().intValue() : 0,
                    invoice.getCurrency() != null ? invoice.getCurrency() : "usd",
                    "succeeded", OffsetDateTime.now()));
        });
    }

    private void handleSubscriptionDeleted(Event event) {
        // 구독 취소 시 tenant.plan을 'free'로 복원 (D-026: 플랜 비활성화 로직)
        com.stripe.model.Subscription stripeSub =
                (com.stripe.model.Subscription) deserialize(event);
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresent(s -> {
                    s.cancel();
                    subscriptionRepository.save(s);
                    tenantApi.activatePlan(s.getTenantId(), PlanCode.FREE.value());
                });
    }

    private UUID resolveTenantId(UUID userId) {
        return userApi.findById(userId)
                .map(u -> u.defaultTenantId())
                .orElseThrow(() -> new BillingException("BILLING-004", 404, "User not found"));
    }

    private String resolvePriceId(PlanCode planCode) {
        return switch (planCode) {
            case PRO -> stripeProperties.plans().pro().priceId();
            case TEAM -> stripeProperties.plans().team().priceId();
            case ENTERPRISE -> stripeProperties.plans().enterprise().priceId();
            default -> throw new BillingException("BILLING-005", 400, "Invalid plan for checkout");
        };
    }

    private com.stripe.model.StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer.getObject()
                .orElseThrow(() -> new BillingException("BILLING-006", 500, "Failed to deserialize Stripe event"));
    }
}
```

---

### 9. BillingException

`src/main/java/io/synapse/platform/billing/exception/BillingException.java`

```java
package io.synapse.platform.billing.exception;

import io.synapse.platform.shared.exception.BusinessException;

public class BillingException extends BusinessException {
    public BillingException(String errorCode, int status, String message) {
        super(errorCode, status, message);
    }
}
```

---

### 10. BillingController

`src/main/java/io/synapse/platform/billing/BillingController.java`

```java
package io.synapse.platform.billing;

import io.synapse.platform.billing.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutSessionResponse> createCheckout(
            Authentication authentication,
            @Valid @RequestBody CheckoutSessionRequest request) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(billingService.createCheckoutSession(userId, request));
    }

    // [스파이크 확인] byte[] raw body 직접 수신 → UTF-8 변환은 Service에서 처리
    @PostMapping("/webhooks")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        billingService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> getSubscription(Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(billingService.getSubscription(userId));
    }
}
```

---

### 11. SecurityConfig 수정 — Webhook permitAll

`auth/config/SecurityConfig.java` `.authorizeHttpRequests()` 블록에 추가:

```java
.requestMatchers(
    "/actuator/**",
    "/oauth2/**",
    "/login/**",
    "/api/v1/auth/callback",
    "/api/v1/auth/refresh",
    "/api/v1/billing/webhooks"   // ← 추가
).permitAll()
```

---

### 12. PlatformApplication — @ConfigurationPropertiesScan 확인

`PlatformApplication.java`에 `@ConfigurationPropertiesScan`이 있는지 확인하고 없으면 추가.
(StripeProperties 바인딩에 필요)

---

### 13. 통합 테스트

`src/test/java/io/synapse/platform/billing/BillingIntegrationTest.java`

- Testcontainers PostgreSQL + Redis (기존 패턴)
- Stripe API: Mockito mock
- 테스트 케이스 8건:
  1. `POST /billing/checkout` 정상 → 200 + checkoutUrl
  2. `POST /billing/checkout` 인증 없음 → 401
  3. `POST /billing/webhooks` 유효 서명 + `checkout.session.completed` → 200, subscription ACTIVE, tenant.plan 갱신
  4. `POST /billing/webhooks` 유효 서명 + `invoice.paid` → 200, payment_history 저장됨
  5. `POST /billing/webhooks` 동일 `event.id` 재전송 → 200 (processed_events ON CONFLICT, 비즈니스 로직 미실행)
  6. `POST /billing/webhooks` 잘못된 서명 → 400
  7. `GET /billing/subscription` 활성 구독 있음 → 200
  8. `GET /billing/subscription` 활성 구독 없음 → 404

---

## 제약 사항 (반드시 준수)

1. billing → auth 경계: `TenantApi` @NamedInterface만 사용, TenantRepository 직접 주입 금지
2. billing → user 경계: `UserApi` @NamedInterface만 사용
3. Stripe API Key: 하드코딩 금지, 환경변수에서만 읽기
4. Webhook 서명 검증: `Webhook.constructEvent()` 반드시 사용
5. 멱등성: `processed_events` 테이블 + `event.id` ON CONFLICT DO NOTHING (D-025)
6. 테스트 커버리지: 신규 코드 80% 이상
7. `PlatformModuleStructureTest` 통과 필수

## 검증 순서

```
./gradlew compileJava
./gradlew test --tests "io.synapse.platform.PlatformModuleStructureTest"
./gradlew test --tests "io.synapse.platform.billing.*"
./gradlew build
```

## 완료 기준

- [ ] `./gradlew build` 성공
- [ ] `PlatformModuleStructureTest` 통과
- [ ] 통합 테스트 8케이스 모두 통과
- [ ] billing 모듈 커버리지 80% 이상
