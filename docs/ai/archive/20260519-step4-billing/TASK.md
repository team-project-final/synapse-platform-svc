# TASK — Step 4: Stripe Checkout 결제 및 Webhook 플랜 활성화

> 출처: TASK_platform.md Step 4

## 상태

- Phase: 분석
- 담당 Agent: Director
- 시작일: 2026-05-19
- 목표 완료일: 2026-05-21 (Duration 2.5일)

---

## Step Goal

사용자가 Stripe Checkout으로 유료 플랜을 결제하고, Webhook으로 플랜이 활성화된다.

## Done When

- [ ] Stripe Checkout 세션 생성 API 동작
- [ ] 사용자가 Checkout 페이지에서 결제 완료
- [ ] Webhook(checkout.session.completed) 수신 시 플랜 활성화
- [ ] subscriptions 테이블에 구독 정보 저장
- [ ] payment_history 테이블에 결제 이력 저장
- [ ] 통합 테스트 통과

## Scope

- In Scope:
  - Stripe Checkout 세션 생성 API (`POST /billing/checkout`)
  - Webhook 핸들러 (`POST /billing/webhooks`)
    - checkout.session.completed
    - invoice.paid
    - customer.subscription.deleted
  - subscriptions 테이블 설계 + Flyway 마이그레이션 (V24)
  - payment_history 테이블 설계 + Flyway 마이그레이션 (V25)
  - 플랜 활성화/비활성화 로직
  - Webhook 서명 검증
  - 구독 상태 조회 API (`GET /billing/subscription`)
  - 통합 테스트
- Out of Scope:
  - 환불 처리
  - 플랜 업그레이드/다운그레이드
  - 청구서 PDF 생성

## Input

- Stripe API Key, Stripe Webhook Secret (환경변수)
- 플랜/가격 정보 (FREE, PRO, TEAM)
- 기존 Flyway 마이그레이션 현황: V1~V23 완료, 다음은 V24

## Instructions

1. subscriptions 테이블 DDL 작성 + Flyway V24 마이그레이션
2. payment_history 테이블 DDL 작성 + Flyway V25 마이그레이션
3. processed_events 테이블 DDL 작성 + Flyway V26 마이그레이션 (멱등성)
4. Stripe Checkout 세션 생성 API 구현 (`POST /api/v1/billing/checkout`)
5. Webhook 엔드포인트 구현 (`POST /api/v1/billing/webhooks`)
6. Webhook 서명 검증 + processed_events INSERT-FIRST 멱등성 처리
7. checkout.session.completed → 구독 활성화 / invoice.paid → 결제 이력 저장
8. 구독 상태 조회 API (`GET /api/v1/billing/subscription`)
9. 통합 테스트 8건 (Stripe Test Mode, Mockito)

## Output Format

billing 모듈 결제 코드 + Flyway 마이그레이션 (V24, V25, V26) + 테스트 코드 8건

## Constraints

- Stripe Test Mode 사용 (dev/staging)
- Webhook 서명 검증 필수 (replay attack 방지)
- 멱등성 보장 (동일 이벤트 중복 처리 방지)
- 테스트 커버리지 80% 이상
- 모듈 간 순환 의존 금지

## Duration

2.5일

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
