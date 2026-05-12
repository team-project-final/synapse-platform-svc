# synapse-platform-svc

Synapse 플랫폼 핵심 서비스 — Auth · Audit · Billing · Notification

## Modules

| Module | 설명 |
|---|---|
| `auth` | 인증/인가 (JWT, OAuth2) |
| `audit` | 감사 로그 |
| `billing` | 구독/결제 |
| `notification` | 알림 (email, push) |

## Tech Stack
- Java 21 · Spring Boot 3.4 · Spring Modulith
- PostgreSQL · Kafka · Redis
