# WORKFLOW: @platform-owner — Week 5

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-06-08 ~ 2026-06-12, 5 영업일
> **목표**: 인증/결제/알림 E2E, P0 버그 수정, 알림 안정화

## Step 9: 인증/결제 E2E

### 9.1 시나리오 준비
- [ ] OAuth 회원가입/로그인 테스트 계정 준비
- [ ] JWT 갱신, 로그아웃, MFA 등록/검증 시나리오 작성
- [ ] Stripe Test Mode 결제 시나리오 작성

### 9.2 실행
- [ ] 회원가입 → 로그인 → JWT 갱신 → MFA → 로그아웃 E2E 실행
- [ ] Stripe Checkout → Webhook → 플랜 활성화 E2E 실행
- [ ] 실패 케이스를 P0/P1/P2로 분류

## Step 10: 알림 안정화

### 10.1 알림 플로우 검증
- [ ] `card.review.due` → notification → FCM/SES 경로 확인
- [ ] `gamification.*` → notification 경로 확인
- [ ] `community.*` → notification 경로 확인

### 10.2 P0 수정
- [ ] 인증/결제/알림 P0 버그 수정
- [ ] 알림 발송 지연 SLA 확인
- [ ] 회귀 테스트 실행

## Done When

- [ ] 인증 E2E가 통과한다.
- [ ] 결제 E2E가 통과한다.
- [ ] 알림 발송 성공률과 지연 SLA가 기준을 만족한다.
- [ ] platform P0 버그가 0건이다.
