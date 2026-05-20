# SAMPLING — Step 4: Stripe Checkout 결제 및 Webhook

> **목적**: Step 4 구현 전, 샘플 코드에서 Stripe 결제 핵심 기술을 검증한다.
> 샘플링 결과를 바탕으로 본 프로젝트 설계 및 구현 방향을 확정한다.

---

## 샘플링 환경

| 항목 | 내용 |
|------|------|
| 기반 프로젝트 | synapse-platform-svc 복사본 |
| 기술 스택 | Spring Boot 4.0.0 + Java 21 + Spring Modulith 1.3.0 |
| 빌드 | Gradle (Kotlin DSL) |
| Stripe SDK | stripe-java:32.1.0 |
| 테스트 모드 | Stripe Test Mode + Stripe CLI |
| 목표 기간 | 1일 |

---

## 샘플링 목표 전체 목록

| # | 항목 | 리스크 | 핵심 검증 포인트 |
|---|------|--------|----------------|
| A | StripeClient Bean 등록 + application.yml 연동 | LOW | `StripeClientOptions` 초기화, Key 주입 |
| B | Checkout Session 생성 API | MEDIUM | `SessionCreateParams` 구성, metadata 포함, URL 반환 |
| C | Webhook raw body 수신 + 서명 검증 | HIGH | `@RequestBody byte[]` + `Webhook.constructEvent` 동작 |
| D | Spring Security CSRF 예외 + permitAll | MEDIUM | `/billing/webhooks` 인증 면제 + 기존 Security 설정 충돌 여부 |
| E | 멱등성 처리 (processed_events) | MEDIUM | `ON CONFLICT DO NOTHING` + 중복 이벤트 200 OK 반환 |
| F | Flyway V24~V26 마이그레이션 | LOW | subscriptions, payment_history, processed_events DDL 적용 |

---

## A. StripeClient Bean 등록

### 목적

`StripeClient`를 Spring Bean으로 등록하고 `application.yml` 값을 주입받는 구조 검증.

### 의존성

```kotlin
implementation("com.stripe:stripe-java:32.1.0")
```

### 검증 항목

- [ ] `@Value("${stripe.api.key}")` 주입 정상 동작
- [ ] `StripeClientOptions.builder()` 빌드 성공
- [ ] 애플리케이션 기동 시 `StripeClient` Bean 생성 성공

### 결과

- 동작 여부: 코드 레벨 검증 완료. `StripeClient` Bean은 `StripeClient.builder()`로 생성해야 하며, `StripeClientOptions` 클래스는 stripe-java 32.1.0에 존재하지 않는다.
- 발견된 문제: 1차 조사 문서의 `StripeClientOptions` 예시는 현재 SDK와 불일치.
- 메인 프로젝트 반영 시 주의사항: API Key는 `${STRIPE_API_KEY}` 환경변수로만 주입하고, 테스트/로컬 기본값은 빈 값 또는 placeholder만 사용한다.

---

## B. Checkout Session 생성 API

### 목적

`POST /billing/checkout` 호출 시 Stripe Checkout Session이 생성되고 결제 URL이 반환되는지 검증.

### 검증 항목

- [ ] `SessionCreateParams` 구성 (mode=SUBSCRIPTION, line_items, success_url, cancel_url)
- [ ] `metadata`에 `tenant_id`, `plan_code` 포함 확인
- [ ] Stripe Test Mode에서 실제 Checkout URL 반환 확인
- [ ] PlanCode → Price ID 매핑 (`application.yml` 기반)

### 결과

- 동작 여부: 단위 테스트 통과. `POST /billing/checkout` → `BillingService.createCheckoutSession()` → `StripeCheckoutClient` 경계까지 URL 반환 흐름을 검증했다.
- 발견된 문제: 인증 컨텍스트에서 tenant를 바로 얻는 공통 계약이 없어 샘플에서는 `X-Tenant-Id` 헤더로 tenant를 전달한다.
- 메인 프로젝트 반영 시 주의사항: 실제 반영 시 JWT principal 또는 tenant context에서 tenantId를 확정하는 공통 방식이 필요하다.

---

## C. Webhook raw body 수신 + 서명 검증

### 목적

`@RequestBody byte[] payload`로 raw body를 수신하고 `Webhook.constructEvent`로 서명 검증이 통과하는지 확인.
필터 방식 대신 Controller에서 직접 수신하는 방식 채택.

### 검증 항목

- [ ] `@RequestBody byte[]` + `@RequestHeader("Stripe-Signature")` 수신 성공
- [ ] `Webhook.constructEvent(new String(payload), sigHeader, secret)` 서명 검증 통과
- [ ] `SignatureVerificationException` 발생 시 400 반환 확인
- [ ] Stripe CLI `stripe trigger checkout.session.completed`로 로컬 테스트 통과

### 결과

- 동작 여부: 단위 테스트 통과. `@RequestBody byte[]` raw payload와 `Stripe-Signature` 헤더가 서비스로 그대로 전달되고, `Webhook.constructEvent()` 서명 검증을 통과하는 payload 처리까지 확인했다.
- 발견된 문제: 로컬 Stripe CLI 실수신은 실제 `STRIPE_WEBHOOK_SECRET`, Price ID가 없어 수행하지 않았다.
- 메인 프로젝트 반영 시 주의사항: Stripe CLI의 `whsec_...` 값을 `STRIPE_WEBHOOK_SECRET`으로 맞추지 않으면 검증은 400으로 실패한다.

