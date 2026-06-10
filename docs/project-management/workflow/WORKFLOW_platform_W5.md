# WORKFLOW: @platform-owner — Week 5

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-06-08 ~ 2026-06-12, 5 영업일
> **목표**: 인증/결제/알림 E2E, P0 버그 수정, 알림 안정화

## 현재 상태 요약 (2026-06-10)

- 백엔드 준비 작업은 대부분 완료됐다.
- 프론트 연동 갭 보강은 `PLAT-063`, `PLAT-067`, `PLAT-068`, `PLAT-069`, `PLAT-070`, `PLAT-071`, `PLAT-072`로 `dev`에 merge 완료됐다.
- W5 중 발견된 platform 이슈는 #84, #86, #91 처리 완료 및 close 상태다.
- `dev` 변경사항은 PR #90으로 `main`에 반영 완료됐다.
- 남은 open 항목은 #62 라이브 E2E 실행과 #87 audit DLT 조사다.

## Step 9: 인증/결제 E2E

### 9.1 시나리오 준비
- [x] OAuth 회원가입/로그인 백엔드 경로 준비
  - OAuth provider 컬럼 정합 확인 완료 (#91)
  - 신규 사용자 기본 `ROLE_USER` 저장 및 DB 기반 role 발급 완료 (`PLAT-064`)
  - 관리자 계정은 자동 seed 없이 DB 수동 grant 방식 유지 (#86)
- [x] JWT 갱신, MFA 등록/검증 시나리오 백엔드 보강
  - auth 복구 플로우 및 MFA backup code 보강 완료 (`PLAT-070`)
  - access token role claim 정합 보강 완료 (#86)
- [x] Stripe Test Mode 결제 시나리오 백엔드 보강
  - 결제 조회 API, 사용량, 영수증 조회 API 보강 완료 (`PLAT-067`)
  - 기존 Auth/Billing E2E 및 관련 repository/service 테스트 통과 기록 있음
- [ ] 라이브 E2E용 실제 테스트 계정/Stripe 실행 데이터 최종 준비
  - #62에서 실제 실행 시 확인

### 9.2 실행
- [ ] 회원가입 → 로그인 → JWT 갱신 → MFA → 로그아웃 E2E 실행
  - #62에서 라이브 실행 필요
- [ ] Stripe Checkout → Webhook → 플랜 활성화 E2E 실행
  - #62에서 라이브 실행 필요
- [ ] 실패 케이스를 P0/P1/P2로 최종 분류
  - #84, #86, #91은 처리 완료
  - #87 audit DLT 조사는 별도 open

## Step 10: 알림 안정화

### 10.1 알림 플로우 검증
- [ ] `card.review.due` → notification → FCM/SES 경로 확인
  - #62에서 라이브 실행 필요
- [x] `gamification.*` → notification 경로 확인
  - W5 Day3 관찰 기준 notification 경로는 정상
  - #87은 notification 경로가 아니라 audit-leg DLT 조사 건
- [ ] `community.*` → notification 경로 확인
  - #62에서 라이브 실행 필요

### 10.2 P0 수정
- [x] 인증/결제/알림 관련 platform P0/P1 이슈 처리
  - #84 OpenAPI/SpringDoc 문서 노출 보강 완료
  - #86 ADMIN role/JWT 계약 정합 보강 완료
  - #91 OAuth provider 컬럼 정합 조사 완료
- [ ] 알림 발송 지연 SLA 확인
  - #62 라이브 실행 후 확정
- [x] 백엔드 회귀 테스트 실행
  - 각 PR별 단위/통합 테스트 및 `clean build` 통과
- [ ] 라이브 E2E 회귀 테스트 실행
  - #62에서 최종 확인

## Done When

- [ ] 인증 E2E가 통과한다.
- [ ] 결제 E2E가 통과한다.
- [ ] 알림 발송 성공률과 지연 SLA가 기준을 만족한다.
- [x] 현재 확인된 platform P0 버그가 0건이다.

## 남은 작업

1. #62 라이브 E2E 실행
   - 인증 플로우
   - 결제 플로우
   - 알림 플로우
2. #87 audit DLT 조사
   - 실 서비스 발행 `ReviewCompleted`로 재현 여부 확인
   - audit 대상 이벤트가 아니면 graceful skip 또는 구독 필터 정리
