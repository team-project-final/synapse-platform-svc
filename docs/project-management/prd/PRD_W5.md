# PRD: Week 5 — E2E + 버그 + 발표 준비

## 1. 주차 개요

| 항목 | 내용 |
|------|------|
| 기간 | 2026-06-08 (월) ~ 2026-06-12 (금) (5 영업일) |
| 발표 | 2026-06-15 (월) — 최종 발표·시연·제출 (코드 동결) |
| 목표 | E2E 테스트, 버그 수정, P1 기능 마무리, Staging 배포, 발표 자료/리허설 |
| 전주 결과 | W4에서 이벤트 소비자 (notification/audit/admin) 통합 검증 완성 |
| GitHub Repositories | [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) · [synapse-engagement-svc](https://github.com/team-project-final/synapse-engagement-svc) · [synapse-knowledge-svc](https://github.com/team-project-final/synapse-knowledge-svc) · [synapse-learning-svc](https://github.com/team-project-final/synapse-learning-svc) · [synapse-frontend](https://github.com/team-project-final/synapse-frontend) · [synapse-shared](https://github.com/team-project-final/synapse-shared) |

## 2. 기능 요구사항

### 2.1 전체 팀 공통 — 버그 수정 + 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-ALL-301 | 전체 E2E 시나리오가 통과한다 | 핵심 유저 플로우 10개 E2E 테스트 PASS | P0 |
| FR-ALL-302 | P0 버그가 0건이다 | Sentry/로그 기반 버그 트리아지 → P0 전수 해결 | P0 |
| FR-ALL-303 | 테스트 커버리지가 80% 이상이다 | jacoco(Java) + coverage(Python) + flutter_test 종합 80%+ | P0 |
| FR-ALL-304 | Staging 환경에 배포가 완료된다 | ArgoCD staging 수동 승인 → 배포 성공 + Health 확인 | P0 |

### 2.2 @team-lead — 최종 점검

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-TL-301 | 전체 서비스 성능이 SLA를 만족한다 | P95 API < 200ms, Kafka 지연 < 5s, 검색 < 2s | P0 |
| FR-TL-302 | Schema Registry 전 토픽이 BACKWARD 호환된다 | 호환성 검증 전수 통과 | P0 |
| FR-TL-303 | 모니터링 대시보드가 가동된다 | Grafana 대시보드 + Prometheus 메트릭 + Fluent Bit → CloudWatch 로그 | P1 |
| FR-TL-304 | API 문서가 최신 상태로 업데이트된다 | SpringDoc OpenAPI 자동 생성 + 수동 보완 | P0 |
| FR-TL-305 | 팀이 최종 발표 자료를 준비하고 시연 리허설을 1회 이상 수행한다 | 슬라이드 + 데모 스크립트 + 시연 환경 검증 + 리허설 회고 | P0 |

### 2.3 @platform-owner — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-PL-301 | 인증 플로우 전체 E2E가 통과한다 | 회원가입→로그인→JWT갱신→MFA→로그아웃 전체 시나리오 | P0 |
| FR-PL-302 | 결제 플로우 E2E가 통과한다 | Stripe Test Mode 결제→Webhook→플랜활성화 | P0 |

### 2.4 @engagement-owner — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-EG-301 | 게이미피케이션 전체 플로우가 E2E로 동작한다 | 복습→XP→배지→레벨업→리더보드→알림 | P0 |
| FR-EG-302 | 커뮤니티 공유/신고 플로우가 E2E로 동작한다 | 공유→검색→복사 + 신고→관리자처리 | P0 |

### 2.5 @knowledge-owner-1 — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-KN-301 | 노트 CRUD + 위키링크 + 그래프 전체 E2E가 통과한다 | 노트생성→위키링크파싱→그래프표시→검색 | P0 |

### 2.6 @knowledge-owner-2 — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-K2-301 | 하이브리드 검색 E2E가 통과한다 | 키워드+시맨틱 결합 검색 → 관련 노트 반환 | P0 |
| FR-K2-302 | 검색 정확도 리포트가 산출된다 | 테스트 쿼리 20개 → 정확도 70%+ 확인 | P1 |

### 2.7 @learning-card-owner — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-LC-301 | 복습 세션 전체 E2E가 통과한다 | 카드선택→복습→SM-2→통계→XP 전체 시나리오 | P0 |

### 2.8 @learning-ai-owner — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-LA-301 | AI 카드 자동 생성 E2E가 통과한다 | 노트생성→이벤트→AI카드생성→덱에저장 | P0 |
| FR-LA-302 | 시맨틱 검색 정확도가 검증된다 | 테스트 쿼리 → 관련 노트 상위 5개 관련도 80%+ | P1 |

### 2.9 Frontend — 안정화

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-FE-301 | 전체 화면 반응형이 동작한다 | Mobile/Tablet/Desktop 3개 브레이크포인트 검증 | P0 |
| FR-FE-302 | 에러/로딩 상태가 일관되게 표시된다 | 모든 화면에서 AppErrorWidget/AppLoadingWidget 사용 | P0 |
| FR-FE-303 | DESIGN.md 토큰이 일관되게 적용된다 | 색상/타이포/스페이싱 하드코딩 0건 | P1 |
| FR-FE-304 | 발표용 데모 시나리오와 시드 데이터가 정돈된다 | 시연 흐름 정상 + 시드 데이터 일관성 + 깨진 링크 0건 | P0 |

## 3. 비기능 요구사항

| ID | 항목 | 기준 |
|----|------|------|
| NFR-301 | API 응답 시간 | 전체 P95 < 200ms |
| NFR-302 | 테스트 커버리지 | 전체 80%+, 신규 코드 85%+ |
| NFR-303 | E2E 시나리오 | 핵심 10개 시나리오 100% PASS |
| NFR-304 | 보안 | OWASP Top 10 취약점 0건 |
| NFR-305 | 배포 | Staging 환경 배포 + 24시간 안정 운영 |

## 4. 의존성 맵

| From | To | 내용 | 시점 |
|------|-----|------|------|
| 전체 서비스 | @team-lead | E2E 테스트 시나리오 제공 | W4 Day 1 |
| @team-lead | 전체 | Staging 환경 배포 | W4 Day 3~ |

## 5. 성공 기준 체크리스트

- [ ] 전체 E2E 시나리오 통과
- [ ] 테스트 커버리지 80% 이상
- [ ] Staging 환경 배포 완료
- [ ] P0 기능 100% 동작
- [ ] Schema Registry BACKWARD 호환성 모든 토픽 통과
- [ ] ArgoCD ApplicationSet으로 staging 환경 배포 완료
- [ ] 발표 자료 완성 + 시연 리허설 1회 이상 수행 (6/15 발표 D-3 이전)

## 6. 리스크 & 대안

| 리스크 | 영향 | 확률 | 대안 |
|--------|------|------|------|
| E2E 테스트 환경 불안정 | 테스트 결과 신뢰성 저하 | 중 | Docker Compose 로컬 E2E + Staging 분리 |
| P0 버그 다수 발견 | 일정 지연 | 중 | 우선순위 트리아지 + P1/P2는 Phase 2로 이월 |
| Staging 배포 실패 | 시연 불가 | 낮 | dev 환경으로 시연 + Staging은 추가 디버깅 |
| 커버리지 미달 | 품질 기준 미충족 | 낮 | 핵심 비즈니스 로직 테스트 집중 + 80% 미달 시 사유 문서화 |
