# WORKFLOW: @platform-owner — Week 4

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-06-01 ~ 2026-06-05, 4 영업일
> **PRD**: [PRD_W4.md](../prd/PRD_W4.md)

---

## Step 9: 인증/결제 E2E 테스트

> 완료: PLAT-023 (PR #57). E2E `AuthBillingE2ETest` — @SpringBootTest+MockMvc, Testcontainers PG. 발견 gap → 이슈 #56.

### 9.1 E2E 시나리오 정의
- [x] 인증 플로우 시나리오 작성 (회원가입→로그인→JWT→MFA→토큰갱신) *(로그아웃 전용 엔드포인트 없음 → 토큰 갱신/무효화로 대체)*
- [x] 결제 플로우 시나리오 작성 (Stripe Checkout→Webhook→구독 활성화)
- [x] 테스트 데이터 준비 (유저별 unique email, 테스트 간 TRUNCATE 격리)

### 9.2 E2E 테스트 실행
- [x] 인증 플로우 E2E 테스트 실행
- [x] 결제 플로우 E2E 테스트 실행
- [x] 실패 항목 기록 (billing `ON CONFLICT`가 H2에서 미검증 → 이슈 #56)

### 9.3 버그 트리아지
- [x] P0/P1/P2 분류 (flaky 테스트 2건=P0, billing H2 gap=P2/tech-debt)
- [x] P0 즉시 수정 대상 확정 (Audit Kafka flaky, JWT 변조 flaky)

### 9.4 버그 수정
- [x] P0 버그 수정 (PLAT-018 consumer earliest, PLAT-021 JWT 결정적 변조)
- [x] 수정 코드 리뷰 + 테스트 (PR #50, #55)

### 9.5 회귀 테스트
- [x] 수정 후 전체 테스트 재실행 (`./gradlew clean build` green)
- [x] 커버리지 80% 이상 확인 (JaCoCo 게이트 통과)

### 9.6 문서 업데이트
- [x] API 문서 최신화 (Step 9는 API 변경 없음 — 해당 없음)
- [x] HISTORY 완료 기록 (PR #58, TASK Step 9 Done)

**Step 9 Status**: [ ] Not Started / [ ] In Progress / [x] Done

---

## Step 10: 버그 수정 + 알림 안정화

> TASK_platform Step 10 정의에 맞춰 정렬(기존 E2E 템플릿 → P0 버그 + 알림 retry/metrics).

### 10.1 P0 버그 트리아지
- [ ] GitHub Issues P0/bug 필터 — P0 버그 목록 확인
- [ ] P0/P1/P2 분류 + P0 즉시 수정 대상 확정

### 10.2 P0 버그 수정
- [ ] 각 P0 재현 → 원인 분석 → 수정
- [ ] 수정 후 재현 테스트 작성 및 통과 (회귀 방지)

### 10.3 알림 발송 안정화 (재시도 로직)
- [ ] FCM 발송 실패 원인 분석 + 재시도 로직 보강
- [ ] SES 발송 실패 원인 분석 + 안정화

### 10.4 알림 모니터링 메트릭
- [ ] 발송 성공/실패/지연 메트릭 추가 (Micrometer)
- [ ] 성공률 > 99% / SLA(FCM < 10s, SES < 30s) 기준 반영·관찰

### 10.5 회귀 테스트
- [ ] 수정 후 전체 테스트 재실행 (`clean build`)
- [ ] 커버리지 80% 이상 확인

### 10.6 문서 업데이트
- [ ] 안정화 리포트 작성
- [ ] HISTORY 완료 기록

**Step 10 Status**: [ ] Not Started / [x] In Progress / [ ] Done
