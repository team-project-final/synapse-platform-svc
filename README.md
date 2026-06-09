# synapse-platform-svc

> 마지막 갱신: 2026-06-09 KST
> 기준 브랜치: `dev` (`cc57c9f`, PLAT-064 DB 기반 사용자 role 포함)

Synapse MSA의 platform 서비스입니다. 인증, 사용자, 결제, 알림, 감사 로그, 관리자 기능을 Spring Boot 단일 애플리케이션 안에서 Spring Modulith 모듈 경계로 관리합니다. 다른 서비스가 신뢰하는 JWT 발급/검증의 기준점이며, Kafka 이벤트 계약의 platform 영역을 담당합니다.

## 현재 상태

- W3/W4 platform 기능은 `dev` 기준 완료: auth, billing, notification, audit, admin, Kafka Avro 계약, Step 9 E2E, Step 10 알림 안정화.
- W5 프론트 연동 백엔드 갭 중 A-1 User self-service API와 PLAT-064 DB 기반 사용자 role 발급 경로가 반영되었습니다.
- Kafka는 Confluent Avro + Schema Registry bare typed record 방식입니다.
- Kafka 인프라는 `KAFKA_ENABLED=false`가 기본이며, GitOps/dev/staging/prod에서 필요할 때 `true`로 켭니다.
- JWT key는 기본 프로파일/prod에서 누락 또는 파싱 실패 시 기동 단계에서 fail-fast 합니다. dev/staging은 로컬 편의용 non-prod 기본 키를 갖습니다.
- 열린 GitHub 이슈는 #37, #62입니다. #37 staging datasource/profile 정합은 최신 GitOps/shared 기준으로 확인된 상태이고, #62 cross-service live E2E는 engagement/learning 선결 이슈 이후 재실행 대상입니다.

## Modules

| Module | 역할 | 상태 |
|---|---|---|
| `auth` | 이메일/비밀번호 가입/로그인, Google/GitHub/Apple OAuth2, JWT RS256, Refresh Token, MFA TOTP, OAuth 연결 관리 | 구현 |
| `user` | 사용자 도메인, 내 프로필/비밀번호/계정 삭제 self-service, DB 기반 role, 관리자 사용자 조회/상태 변경/삭제 | 구현 |
| `billing` | Stripe Checkout, 구독 조회, Stripe Webhook, 결제 이력, 관리자 테넌트 관리 | 구현 |
| `notification` | FCM 디바이스 토큰 등록/해제, `NotificationSend` 이벤트 기반 FCM/SES 발송 | 구현 |
| `audit` | 주요 도메인 Kafka 이벤트를 `audit_logs`에 적재, 관리자 감사 로그 조회 | 구현 |
| `admin` | 관리자 공통 영역 placeholder. 실제 API는 `user`, `billing`, `audit` 모듈에 위치 | 골격 |
| `global` | 공통 예외, crypto, Kafka config/error handler | 구현 |

## Tech Stack

- Java 21
- Spring Boot 4.0.0
- Spring Modulith 2.0.6
- PostgreSQL 16
- Redis 7
- Flyway
- Spring Data JPA
- Spring Security OAuth2 Client
- jjwt 0.12.6
- Apache Kafka + Avro 1.12.0 + Confluent Schema Registry 7.7.0
- Firebase Admin SDK 9.4.1
- AWS SDK for Java v2 SES v2 2.26.29
- Stripe Java 32.1.0
- Micrometer / Actuator
- Testcontainers 1.21.4
- Checkstyle, SpotBugs, JaCoCo

## Profiles And Ports

| Profile | 용도 | 주요 설정 |
|---|---|---|
| `dev` | 로컬/CI 기본값 | DB `localhost:5432/synapse`, Redis password `redis_local_pw`, non-prod JWT key 기본값 |
| `staging` | EKS staging | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_DATA_REDIS_*` 사용, cookie secure |
| `prod` | 운영 | 필수 secret/env 누락 시 fail-fast |

- 기본 앱 포트는 `8081`입니다.
- Kubernetes/GitOps 배포에서는 `SERVER_PORT=8080`으로 덮어써서 컨테이너 포트/probe와 맞춥니다.
- Actuator health: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`

## Local Run

