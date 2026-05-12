# PRD: Week 4 — 이벤트 소비자 + 운영 기능

## 1. 주차 개요

| 항목 | 내용 |
|------|------|
| 기간 | 2026-06-01 (월) ~ 2026-06-05 (금) (4 영업일, 6/3 제9회 전국동시지방선거 제외) |
| 목표 | notification 발송 (FCM/SES) / audit Kafka 소비 / 관리자·Admin 모더레이션 / 통합 검증 |
| 전주 결과 | W3에서 모든 producer 토픽 발행 + gamification 완성 + 검색 RRF + AI 자동 생성 안정화 |
| GitHub Repositories | [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) · [synapse-engagement-svc](https://github.com/team-project-final/synapse-engagement-svc) · [synapse-knowledge-svc](https://github.com/team-project-final/synapse-knowledge-svc) · [synapse-learning-svc](https://github.com/team-project-final/synapse-learning-svc) · [synapse-frontend](https://github.com/team-project-final/synapse-frontend) · [synapse-shared](https://github.com/team-project-final/synapse-shared) |

## 2. 기능 요구사항

### 2.1 @team-lead — 통합 테스트 + 배포 검증

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-TL-401 | 전체 서비스 간 Kafka 이벤트 흐름이 E2E로 동작한다 | 복습→XP→레벨업→알림 전체 체인 동작 + 코드 리뷰 완료 | P0 |
| FR-TL-402 | ArgoCD dev/staging 환경 배포가 검증된다 | dev autoSync 동작 + staging 수동 승인 배포 성공 | P0 |

### 2.2 @platform-owner — Notification 소비 + Audit + Admin

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-PL-401 | 사용자에게 FCM 푸시 알림이 발송된다 | gamification.level_up / community.shared / card.review.due Kafka 소비 → 푸시 발송 | P0 |
| FR-PL-402 | 사용자에게 이메일 알림이 발송된다 | AWS SES → 주간 학습 리포트 이메일 | P1 |
| FR-PL-403 | 사용자가 조용한 시간을 설정할 수 있다 | PUT /api/v1/notifications/quiet-hours → 해당 시간 알림 미발송 | P1 |
| FR-PL-404 | 시스템이 주요 이벤트를 audit_logs에 자동 기록한다 | Kafka 이벤트 소비 → audit_logs 적재 + 90일 보존 정책 | P0 |
| FR-PL-405 | 관리자가 테넌트/사용자를 관리할 수 있다 | 사용자 목록/검색/정지/삭제 관리 API | P0 |

### 2.3 @engagement-owner — 신고 처리 + Admin 모더레이션

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-EG-401 | 사용자가 부적절한 콘텐츠를 신고할 수 있다 | POST /api/v1/reports → 신고 접수 + 관리자 알림 | P1 |
| FR-EG-402 | 관리자가 신고를 처리할 수 있다 | GET/PUT /api/v1/admin/reports → 신고 목록 + 승인/거부/숨김 | P1 |

### 2.4 @knowledge-owner-1 — 통합 검증 잔무

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-KN-401 | W3 잔무 (note 버전·태그) 안정화 | 통합 검증 + P0 버그 수정 | P0 |

### 2.5 @knowledge-owner-2 — 검색 튜닝 + E2E

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-K2-401 | 하이브리드 검색 E2E가 통과한다 | BM25 + 시맨틱 RRF 결합 결과 → 관련 노트 반환 | P0 |
| FR-K2-402 | 검색 정확도 튜닝 + P0 버그 수정 | 테스트 쿼리 정확도 70%+ 확인 | P0 |

### 2.6 @learning-card-owner — 복습 E2E

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-LC-401 | 복습 전체 E2E가 통과한다 | 카드 → 복습 → SM-2 → 통계 → XP 전체 시나리오 | P0 |

### 2.7 @learning-ai-owner — RAG + AI 자동 생성 E2E

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-LA-401 | AI 카드 자동 생성 E2E가 통과한다 | 노트 생성 → 이벤트 → AI 카드 생성 → 덱 저장 | P0 |
| FR-LA-402 | 사용자가 노트 기반으로 AI에게 질문할 수 있다 (RAG Q&A) | POST /api/v1/ai/ask → 관련 청크 검색 → LLM 답변 생성 | P2 (시간 허용 시) |
| FR-LA-403 | 시맨틱 검색 정확도가 검증된다 | 테스트 쿼리 → 관련 노트 상위 5개 관련도 80%+ | P1 |

### 2.8 Frontend (전체 협업)

| ID | 유저 스토리 | 수용 기준 | 우선순위 |
|----|------------|-----------|----------|
| FR-FE-401 | 사용자가 알림 센터에서 알림을 확인할 수 있다 | 알림 목록 + 읽음/안읽음 + 알림 설정 | P0 |
| FR-FE-402 | 관리자가 관리 화면에서 신고를 처리할 수 있다 | 신고 목록 + 처리(승인/거부) | P1 |
| FR-FE-403 | 사용자가 공유 덱을 탐색하고 복사할 수 있다 | 공유 덱 목록 + 상세 + 복사 버튼 | P0 |

## 3. 비기능 요구사항

| ID | 항목 | 기준 |
|----|------|------|
| NFR-401 | E2E 이벤트 체인 | 복습 → XP → 레벨업 → 알림 전체 < 10초 |
| NFR-402 | 알림 발송 | FCM 발송 성공률 > 95% |
| NFR-403 | Audit 로그 | 적재 지연 < 30초, 90일 보존 |
| NFR-404 | 검색 정확도 | 하이브리드 검색 정확도 70%+ |
| NFR-405 | AI 자동 생성 E2E | 노트 → 카드 생성 시간 < 30초, 정확도 검증 |

## 4. 의존성 맵

| From | To | 내용 | 시점 |
|------|-----|------|------|
| W3 producer 토픽 | @platform-owner | gamification.* / card.review.due → notification 소비 | W4 Day 1 (W3 종료 직후) |
| W3 producer 토픽 | @platform-owner | 모든 도메인 이벤트 → audit 소비 | W4 Day 1 |
| @engagement-owner | @platform-owner | 신고 알림 → admin notification | W4 Day 2~ |
| 전체 서비스 | @team-lead | E2E 시나리오 → 통합 조율 | W4 Day 3~ |

## 5. 성공 기준 체크리스트

- [ ] notification Kafka 소비 → FCM 푸시 + SES 이메일 발송 동작
- [ ] audit Kafka 소비 → audit_logs 적재 동작 (90일 보존)
- [ ] 관리자 신고 처리 + 모더레이션 API 동작
- [ ] 검색 튜닝 + 하이브리드 검색 E2E 통과
- [ ] AI 카드 자동 생성 E2E 통과
- [ ] ArgoCD dev/staging 환경 자동 배포 검증

## 6. 리스크 & 대안

| 리스크 | 영향 | 확률 | 대안 |
|--------|------|------|------|
| Kafka 이벤트 체인 디버깅 어려움 | 통합 지연 | 중 | DLQ + 이벤트 추적 로깅 + W3 종료 게이트 통과 후 시작 |
| FCM 인증서 설정 오류 | 푸시 알림 불가 | 중 | 이메일 알림을 1순위 폴백으로 |
| 6/3 지방선거 1일 손실 | W4 영업일 4일로 압축 | 확정 | 설계 단계에서 이미 반영, P2(RAG)는 시간 허용 시 |
| W3 producer 잔여 발생 시 | W4 소비 시작 지연 | 중 | 6/1(월) 첫날 producer 보완 가능, W4 소비는 6/2부터 본격 시작 |
