# 작업 스코프: @platform-owner

## 담당자 정보

| 항목 | 내용 |
|------|------|
| Handle | @platform-owner |
| 역할 | 트랙 A (1명) |
| 담당 서비스 | synapse-platform-svc |
| 담당 모듈 | auth, audit, billing, notification |
| GitHub Repository | [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) |

## 5주 전체 책임 범위

### 도메인 경계

- **In Scope**:
  - OAuth 2.0 회원가입/로그인 (MVP: Google, GitHub, Apple / 확장: Microsoft)
  - JWT Access/Refresh Token 발급/검증/갱신
  - MFA (TOTP) 등록/검증
  - Stripe Checkout + Webhook + 플랜 관리
  - FCM 푸시 알림 + AWS SES 이메일 알림
  - Kafka 이벤트 소비 → audit_logs 적재
  - 테넌트/사용자 관리
  - **GDPR/CCPA 사용자 데이터 권리**: `POST /me/data-export` (데이터 내보내기 요청), `GET /me/data-export/{jobId}` (상태 조회), `DELETE /me/account` (계정 삭제, 30일 유예 — GDPR Article 17) *(Wiki API 명세서 동기화 — 추가)*
  - **Audit 로그 조회 API**: `GET /audit/logs` (현재 테넌트 감사 로그 조회, Admin/Owner 전용) — 현재 적재만 담당, 조회 API 추가 *(Wiki API 명세서 동기화 — 추가)*
  - **Tenant 상세 API**: `POST /tenant/switch` (테넌트 전환), `GET /tenant/usage` (사용량 현황 조회) — 현재 포괄적 언급만 있고 개별 엔드포인트 미명시 *(Wiki API 명세서 동기화 — 추가)*
  - **알림 읽음 처리**: `GET /notifications`, `PATCH /notifications/{id}/read`, `POST /notifications/read-all`, `GET /notifications/unread-count` — 발송만 담당하고 읽음 처리 API 누락 *(Wiki API 명세서 동기화 — 추가)*
  - **알림 설정**: `GET /notifications/preferences`, `PUT /notifications/preferences` — 알림 설정 조회/변경 *(Wiki API 명세서 동기화 — 추가)*
- **Out of Scope**:
  - 알림 트리거 로직 (engagement/learning 서비스가 이벤트 발행)
  - Flutter UI 전체 (인증 화면은 frontend 협업)
  - 다른 서비스 비즈니스 로직

### 주차별 스코프 매트릭스

| 주차 | 기간 | 핵심 목표 | 산출물 | 의존성 |
|------|------|-----------|--------|--------|
| W1 | 05-12~15 | platform-svc 골격 + auth(OAuth+JWT+MFA 기초) | 서비스 골격, 회원가입/로그인/JWT API | 인프라 Docker Compose (team-lead) |
| W2 | 05-18~22 | billing(Stripe) + notification(FCM 설정) | 결제 API, device_tokens 관리 | auth 완성 (W1) |
| W3 | 05-26~29 | audit(Kafka→logs) + notification 발송 + 테넌트 관리 + 알림 읽음 처리/설정 API + Tenant 상세 API | audit_logs, 푸시/이메일 발송, 관리 API, 알림 읽음/설정 API, tenant switch/usage | Kafka 토픽 (team-lead W2) |
| W4 | 06-01~05 | GDPR/CCPA 데이터 권리 API + Audit 로그 조회 API + 버그 수정 + 통합 테스트 | data-export API, account 삭제 API, audit/logs 조회, 안정화된 platform-svc | 전체 통합 (W3) |
| W5 | 06-08~12 | 인증/결제/알림 E2E + P0 버그 수정 + 알림 안정화 | E2E 테스트 결과, P0 수정 PR, 알림 안정화 리포트 | staging 환경 (team-lead W4) |

## 협업 인터페이스

| 상대 | 주고받는 것 | 방향 |
|------|------------|------|
| 전체 서비스 | JWT 검증 (Gateway 연동) | 제공 → |
| @engagement-owner | gamification.* Kafka 이벤트 → notification 소비 | ← 수신 |
| @learning-card-owner | card.review.due Kafka 이벤트 → notification 소비 | ← 수신 |
| @team-lead | Gateway 인증 필터 협의 | 양방향 |
| Frontend | 로그인/회원가입 API 제공 | 제공 → |

## 성공 기준

- [ ] OAuth 로그인 (Google/GitHub/Apple) 완전 동작
- [ ] JWT 발급/갱신/검증 정상
- [ ] Stripe 결제 플로우 (Checkout → Webhook → 플랜 활성화)
- [ ] 푸시 알림 발송 동작 (FCM)
- [ ] Audit 로그 Kafka 소비 → DB 적재
- [ ] GDPR/CCPA 데이터 내보내기 요청 및 상태 조회 동작 *(Wiki API 명세서 동기화 — 추가)*
- [ ] 계정 삭제 API (30일 유예 포함) 동작 *(Wiki API 명세서 동기화 — 추가)*
- [ ] Audit 로그 조회 API (`GET /audit/logs`) Admin/Owner 권한 검증 포함 *(Wiki API 명세서 동기화 — 추가)*
- [ ] Tenant switch/usage API 동작 *(Wiki API 명세서 동기화 — 추가)*
- [ ] 알림 읽음 처리 API 4종 동작 *(Wiki API 명세서 동기화 — 추가)*
- [ ] 알림 설정 조회/변경 API 동작 *(Wiki API 명세서 동기화 — 추가)*