가장 덜 꼬이는 로컬 실행은 `docker-compose.ci.yml`로 PostgreSQL/Redis만 띄우고 앱은 `bootRun`으로 실행하는 방식입니다. 이 compose 값이 `dev` 프로필 기본값과 맞습니다.

```powershell
docker compose -f docker-compose.ci.yml up -d --wait
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

```bash
docker compose -f docker-compose.ci.yml up -d --wait
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Health check:

```bash
curl http://localhost:8081/actuator/health
```

`docker-compose.yml`은 PostgreSQL/Redis와 app 컨테이너를 함께 정의하지만, 현재 app 서비스가 `8080:8080`으로 매핑되어 있습니다. 로컬 `bootRun`의 8081, gateway의 8080과 혼동될 수 있으니 platform 앱까지 compose로 띄우는 방식은 포트 정책을 먼저 맞춘 뒤 사용하세요.

## Local Infra Variants

`docker-compose.ci.yml` 기본값:

| Component | Host | Port | Credential |
|---|---|---|---|
| PostgreSQL | `localhost` | `5432` | DB `synapse`, user `synapse`, password `synapse_local_pw` |
| Redis | `localhost` | `6379` | password `redis_local_pw` |

`docker-compose.yml` 기본값:

| Component | Host | Port | Credential |
|---|---|---|---|
| PostgreSQL | `localhost` | `5432` | DB `synapse_platform`, user `synapse`, password `synapse` |
| Redis | `localhost` | `6379` | no password |

`docker-compose.yml`의 DB/Redis만 쓰려면 앱 실행 전에 값을 명시하세요.

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/synapse_platform'
$env:DB_USERNAME='synapse'
$env:DB_PASSWORD='synapse'
$env:SPRING_DATA_REDIS_HOST='localhost'
$env:SPRING_DATA_REDIS_PORT='6379'
$env:SPRING_DATA_REDIS_PASSWORD=''
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

## Build And Test

```bash
# full build: test, checkstyle, spotbugs, jacoco coverage verification
./gradlew clean build

# Avro SpecificRecord 생성
./gradlew generateAvroJava

# 전체 테스트
./gradlew test

# Modulith 구조 검증
./gradlew test --tests "*ModuleStructureTest"

# Kafka config/gate 관련 테스트
./gradlew test --tests "*Kafka*"

# Auth/Billing E2E
./gradlew test --tests "*AuthBillingE2ETest"
```

Windows에서 Testcontainers가 Docker Desktop Linux Engine을 못 찾으면 다음 값을 먼저 지정합니다.

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
```

## API Endpoints

### Auth

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/auth/signup` | 이메일/비밀번호 회원가입 | 불필요 |
| `POST` | `/api/v1/auth/login` | 이메일/비밀번호 로그인, access token 응답 + refresh cookie 설정 | 불필요 |
| `POST` | `/api/v1/auth/refresh` | HttpOnly refresh cookie로 access token 재발급 | Origin 검증 |
| `POST` | `/api/v1/auth/mfa/setup` | TOTP secret/QR URL 생성 | 필요 |
| `POST` | `/api/v1/auth/mfa/verify` | TOTP 코드 검증 | 필요 |
| `GET` | `/api/v1/auth/callback` | 프론트 OAuth callback 보조 엔드포인트 | 불필요 |
| `GET` | `/oauth2/authorization/google` | Google OAuth 시작 | 불필요 |
| `GET` | `/oauth2/authorization/github` | GitHub OAuth 시작 | 불필요 |
| `GET` | `/oauth2/authorization/apple` | Apple OAuth 시작 | 불필요 |

### User Self-Service

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | 내 프로필 조회 | 필요 |
| `PUT` | `/api/v1/users/me` | 표시 이름, 언어 설정 수정 | 필요 |
| `PUT` | `/api/v1/users/me/password` | 현재 비밀번호 검증 후 새 비밀번호로 변경 | 필요 |
| `DELETE` | `/api/v1/users/me` | 본인 계정 soft delete 및 세션 무효화 | 필요 |
| `GET` | `/api/v1/users/me/oauth` | 연결된 OAuth provider 목록 조회 | 필요 |
| `DELETE` | `/api/v1/users/me/oauth/{provider}` | OAuth 연결 해제. 마지막 로그인 수단 삭제는 차단 | 필요 |

Profile update request:

```json
{
  "displayName": "Updated User",
  "language": "ko-KR"
}
```

