# 작업 스코프: @platform-owner

## 담당자 정보

| 항목 | 내용 |
|------|------|
| Handle | @platform-owner |
| 역할 | 트랙 A (1명) |
| 담당 서비스 | synapse-platform-svc |
| 담당 모듈 | auth, audit, billing, notification |
| GitHub Repository | [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) |

## 4주 전체 책임 범위

### 도메인 경계

- **In Scope**:
  - OAuth 2.0 회원가입/로그인 (Google, GitHub, Apple, Microsoft)
  - JWT Access/Refresh Token 발급/검증/갱신
  - MFA (TOTP) 등록/검증
  - Stripe Checkout + Webhook + 플랜 관리
  - FCM 푸시 알림 + AWS SES 이메일 알림
  - Kafka 이벤트 소비 → audit_logs 적재
  - 테넌트/사용자 관리
- **Out of Scope**:
  - 알림 트리거 로직 (engagement/learning 서비스가 이벤트 발행)
  - Flutter UI 전체 (인증 화면은 frontend 협업)
  - 다른 서비스 비즈니스 로직

### 주차별 스코프 매트릭스

| 주차 | 기간 | 핵심 목표 | 산출물 | 의존성 |
|------|------|-----------|--------|--------|
| W1 | 05-12~16 | platform-svc 골격 + auth(OAuth+JWT+MFA 기초) | 서비스 골격, 회원가입/로그인/JWT API | 인프라 Docker Compose (team-lead) |
| W2 | 05-19~23 | billing(Stripe) + notification(FCM 설정) | 결제 API, device_tokens 관리 | auth 완성 (W1) |
| W3 | 05-26~30 | audit(Kafka→logs) + notification 발송 + 테넌트 관리 | audit_logs, 푸시/이메일 발송, 관리 API | Kafka 토픽 (team-lead W2) |
| W4 | 06-02~06 | 버그 수정 + 통합 테스트 | 안정화된 platform-svc | 전체 통합 (W3) |

## 협업 인터페이스

| 상대 | 주고받는 것 | 방향 |
|------|------------|------|
| 전체 서비스 | JWT 검증 (Gateway 연동) | 제공 → |
| @engagement-owner | gamification.* Kafka 이벤트 → notification 소비 | ← 수신 |
| @learning-card-owner | card.review.due Kafka 이벤트 → notification 소비 | ← 수신 |
| @team-lead | Gateway 인증 필터 협의 | 양방향 |
| Frontend | 로그인/회원가입 API 제공 | 제공 → |

## 성공 기준

- [ ] OAuth 로그인 (Google/GitHub) 완전 동작
- [ ] JWT 발급/갱신/검증 정상
- [ ] Stripe 결제 플로우 (Checkout → Webhook → 플랜 활성화)
- [ ] 푸시 알림 발송 동작 (FCM)
- [ ] Audit 로그 Kafka 소비 → DB 적재
