# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- **Stripe SDK**: `32.1.0` (Spring Boot 4 / Jakarta EE 11 호환 샘플링 검증 완료, 32.2.0-beta.2는 베타 제외)
- **Flyway**: V24(subscriptions), V25(payment_history), V26(processed_events) — 3개
- **브랜치**: `feature/PLAT-004-stripe-billing`
- **API 경로**: `/api/v1/billing/checkout`, `/api/v1/billing/webhooks`, `/api/v1/billing/subscription`
- **멱등성 전략**: `processed_events` 테이블 + `event.id` ON CONFLICT DO NOTHING (D-025)
- **StripeClient 초기화**: `StripeClient.builder()` Spring Bean (정적 setter 금지)
- **Webhook 수신**: `@RequestBody byte[]` + UTF-8 변환
- **tenantId 조회**: `UserApi.findById(userId).defaultTenantId()` (D-020)
- **tenant.plan 업데이트**: `TenantApi` @NamedInterface (D-022)
- **status DDL**: UPPERCASE (`'ACTIVE'`) — Java enum과 일치 (D-024)
- **stripe_customer_id**: nullable (Webhook 수신 시 채움)
- **StripeProperties 구조**: `plans().pro().priceId()`, `plans().team().priceId()`

## 현재 미결 사항

- (없음)

## 활성 제약

- 모듈 간 순환 의존 금지 (`PlatformModuleStructureTest` CI 검증)
- 테스트 커버리지: 신규 코드 80% 이상
- Stripe API Key: 환경변수 관리, 하드코딩 금지
- Webhook 서명 검증: `Webhook.constructEvent()` 필수
- PCI DSS: 카드 정보 직접 저장 금지

## 참고할 공식 문서

- docs/ai/current/HANDOFF.md (Worker 구현 명세 최종본)
- docs/spike/billing/HANDOFF_MAIN.md (샘플링 결과)
- docs/ai/decisions/DECISION_LOG.md D-020~D-025
