# CONTEXT - PLAT-067: Billing 결제 이력/사용량 조회 API

## 배경

루트 `docs/BACKEND_GAP_platform.md` A-4는 프론트 결제 화면이 기대하는 API 중 platform-svc에 아직 없는 항목을 정리한다.

프론트가 기대하는 동작:

- 결제 이력 조회
- 현재 플랜 대비 사용량 조회
- 결제 건별 영수증 또는 인보이스 확인

현재 platform-svc가 제공하는 billing API:

- `POST /api/v1/billing/checkout`
- `GET /api/v1/billing/subscription`
- `POST /api/v1/billing/webhooks`

즉, 결제 생성과 Webhook 반영은 있으나 프론트 화면이 읽을 수 있는 read API가 부족하다.

## 현재 코드 상태

### Controller

`BillingController`는 `/api/v1/billing` 아래 3개 엔드포인트만 가진다.

- `POST /checkout`
- `POST /webhooks`
- `GET /subscription`

`/webhooks`만 SecurityConfig에서 permitAll이고, 나머지 billing read/write API는 인증이 필요하다.

### Service

`BillingService`는 다음 책임을 가진다.

- Stripe Checkout Session 생성
- Stripe Webhook 서명 검증
- `checkout.session.completed` 처리
- `invoice.paid` 처리
- `customer.subscription.deleted` 처리
- 현재 활성 구독 조회

현재 사용자 tenant는 `UserApi.findById(userId).defaultTenantId()`로 찾는다.

### Tables

`subscriptions`:

- `id`
- `tenant_id`
- `plan_code`
- `stripe_customer_id`
- `stripe_subscription_id`
- `status`
- `current_period_start`
- `current_period_end`
- `canceled_at`
- `created_at`
- `updated_at`

`payment_history`:

- `id`
- `tenant_id`
- `subscription_id`
- `stripe_payment_intent_id`
- `amount`
- `currency`
- `status`
- `paid_at`
- `created_at`

`plan_quotas`:

- `plan`
- `display_name`
- `price_usd_monthly`
- `price_usd_yearly`
- `max_notes`
- `max_cards`
- `max_storage_bytes`
- `max_ai_tokens_monthly`
- `max_ai_card_generations_monthly`
- `max_users_per_tenant`
- `features`
- `is_active`
- `created_at`

## 주요 갭

### 결제 이력

`payment_history` 테이블과 entity는 존재하지만 repository에 tenant scoped 조회 메서드가 없다.
프론트에 노출하려면 로그인 사용자의 기본 tenant 기준으로만 조회해야 한다.

### 영수증/인보이스

현재 `invoice.paid` 이벤트에서 amount/currency/paymentIntent만 저장한다.
Stripe invoice URL 또는 PDF URL을 저장하지 않으므로 영수증 API를 만들려면 nullable 컬럼 추가가 필요하다.

권장 저장값:

- Stripe invoice id
- hosted invoice URL
- invoice PDF URL

기존 row는 URL이 없을 수 있으므로 receipt endpoint는 결제 건 자체가 존재하면 200을 반환하고, URL 부재는 `available=false`로 표현한다.

### 사용량

플랜별 한도는 `plan_quotas`에 정본이 있다.
하지만 note/card/storage/AI 사용량은 각 도메인 서비스 소관이라 platform-svc 단독으로 실제 사용량을 계산할 수 없다.

이번 작업에서는 다음까지만 처리한다.

- 현재 plan 식별
- `plan_quotas` 기준 한도 반환
- 실제 사용량 값은 `null`
- source는 `NOT_CONNECTED`

이렇게 하면 프론트는 플랜 한도 UI를 붙일 수 있고, 실제 사용량 집계는 후속 cross-service 연동에서 교체할 수 있다.

## 모듈 경계

billing 모듈은 auth/user 내부 repository와 entity에 직접 접근하지 않는다.

허용 경로:

- user 식별: `UserApi`
- tenant plan/status 조회: `TenantApi`
- plan quota 조회: `TenantApi` named interface 확장 또는 별도 공개 API

금지:

- billing에서 `TenantRepository` 직접 주입
- billing에서 auth entity 직접 조회
- profile/env/gitops 변경으로 문제 우회

## API 설계 기준

### `/api/v1/billing/payments`

- tenant scoped
- pageable
- newest first
- 결제 상세에 receipt 가능 여부 포함

### `/api/v1/billing/usage`

- tenant scoped
- subscription이 있으면 subscription plan 우선
- subscription이 없으면 tenant plan fallback
- quota는 `plan_quotas`
- 실제 usage는 `NOT_CONNECTED`

### `/api/v1/billing/payments/{id}/receipt`

- tenant scoped
- 타 tenant payment는 404
- Stripe API 실시간 호출 없음
- 저장된 invoice metadata만 반환

## 테스트 포인트

- 결제 이력은 본인 기본 tenant row만 반환한다.
- 결제 이력은 타 tenant row를 반환하지 않는다.
- 결제 이력은 최신순으로 정렬된다.
- receipt endpoint는 본인 tenant payment만 반환한다.
- 타 tenant payment id 접근은 404다.
- invoice URL이 없는 기존 row는 `available=false`다.
- `invoice.paid` Webhook 처리 시 invoice metadata가 저장된다.
- usage endpoint는 plan quota를 반환한다.
- 실제 usage source는 `NOT_CONNECTED`다.
- auth/user repository 직접 접근 없이 modulith 구조 테스트가 통과한다.

## 주의 사항

- `TASK_platform.md`는 최초 개발 목록이므로 수정하지 않는다.
- env/profile은 수정하지 않는다.
- gitops/shared는 팀장님 관리 영역이므로 수정하지 않는다.
- Stripe 테스트는 mock/fixture 중심으로 처리한다.
- 실제 Stripe API 네트워크 호출을 read endpoint에 넣지 않는다.