`language`는 `ko-KR`, `en-US`, `ja-JP`만 허용합니다. `timezone`은 현재 DB 스키마에 없으므로 API 계약에서 제외합니다.

Password change request:

```json
{
  "currentPassword": "Oldpass1!",
  "newPassword": "Newpass1!"
}
```

### Billing

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/billing/checkout` | Stripe Checkout Session 생성 | 필요 |
| `GET` | `/api/v1/billing/subscription` | 현재 사용자 구독 조회 | 필요 |
| `POST` | `/api/v1/billing/webhooks` | Stripe Webhook 수신 및 서명 검증 | 불필요 |

Checkout request:

```json
{
  "planCode": "PRO",
  "successUrl": "http://localhost:3000/billing/success",
  "cancelUrl": "http://localhost:3000/billing/cancel"
}
```

### Notification

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/notifications/devices` | FCM device token 등록 | 필요 |
| `DELETE` | `/api/v1/notifications/devices/{id}` | FCM device token 해제 | 필요 |

Device token request:

```json
{
  "token": "fcm-device-token",
  "platform": "android"
}
```

지원 platform 값은 `ios`, `android`, `web`입니다. 사용자당 신규 device token은 최대 5개까지 등록할 수 있고, 동일 token 재등록은 기존 row를 갱신합니다.

### Admin

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `GET` | `/api/v1/admin/users` | 관리자 사용자 목록/검색 | `ROLE_ADMIN` |
| `PUT` | `/api/v1/admin/users/{id}/status` | 사용자 상태 변경: `active`, `suspended` | `ROLE_ADMIN` |
| `DELETE` | `/api/v1/admin/users/{id}` | 사용자 삭제 | `ROLE_ADMIN` |
| `GET` | `/api/v1/admin/tenants` | 관리자 테넌트 목록 | `ROLE_ADMIN` |
| `PUT` | `/api/v1/admin/tenants/{id}/status` | 테넌트 상태 변경 | `ROLE_ADMIN` |
| `GET` | `/api/v1/admin/audit-logs` | 감사 로그 조회 | `ROLE_ADMIN` |

관리자 본인 정지/삭제는 차단됩니다. 사용자 정지/삭제 시 `UserSessionsRevocationRequested` 도메인 이벤트로 기존 세션을 무효화합니다.
운영 최초 어드민은 자동 seed/profile 없이 가입된 사용자에게 DB 작업으로 `ROLE_ADMIN`을 부여합니다. 절차는 `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`를 따릅니다.

## Kafka Events

Kafka는 기본적으로 꺼져 있습니다. `KAFKA_ENABLED=true`일 때 producer/consumer factory, listener, outbox publisher가 생성됩니다.

공통 설정:

- serializer/deserializer: Confluent `KafkaAvroSerializer` / `KafkaAvroDeserializer`
- schema registry: `SCHEMA_REGISTRY_URL`
- security protocol: `SPRING_KAFKA_SECURITY_PROTOCOL`, 기본 `PLAINTEXT`, MSK TLS-only 환경은 `SSL`
- message key: `tenantId`
- subject: `<topic>-value`
- idempotency key: Avro record의 `eventId`

| 방향 | Topic | Record | 처리 |
|---|---|---|---|
| 발행 | `platform.auth.user-registered-v1` | `UserRegistered` | 회원가입 후 outbox 저장 및 비동기 발행 |
| 소비 | `platform.auth.user-registered-v1` | `UserRegistered` | `audit_logs` 자동 적재 |
| 소비 | `platform.notification.notification-send-v1` | `NotificationSend` | FCM/SES 발송 |
| 소비 | `knowledge.note.note-created-v1` | `NoteCreated` | `audit_logs` 적재 |
| 소비 | `knowledge.note.note-updated-v1` | `NoteUpdated` | `audit_logs` 적재 |
| 소비 | `learning.card.review-completed-v1` | `ReviewCompleted` | `audit_logs` 적재 |
| 소비 | `engagement.gamification.badge-earned-v1` | `BadgeEarned` | `audit_logs` 적재 |
| 소비 | `engagement.gamification.level-up-v1` | `LevelUp` | `audit_logs` 적재 |

