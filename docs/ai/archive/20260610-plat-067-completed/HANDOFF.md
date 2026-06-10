# HANDOFF - PLAT-067: Billing 결제 이력/사용량 조회 API

## 한줄 요약

루트 `docs/BACKEND_GAP_platform.md` A-4를 처리한다. 기존 billing 결제 저장 데이터를 프론트가 조회할 수 있도록 결제 이력, 사용량/한도, 영수증/인보이스 read API를 추가한다.

## 현재 상태

- 구현 완료
- targeted test 통과
- `clean build` 통과
- PR 전 자체 리뷰 대기

## 작업 위치

- Repo: `synapse-platform-svc`
- Branch: `feature/PLAT-067-billing-read-apis`
- Base: `dev`
- 작업문서: `docs/ai/current`
- 이전 current archive: `docs/ai/archive/20260609-plat-066-completed`

## 구현 순서

1. 결제 이력 메타데이터 migration 추가
   - 후보 파일명: `V20260609170000__add_payment_invoice_metadata.sql`
   - `payment_history.stripe_invoice_id`
   - `payment_history.invoice_url`
   - `payment_history.invoice_pdf_url`
   - tenant + paid_at 조회 인덱스 추가

2. `PaymentHistory` entity 보강
   - invoice id/url/pdf url 필드 추가
   - `hasReceipt()` 또는 `isReceiptAvailable()` 계산 메서드 추가
   - `PaymentHistory.of(...)` 또는 별도 factory에 invoice metadata 반영

3. Webhook 저장 로직 보강
   - `BillingService.handleInvoicePaid`
   - Stripe `Invoice`에서 invoice id, hosted invoice URL, invoice PDF URL 추출
   - URL은 nullable 허용
   - 기존 amount/currency/status 저장 로직 유지

4. Repository query 추가
   - `findByTenantId(UUID tenantId, Pageable pageable)`
   - `findByIdAndTenantId(UUID id, UUID tenantId)`
   - 필요 시 `paidAt DESC, createdAt DESC` 정렬을 service에서 pageable sort로 고정

5. Plan quota 공개 계약 추가
   - billing이 auth 내부 repository를 직접 참조하지 않도록 `TenantApi` 확장 권장
   - 후보 record: `PlanQuotaInfo`
   - 필드: plan, displayName, maxNotes, maxCards, maxStorageBytes, maxAiTokensMonthly, maxAiCardGenerationsMonthly, maxUsersPerTenant

6. Billing read service 추가
   - 기존 `BillingService`에 read method를 추가하거나 `BillingReadService`로 분리
   - 분리 기준: Stripe write/webhook 로직과 read API 로직이 섞이면 `BillingReadService` 선호
   - tenant resolve는 기존 방식과 동일하게 `UserApi` 사용

7. Controller/DTO 추가
   - `GET /api/v1/billing/payments`
   - `GET /api/v1/billing/usage`
   - `GET /api/v1/billing/payments/{id}/receipt`
   - DTO 후보:
     - `PaymentHistoryResponse`
     - `PaymentHistoryPageResponse`
     - `BillingUsageResponse`
     - `BillingReceiptResponse`

8. 테스트 추가
   - Controller standalone test
   - Service unit test
   - Repository Testcontainers PG test
   - 필요 시 HTTP + security integration test
   - ModuleStructureTest 회귀

## API 응답 후보

결제 이력:

```json
{
  "items": [
    {
      "id": "uuid",
      "subscriptionId": "uuid",
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

사용량:

```json
{
  "tenantId": "uuid",
  "planCode": "pro",
  "subscriptionStatus": "ACTIVE",
  "quotas": {
    "maxNotes": 50000,
    "maxCards": 50000,
    "maxStorageBytes": 10000000000,
    "maxAiTokensMonthly": 5000000,
    "maxAiCardGenerationsMonthly": 500,
    "maxUsersPerTenant": 1
  },
  "usage": {
    "notes": { "used": null, "limit": 50000, "remaining": null, "source": "NOT_CONNECTED" }
  }
}
```

영수증/인보이스:

```json
{
  "paymentId": "uuid",
  "stripePaymentIntentId": "pi_test",
  "stripeInvoiceId": "in_test",
  "invoiceUrl": "https://invoice.stripe.com/i/acct_test/...",
  "invoicePdfUrl": "https://pay.stripe.com/invoice/...",
  "available": true
}
```

## 검증 명령

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

## 금지/주의

- `TASK_platform.md` 수정 금지
- env/profile 수정 금지
- gitops/shared 수정 금지
- billing에서 auth/user 내부 repository 직접 참조 금지
- read endpoint에서 Stripe API 실시간 호출 금지
- 기존 Webhook permitAll 설정 변경 금지
- PR 본문 작성 시 UTF-8 body file 사용
