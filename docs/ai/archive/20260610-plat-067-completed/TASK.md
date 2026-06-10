# TASK - PLAT-067: Billing 결제 이력/사용량 조회 API

> 출처: 루트 `docs/BACKEND_GAP_platform.md` A-4. 프론트 `billing/billing_screens.dart`가 결제 이력, 사용량, 영수증/인보이스 조회를 기대하지만 platform-svc는 현재 Checkout 생성, Webhook 처리, 현재 구독 조회만 제공한다.

## Task Metadata

| 필드 | 내용 |
|---|---|
| Task ID | `PLAT-067` |
| Title | Billing 결제 이력/사용량 조회 API |
| Owner | platform (김해준) |
| Status | `DONE` |
| Priority | `P1` |
| Step Goal | 프론트 결제 화면이 로그인 사용자의 결제 이력, 플랜 한도, 영수증/인보이스 정보를 platform API로 조회한다. |
| Done When | 아래 `Done When` 체크리스트 기준 |
| Scope | 아래 `Scope` 기준 |
| Dependencies | `BACKEND_GAP_platform.md` A-4, `payment_history`, `subscriptions`, `plan_quotas`, `UserApi`, `TenantApi` |
| Due Date | 2026-06-12 |

## Step Goal

프론트 결제 화면이 로그인 사용자의 결제 이력, 플랜 한도, 영수증/인보이스 정보를 platform API로 조회한다.

## Done When

- [ ] `GET /api/v1/billing/payments`가 로그인 사용자의 기본 tenant 결제 이력을 최신순 페이지로 반환한다.
- [ ] `GET /api/v1/billing/usage`가 현재 구독/플랜과 `plan_quotas` 기준 한도를 반환한다.
- [ ] `GET /api/v1/billing/payments/{id}/receipt`가 본인 tenant 결제 건의 Stripe invoice URL/PDF URL 메타데이터를 반환한다.
- [ ] 다른 tenant 결제 이력 또는 영수증 접근은 존재 여부를 숨기도록 404로 처리한다.
- [ ] Stripe Webhook `invoice.paid` 처리 시 invoice 식별자와 invoice URL/PDF URL을 저장한다.
- [ ] 기존 Checkout, Webhook, Subscription API의 동작을 변경하지 않는다.
- [ ] `TASK_platform.md`, env/profile, gitops/shared 프로젝트는 수정하지 않는다.
- [ ] billing 단위 테스트, controller 테스트, repository 또는 통합 테스트가 통과한다.
- [ ] `clean build`가 통과한다.

## Scope

### In Scope

- 결제 이력 조회 API 추가
- 결제 이력 응답 DTO 추가
- 영수증/인보이스 조회 API 추가
- `payment_history`에 Stripe invoice 메타데이터를 저장하는 nullable 컬럼 추가
- `invoice.paid` Webhook 처리 시 invoice id, hosted invoice URL, invoice PDF URL 저장
- 사용량 조회 API 추가
- `plan_quotas` 한도 조회를 위한 공개 API 계약 정리
- 본인 tenant 기준 접근 제어
- billing read API 단위/통합 테스트 추가

### Out of Scope

- Stripe Checkout 생성 플로우 변경
- Stripe Webhook 서명 검증 방식 변경
- 실제 카드 정보 저장 또는 결제 수단 관리
- 타 서비스 note/card/storage/AI 사용량 집계 구현
- 프론트 화면 수정
- `TASK_platform.md` 수정
- env/profile/gitops/shared 수정

## API Contract

### 결제 이력

`GET /api/v1/billing/payments?page=0&size=20`

- 인증 필요
- 로그인 사용자의 `defaultTenantId` 기준 결제 이력만 반환
- 정렬: `paidAt DESC NULLS LAST`, `createdAt DESC`
- 금액 단위는 Stripe 저장값 그대로 minor unit을 사용한다. 예: USD 999 = $9.99

응답 후보:

```json
{
  "items": [
    {
      "id": "3f8a8a87-9c1d-4f1d-89d5-493f2d19d901",
      "subscriptionId": "0cc0c6d7-5fc8-4943-8322-3d2f337713db",
      "amount": 999,
      "currency": "usd",
      "status": "succeeded",
      "paidAt": "2026-06-09T15:30:00+09:00",
      "createdAt": "2026-06-09T15:30:01+09:00",
      "receiptAvailable": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 사용량

`GET /api/v1/billing/usage`

- 인증 필요
- 로그인 사용자의 기본 tenant 기준
- 현재 활성 구독이 있으면 구독의 plan을 우선 사용한다.
- 활성 구독이 없으면 tenant의 현재 plan을 사용한다.
- 한도는 `plan_quotas` 정본을 사용한다.
- note/card/storage/AI 실제 사용량은 현재 platform-svc 정본이 없으므로 이번 작업에서는 `used = null`, `remaining = null`, `source = "NOT_CONNECTED"`로 명시한다.

응답 후보:

```json
{
  "tenantId": "75c0cf72-dc31-4d58-bf6b-77e0b45e9dd5",
  "planCode": "pro",
  "subscriptionStatus": "ACTIVE",
  "currentPeriodStart": "2026-06-09T15:00:00+09:00",
  "currentPeriodEnd": "2026-07-09T15:00:00+09:00",
  "quotas": {
    "maxNotes": 50000,
    "maxCards": 50000,
    "maxStorageBytes": 10000000000,
    "maxAiTokensMonthly": 5000000,
    "maxAiCardGenerationsMonthly": 500,
    "maxUsersPerTenant": 1
  },
  "usage": {
    "notes": { "used": null, "limit": 50000, "remaining": null, "source": "NOT_CONNECTED" },
    "cards": { "used": null, "limit": 50000, "remaining": null, "source": "NOT_CONNECTED" },
    "storageBytes": { "used": null, "limit": 10000000000, "remaining": null, "source": "NOT_CONNECTED" },
    "aiTokensMonthly": { "used": null, "limit": 5000000, "remaining": null, "source": "NOT_CONNECTED" },
    "aiCardGenerationsMonthly": { "used": null, "limit": 500, "remaining": null, "source": "NOT_CONNECTED" },
    "users": { "used": null, "limit": 1, "remaining": null, "source": "NOT_CONNECTED" }
  }
}
```

### 영수증/인보이스

`GET /api/v1/billing/payments/{id}/receipt`

- 인증 필요
- 로그인 사용자 기본 tenant의 결제 건만 반환
- 타 tenant 결제 건은 404
- URL은 Webhook 수신 시 저장된 Stripe invoice 메타데이터를 사용한다.
- 기존 결제 row처럼 URL이 없는 경우 200 응답에 `available=false`를 반환한다.

응답 후보:

```json
{
  "paymentId": "3f8a8a87-9c1d-4f1d-89d5-493f2d19d901",
  "stripePaymentIntentId": "pi_test",
  "stripeInvoiceId": "in_test",
  "invoiceUrl": "https://invoice.stripe.com/i/acct_test/...",
  "invoicePdfUrl": "https://pay.stripe.com/invoice/...",
  "available": true
}
```

## Data Model

신규 Flyway migration 후보:

- `V20260609170000__add_payment_invoice_metadata.sql`

추가 컬럼:

- `payment_history.stripe_invoice_id VARCHAR(255)`
- `payment_history.invoice_url TEXT`
- `payment_history.invoice_pdf_url TEXT`

추가 인덱스:

- `uq_payment_history_stripe_invoice_id` partial unique index where `stripe_invoice_id IS NOT NULL`
- `idx_payment_history_tenant_paid_at` on `(tenant_id, paid_at DESC, created_at DESC)`

## Design Notes

- 사용자 식별은 기존 billing 방식과 동일하게 `Authentication.getName()` UUID를 사용한다.
- tenant 식별은 기존 `BillingService.resolveTenantId(userId)` 경로와 동일하게 `UserApi.findById(userId).defaultTenantId()`를 사용한다.
- billing 모듈은 auth/user 내부 repository를 직접 참조하지 않는다.
- `plan_quotas` 조회가 필요하면 `TenantApi` named interface에 `PlanQuotaInfo` 조회 계약을 추가한다.
- `GET /api/v1/billing/payments/{id}/receipt`는 Stripe API를 실시간 호출하지 않는다. Webhook에서 저장한 메타데이터만 반환한다.
- 실제 사용량 집계는 knowledge/learning/AI 서비스와의 정본 협의가 필요하므로 이번 작업에서는 연결 상태를 응답에 명시한다.

## Implementation Checklist

- [x] 기존 PLAT-066 current 문서 archive 완료
- [x] PLAT-067 작업 브랜치 생성
- [x] PLAT-067 작업문서 작성
- [x] `payment_history` invoice 메타데이터 migration 추가
- [x] `PaymentHistory` entity invoice 메타데이터 필드 추가
- [x] `PaymentHistoryRepository` tenant scoped query 추가
- [x] `TenantApi` plan quota 조회 계약 추가
- [x] plan quota 응답 record/API 구현
- [x] billing payment/usage/receipt DTO 추가
- [x] `BillingService` read method 추가
- [x] `BillingController` read endpoint 추가
- [x] Webhook `invoice.paid` 저장 로직 invoice 메타데이터 반영
- [x] controller/service/repository/security 테스트 추가
- [x] modulith 구조 테스트 통과
- [x] targeted test 통과
- [x] `clean build` 통과

## Verification Plan

```powershell
.\gradlew.bat test --tests "*BillingControllerTest"
.\gradlew.bat test --tests "*BillingServiceTest"
.\gradlew.bat test --tests "*BillingRepositoryTest"
.\gradlew.bat test --tests "*BillingSecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

검증 결과(2026-06-10):

- `.\gradlew.bat test --tests "*BillingControllerTest" --tests "*BillingServiceTest" --tests "*BillingRepositoryTest"`: PASS
- `.\gradlew.bat test --tests "*BillingControllerTest" --tests "*BillingServiceTest" --tests "*BillingRepositoryTest" --tests "*BillingSecurityIntegrationTest" --tests "*PlatformModuleStructureTest"`: PASS
- `.\gradlew.bat clean build`: PASS

> Windows Embedded Kafka 종료 중 임시 디렉터리 삭제 실패 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.