Avro schema는 `src/main/avro/`에 vendoring되어 있습니다. `platform` schema는 `UserRegistered`, `NotificationSend`이고, audit 소비용으로 `knowledge`, `learning`, `engagement` schema도 포함합니다.

## Notification Reliability

Step 10 기준으로 FCM/SES 발송 안정화가 반영되어 있습니다.

- FCM/SES 전송 retry: `app.notification.retry.max-attempts`, `backoff-ms`
- 기본값: `NOTIFICATION_RETRY_MAX_ATTEMPTS=3`, `NOTIFICATION_RETRY_BACKOFF_MS=200`
- backoff: `backoff-ms * attempt`
- metrics:
  - counter `notification.send`, tags `channel`, `result`
  - timer `notification.send.latency`, tag `channel`
- notification idempotency: `UNIQUE(event_id, channel)`
- email daily limit: 사용자당 하루 10건

## Environment Variables

| Variable | 설명 | 기본/예시 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` |
| `SERVER_PORT` | 서버 포트 override | 로컬 기본 `8081`, k8s `8080` |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/synapse` |
| `DB_USERNAME` | PostgreSQL username | `synapse` |
| `DB_PASSWORD` | PostgreSQL password | `synapse_local_pw` |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password | `redis_local_pw` |
| `SPRING_DATA_REDIS_SSL_ENABLED` | Redis TLS | staging/prod에서 사용 |
| `KAFKA_ENABLED` | Kafka bean/listener/outbox 활성화 | `false` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:9092` |
| `SCHEMA_REGISTRY_URL` | Schema Registry URL | `http://localhost:8086` |
| `SPRING_KAFKA_SECURITY_PROTOCOL` | Kafka security protocol | `PLAINTEXT`, `SSL` |
| `KAFKA_TOPIC_NOTIFICATION_SEND` | 알림 요청 소비 topic | `platform.notification.notification-send-v1` |
| `KAFKA_TOPIC_NOTE_CREATED` | audit 소비 topic | `knowledge.note.note-created-v1` |
| `KAFKA_TOPIC_NOTE_UPDATED` | audit 소비 topic | `knowledge.note.note-updated-v1` |
| `KAFKA_TOPIC_REVIEW_COMPLETED` | audit 소비 topic | `learning.card.review-completed-v1` |
| `KAFKA_TOPIC_BADGE_EARNED` | audit 소비 topic | `engagement.gamification.badge-earned-v1` |
| `KAFKA_TOPIC_LEVEL_UP` | audit 소비 topic | `engagement.gamification.level-up-v1` |
| `JWT_PRIVATE_KEY` | RS256 private key, PKCS#8/Base64 | prod 필수 |
| `JWT_PUBLIC_KEY` | RS256 public key, X.509/Base64 | prod 필수 |
| `JWT_KID` | JWT key id | `synapse-key-2026-05` |
| `JWT_ISSUER` | JWT issuer | `synapse-auth` |
| `AES_SECRET_KEY` | AES-256-GCM key, Base64 32 bytes | prod 필수 |
| `GOOGLE_CLIENT_ID` | Google OAuth client id | profile별 기본값 또는 secret |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret | profile별 기본값 또는 secret |
| `GITHUB_CLIENT_ID` | GitHub OAuth client id | profile별 기본값 또는 secret |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth client secret | profile별 기본값 또는 secret |
| `APPLE_CLIENT_ID` | Apple OAuth client id | profile별 기본값 또는 secret |
| `APPLE_CLIENT_SECRET` | Apple OAuth client secret | profile별 기본값 또는 secret |
| `CORS_ALLOWED_ORIGINS` | dev/default CORS origins | `http://127.0.0.1:8088` |
| `STAGING_FRONTEND_ORIGINS` | staging CORS origins | `https://staging.synapse.io` |
| `PROD_FRONTEND_ORIGINS` | prod CORS origins | `https://app.synapse.io` |
| `APP_OAUTH2_REDIRECT_URI` | OAuth success redirect URI | `http://localhost:3000/auth/callback` |
| `STRIPE_API_KEY` | Stripe secret key | prod 필수 |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook secret | prod 필수 |
| `STRIPE_PRO_PRICE_ID` | Stripe PRO price id | |
| `STRIPE_TEAM_PRICE_ID` | Stripe TEAM price id | |
| `STRIPE_ENTERPRISE_PRICE_ID` | Stripe ENTERPRISE price id | |
| `FCM_ENABLED` | Firebase Admin SDK 활성화 | `false` |
| `FCM_SERVICE_ACCOUNT_PATH` | Firebase service account JSON path | |
| `FCM_PROJECT_ID` | Firebase project id | |
| `SES_ENABLED` | SES client 활성화 | `false` |
| `SES_REGION` | SES region | `ap-northeast-2` |
| `SES_FROM_EMAIL` | SES sender email | `noreply@synapse.app` |
| `NOTIFICATION_RETRY_MAX_ATTEMPTS` | FCM/SES retry 횟수 | `3` |
| `NOTIFICATION_RETRY_BACKOFF_MS` | retry backoff 기준 ms | `200` |