---

## D. Spring Security CSRF 예외 + permitAll

### 목적

`/billing/webhooks`가 CSRF 예외 처리되고 인증 없이 접근 가능한지, 기존 Security 설정과 충돌이 없는지 확인.

### 검증 항목

- [ ] `csrf.ignoringRequestMatchers("/billing/webhooks")` 적용 후 Webhook 수신 성공
- [ ] `permitAll()` 적용 후 JWT 없이 `/billing/webhooks` 접근 가능
- [ ] 기존 JWT 인증 필터와 충돌 없음 확인
- [ ] `/billing/checkout`은 JWT 인증 정상 요구 확인

### 결과

- 동작 여부: `/billing/webhooks`는 `permitAll()`에 추가했다. 현재 프로젝트는 기존 설정상 CSRF가 전역 disable 상태라 webhook CSRF 충돌은 발생하지 않는다.
- 발견된 문제: HANDOFF는 CSRF 예외를 요구하지만 기존 SecurityConfig가 이미 `csrf(AbstractHttpConfigurer::disable)` 구조라 별도 `ignoringRequestMatchers`는 동작상 의미가 없다.
- 메인 프로젝트 반영 시 주의사항: 향후 CSRF를 다시 활성화하면 `/billing/webhooks`에 `csrf.ignoringRequestMatchers("/billing/webhooks")`를 추가해야 한다.

---

## E. 멱등성 처리 (processed_events)

### 목적

동일한 Stripe 이벤트가 중복 수신될 때 한 번만 처리되고 200 OK가 반환되는지 검증.

### 검증 항목

- [ ] `processed_events` 테이블 INSERT 성공 (최초 수신)
- [ ] 동일 `event_id` 재수신 시 `ON CONFLICT DO NOTHING` 동작 확인
- [ ] 중복 수신 시 비즈니스 로직 미실행 + 200 OK 반환 확인
- [ ] `INSERT INTO processed_events ... ON CONFLICT (event_id) DO NOTHING` 쿼리 동작

### 결과

- 동작 여부: 단위 테스트 통과. `processed_events`에 `ON CONFLICT (event_id) DO NOTHING` insert-first 방식으로 중복 이벤트는 비즈니스 로직 없이 duplicate=true, 200 OK 응답으로 처리한다.
- 발견된 문제: 없음.
- 메인 프로젝트 반영 시 주의사항: 운영에서는 `processed_events` 보존 기간과 cleanup job이 필요하다.

---

## F. Flyway V24~V26 마이그레이션

### 목적

3개 테이블 DDL이 PostgreSQL에 정상 적용되는지 확인.

### 검증 항목

- [ ] V24 subscriptions 테이블 생성 성공 (tenant_id FK 포함)
- [ ] V25 payment_history 테이블 생성 성공 (subscription_id FK 포함)
- [ ] V26 processed_events 테이블 생성 성공 (event_id PRIMARY KEY)
- [ ] `./gradlew build` 전체 통과

### 결과

- 동작 여부: `V24__create_subscriptions.sql`, `V25__create_payment_history.sql`, `V26__create_processed_events.sql` 작성 완료. `./gradlew.bat build` 통과.
- 발견된 문제: `payment_history.subscription_id`는 샘플 webhook payload만으로 내부 subscription UUID를 안전하게 확정하기 어려워 nullable FK로 두었다.
- 메인 프로젝트 반영 시 주의사항: 결제 이력과 내부 subscription row 연결을 강제하려면 webhook 처리에서 subscription 조회/생성 결과 id를 반환하는 repository 계층을 분리하는 편이 낫다.

---

## 최종 샘플링 결과 요약

> Worker 샘플 완료 후 Director가 작성

| # | 항목 | 결과 | 메인 프로젝트 반영 여부 |
|---|------|------|----------------------|
| A | StripeClient Bean 등록 | 통과 | 반영 가능 |
| B | Checkout Session 생성 | 단위 테스트 통과, 실제 Stripe 호출 미수행 | tenant context 보완 후 반영 |
| C | Webhook 서명 검증 | 단위 테스트 통과, Stripe CLI 실수신 미수행 | 환경변수 세팅 후 반영 |
| D | Security CSRF 예외 | permitAll 반영, 기존 CSRF 전역 disable 유지 | CSRF 재활성화 시 예외 추가 |
| E | 멱등성 처리 | 통과 | cleanup 정책 추가 후 반영 |
| F | Flyway 마이그레이션 | build 통과 | payment_history 연결 정책 보완 후 반영 |

## 메인 프로젝트 반영 시 주의사항 (종합)

- Stripe SDK 32.1.0은 `StripeClient.builder()`를 사용한다. 조사 문서의 `StripeClientOptions` 예시는 그대로 쓰면 컴파일 실패한다.
- 샘플 API는 tenant 식별을 `X-Tenant-Id` 헤더로 받는다. 운영 반영 시 인증된 사용자와 tenant membership 검증을 반드시 추가해야 한다.
- 실제 Stripe Test Mode 검증에는 `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRO_PRICE_ID`, `STRIPE_TEAM_PRICE_ID`가 필요하다.
- Webhook은 카드 정보를 저장하지 않고 Stripe ID, amount, currency, status만 저장한다.
- 전체 검증 결과: `./gradlew.bat test` 통과, `./gradlew.bat build` 통과.