## DB Migrations

Flyway migration은 `src/main/resources/db/migration/`에서 관리합니다.

| Version | 내용 |
|---|---|
| V1 | PostgreSQL extension 초기화 |
| V2 | tenants, plans 초기화 |
| V3 | users, oauth_identities 인증 기본 테이블 |
| V16 | RLS 정책 활성화 |
| V17 | 공통 trigger 생성 |
| V18 | plan quota seed |
| V19 | totp_credentials 생성 |
| V20 | oauth_identities.access_token_enc 추가 |
| V21 | refresh_tokens 생성 |
| V22 | totp_credentials -> mfa_credentials 마이그레이션 |
| V23 | refresh_tokens(user_id) unique index 추가 |
| V24 | subscriptions 생성 |
| V25 | payment_history 생성 |
| V26 | processed_events 생성 |
| V27 | device_tokens 생성 |
| V28 | refresh token 멀티 디바이스 허용 |
| V29 | audit_logs 생성 |
| V30 | outbox_events 생성 |
| V31 | notifications 생성 |
| V32 | users.status, suspended_at 추가 |
| V20260609140528 | user_roles 생성, 삭제되지 않은 기존 사용자 `ROLE_USER` 백필 |

Flyway 표준:

- `.github/workflows/flyway-guard.yml`가 synapse-shared reusable guard를 호출합니다.
- `spring.flyway.out-of-order=true`
- `spring.flyway.baseline-on-migrate=false`
- 신규 migration은 synapse-shared 표준의 14자리 timestamp 버전을 사용합니다. 예: `VyyyyMMddHHmmss__description.sql`
- DB vendor 종속 native SQL은 Testcontainers PostgreSQL 통합 테스트로 실제 실행 검증합니다.

## CI

- `build`: `./gradlew clean build`, Modulith verify
- `dev-smoke`: Docker Hub login 후 `docker-compose.ci.yml`로 PostgreSQL/Redis 기동, `dev` profile boot health 검증
- `Flyway Guard`: migration version/checksum/표준 검증

CI가 Docker Hub rate limit에 걸리지 않도록 `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` secret이 필요합니다.

## Architecture Notes

- 패키지 루트는 `com.synapse.platform`입니다.
- Spring Modulith로 모듈 간 직접 참조를 제한하고, 필요한 공유 기능은 공개 API 패키지나 도메인 이벤트로 연결합니다.
- Refresh Token은 원문을 저장하지 않고 SHA-256 hash만 DB에 저장하며, 사용자당 최대 5개 멀티 디바이스 세션을 FIFO로 관리합니다.
- JWT는 RS256만 사용합니다. key는 `JwtTokenProvider` 생성 시 1회 파싱하여 잘못된 키를 기동 단계에서 드러냅니다.
- JWT `roles` claim은 `user_roles` DB row에서 조회합니다. role 변경은 새 access token 발급 이후 반영됩니다.
- Kafka 발행은 outbox pattern으로 트랜잭션과 메시지 발행 사이의 유실을 줄이고, 소비는 `eventId` 기반 멱등성으로 중복 처리를 방지합니다.
- notification 모듈은 auth 내부 구현을 직접 import하지 않고 공개 API와 SecurityContext를 통해 사용자 경계를 다룹니다.

## Related Docs

- `docs/ai/current/HANDOFF.md`
- `docs/ai/current/TASK.md`
- `docs/project-management/task/TASK_platform.md`
- `docs/project-management/history/HISTORY_platform.md`
- `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`
- `docs/synapse-platform-svc_ARCHITECTURE.md`
