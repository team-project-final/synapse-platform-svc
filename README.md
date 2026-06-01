# synapse-platform-svc

> 최종 수정: 2026-06-01 KST

Synapse 플랫폼 핵심 서비스입니다. 인증, 사용자, 결제, 알림, 감사 로그, 관리자 기능을 Spring Boot 단일 애플리케이션 안에서 Spring Modulith 모듈 경계로 관리합니다. MSA의 **인증 허브** 역할을 담당하며 전 서비스의 JWT 검증 의존 기점입니다.

## Modules

| Module | 설명 | 현재 구현 상태 |
|---|---|---|
| `auth` | JWT RS256, Refresh Token(멀티 디바이스·HttpOnly Cookie), Google/GitHub/Apple OAuth2, MFA TOTP, 이메일/비밀번호 가입·로그인 | 구현 |
| `user` | 사용자 프로필(골격), 관리자 사용자 관리 API(상태 변경·삭제) | 일부 구현 |
| `billing` | Stripe Checkout, 구독 조회, Stripe Webhook, 결제 이력, 관리자 테넌트 관리 | 구현 |
| `notification` | FCM 디바이스 토큰 등록/해제, FCM 푸시·SES 이메일 발송(Kafka 이벤트 소비) | 구현 |
| `audit` | `UserRegistered` Kafka 소비 → `audit_logs` 자동 기록, 감사 로그 조회 API | 구현 |
| `admin` | 관리자 공통 영역(placeholder). 실제 관리 API는 `user`/`billing`/`audit` 모듈에 위치 | 골격 |
| `global` | 공통 예외 처리, 필드 암호화, Kafka 직렬화/에러 핸들링 등 공통 인프라 | 구현 |

## Tech Stack

- Java 21
- Spring Boot 4.0.0
- Spring Modulith 2.0.6
- PostgreSQL 16
- Redis 7
- Flyway
- JPA
- Spring Security (OAuth2 Client)
- jjwt 0.12.6
- Apache Kafka (spring-kafka) + Avro 1.12.0 + Confluent Schema Registry (`kafka-avro-serializer` 7.7.0)
- Firebase Admin SDK 9.4.1 (FCM)
- AWS SDK for Java v2 — SES (`sesv2` 2.26.29)
- Stripe Java 32.1.0
- Testcontainers 1.21.4 (PostgreSQL, Kafka)
- Checkstyle
- SpotBugs
- JaCoCo

---

## Getting Started

### 사전 요구사항

- JDK 21
- Docker Desktop
- PowerShell 또는 Bash

### 인프라 컨테이너 실행

```bash
docker compose up -d postgres redis
```

`docker-compose.yml` 기준 PostgreSQL 기본값은 다음과 같습니다.

| 항목 | 값 |
|---|---|
| DB | `synapse_platform` |
| User | `synapse` |
| Password | `synapse` |
| Port | `5432` |

> Kafka 발행/소비를 로컬에서 끝단까지 검증하려면 Kafka + Schema Registry가 필요합니다(기본 `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`, `SCHEMA_REGISTRY_URL=http://localhost:8086`). 통합 테스트는 EmbeddedKafka + mock Schema Registry(`mock://...`)를 사용하므로 별도 기동이 필요 없습니다.

### 로컬 애플리케이션 실행

`application-local.yml`은 로컬 DB 설정을 별도로 가지고 있습니다. Docker Compose의 PostgreSQL을 그대로 사용할 경우 datasource 환경 변수를 함께 지정합니다.

PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/synapse_platform'
$env:SPRING_DATASOURCE_USERNAME='synapse'
$env:SPRING_DATASOURCE_PASSWORD='synapse'
$env:SPRING_PROFILES_ACTIVE='local'
.\gradlew.bat bootRun
```

Bash:

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/synapse_platform' \
SPRING_DATASOURCE_USERNAME='synapse' \
SPRING_DATASOURCE_PASSWORD='synapse' \
SPRING_PROFILES_ACTIVE='local' \
./gradlew bootRun
```

애플리케이션 기본 포트는 `8081`입니다.

### Docker Compose로 애플리케이션까지 실행

```bash
docker compose up -d --build
```

---

## Build & Test

```bash
# 전체 빌드, 테스트, 정적 분석, JaCoCo 검증
./gradlew clean build

# Avro 스키마(.avsc) → SpecificRecord 코드 생성
./gradlew generateAvroJava

# 테스트 전체 실행
./gradlew test

# Modulith 구조 검증
./gradlew test --tests "*ModuleStructureTest"

# 정적 분석
./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest

# JaCoCo 리포트 생성
./gradlew jacocoTestReport
```

Windows + Testcontainers에서 Docker pipe를 명시해야 하는 경우:

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
```

> Avro 생성 소스(`generated-main-avro-java`, `generated-test-avro-java`)는 Checkstyle 대상에서 제외됩니다.

---

## API Endpoints

### Auth

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/auth/signup` | 이메일/비밀번호 회원가입 | 불필요 |
| `POST` | `/api/v1/auth/login` | 이메일/비밀번호 로그인 | 불필요 |
| `POST` | `/api/v1/auth/refresh` | Refresh Token(HttpOnly Cookie)으로 Access Token 갱신 | 불필요 |
| `POST` | `/api/v1/auth/mfa/setup` | TOTP 시크릿 생성 및 QR URL 반환 | 필요 |
| `POST` | `/api/v1/auth/mfa/verify` | TOTP 코드 검증 | 필요 |
| `GET` | `/api/v1/auth/callback` | OAuth 성공 후 프론트 콜백 보조 엔드포인트 | 불필요 |
| `GET` | `/oauth2/authorization/google` | Google OAuth 로그인 시작 | 불필요 |
| `GET` | `/oauth2/authorization/github` | GitHub OAuth 로그인 시작 | 불필요 |
| `GET` | `/oauth2/authorization/apple` | Apple OAuth 로그인 시작 | 불필요 |

OAuth 로그인 성공 시 Access Token은 `?access_token={jwt}` 쿼리 파라미터로, **Refresh Token은 HttpOnly Cookie**로 내려가며 `app.oauth2.redirect-uri`로 리디렉트됩니다(D-028). `/api/v1/auth/refresh`는 요청 Cookie의 Refresh Token을 사용하고 Origin 검증을 수행합니다.

### Billing

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/billing/checkout` | Stripe Checkout Session 생성 | 필요 |
| `GET` | `/api/v1/billing/subscription` | 현재 사용자의 구독 정보 조회 | 필요 |
| `POST` | `/api/v1/billing/webhooks` | Stripe Webhook 수신(서명 검증) | 불필요 |

Checkout 요청 예시:

```json
{
  "planCode": "PRO",
  "successUrl": "http://localhost:3000/billing/success",
  "cancelUrl": "http://localhost:3000/billing/cancel"
}
```

지원 플랜 코드는 `FREE`, `PRO`, `TEAM`, `ENTERPRISE`입니다. Checkout 생성은 `PRO`, `TEAM`, `ENTERPRISE` 플랜을 대상으로 합니다.

### Notification

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/notifications/devices` | FCM 디바이스 토큰 등록 | 필요 |
| `DELETE` | `/api/v1/notifications/devices/{id}` | FCM 디바이스 토큰 해제 | 필요 |

디바이스 등록 요청 예시:

```json
{
  "token": "fcm-device-token",
  "platform": "android"
}
```

지원 platform 값은 `ios`, `android`, `web`입니다. 사용자당 신규 디바이스 토큰은 최대 5개까지 등록할 수 있으며, 동일 토큰 재등록은 UPSERT로 기존 row의 `tenant_id`, `user_id`, `updated_at`을 갱신합니다.

> 실제 푸시/이메일 **발송**은 REST가 아니라 `platform.notification.notification-send-v1` Kafka 이벤트(`NotificationSend`) 소비로 처리됩니다(아래 Kafka Events 참고).

### Admin (`ROLE_ADMIN`)

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `GET` | `/api/v1/admin/audit-logs` | 감사 로그 조회(action·userId 필터, 페이지네이션) | ADMIN |
| `PUT` | `/api/v1/admin/users/{id}/status` | 사용자 상태 변경(active/suspended/deleted) | ADMIN |
| `DELETE` | `/api/v1/admin/users/{id}` | 사용자 삭제 | ADMIN |
| `PUT` | `/api/v1/admin/tenants/{id}/status` | 테넌트 상태 변경 | ADMIN |

관리자 본인 정지/삭제는 `400`(`AdminSelfActionException`), 잘못된 status 쿼리는 `400`(`InvalidUserStatusFilterException`)으로 응답합니다. 사용자 정지/삭제 시 `UserSessionsRevocationRequested` 이벤트로 기존 세션을 무효화합니다(모듈 순환 의존 회피).

---

## Kafka Events

Confluent **Avro + Schema Registry** 기반 bare typed record(`SpecificRecord`)로 직렬화합니다. 메시지 key는 `tenantId`, subject는 `<topic>-value`, 호환성은 BACKWARD입니다. 멱등성 키는 레코드의 `eventId`입니다. 스키마(`.avsc`)의 단일 출처는 synapse-shared이며 `src/main/avro/platform/`에 벤더링합니다.

| 방향 | 토픽 | 레코드 | 처리 |
|---|---|---|---|
| 발행 | `platform.auth.user-registered-v1` | `UserRegistered` | 회원가입 시 Outbox 저장 후 발행(`UserEventPublisher`/`OutboxEventPublisher`) |
| 소비 | `platform.auth.user-registered-v1` | `UserRegistered` | `audit_logs` 자동 기록(`AuditKafkaConsumer`, `eventId` 멱등) |
| 소비 | `platform.notification.notification-send-v1` | `NotificationSend` | FCM 푸시 / SES 이메일 발송(`NotificationKafkaConsumer`) |

- **Outbox 패턴**: 발행 트랜잭션과 메시지 발행의 원자성을 보장하고(유실 방지), PUBLISHING lease + async 실패 기록으로 재시도합니다.
- **at-least-once + 멱등성**: audit은 `eventId` PK, notification은 `UNIQUE(event_id, channel)`로 중복 처리를 방지합니다.
- **에러 처리**: `ErrorHandlingDeserializer`로 역직렬화 실패를 격리하고, `DefaultErrorHandler` + DLQ(`<topic>` + DLQ suffix)로 재시도/skip합니다. consumer group은 `platform-svc-group`으로 통일합니다.

---

## Environment Variables

| 변수 | 설명 | 예시 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/synapse_platform` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL 사용자 | `synapse` |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL 비밀번호 | `synapse` |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis 포트 | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 부트스트랩 서버 | `localhost:9092` |
| `SCHEMA_REGISTRY_URL` | Confluent Schema Registry URL | `http://localhost:8086` |
| `KAFKA_TOPIC_NOTIFICATION_SEND` | 알림 발송 소비 토픽 | `platform.notification.notification-send-v1` |
| `FCM_ENABLED` | FCM 발송 활성화 | `false` |
| `FCM_SERVICE_ACCOUNT_PATH` | Firebase 서비스 계정 키 경로 | |
| `FCM_PROJECT_ID` | Firebase 프로젝트 ID | |
| `SES_ENABLED` | AWS SES 발송 활성화 | `false` |
| `SES_REGION` | AWS SES 리전 | `ap-northeast-2` |
| `SES_FROM_EMAIL` | 발신 이메일 주소 | `noreply@synapse.app` |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | `xxx.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Client Secret | |
| `GITHUB_CLIENT_ID` | GitHub OAuth Client ID | |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | |
| `APPLE_CLIENT_ID` | Apple OAuth Client ID | |
| `APPLE_CLIENT_SECRET` | Apple OAuth Client Secret | |
| `APP_OAUTH2_REDIRECT_URI` | OAuth 성공 후 프론트 리디렉트 URI | `http://localhost:3000/auth/callback` |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트엔드 Origin 목록. `app.cors.allowed-origins`로 주입됨 | `http://127.0.0.1:8088` |
| `PROD_FRONTEND_ORIGINS` | prod 프로파일 허용 Origin 목록 | `https://app.synapse.io` |
| `JWT_PRIVATE_KEY` | RS256 JWT 서명용 개인키 PEM | |
| `JWT_PUBLIC_KEY` | RS256 JWT 검증용 공개키 PEM | |
| `JWT_KID` | JWT Key ID | `synapse-key-2026-05` |
| `JWT_ISSUER` | JWT issuer | `synapse-auth` |
| `AES_SECRET_KEY` | AES-256-GCM 필드 암호화 키, Base64 32 bytes | |
| `STRIPE_API_KEY` | Stripe Secret API Key | |
| `STRIPE_WEBHOOK_SECRET` | Stripe Webhook 서명 검증 Secret | |
| `STRIPE_PRO_PRICE_ID` | Stripe PRO price id | |
| `STRIPE_TEAM_PRICE_ID` | Stripe TEAM price id | |
| `STRIPE_ENTERPRISE_PRICE_ID` | Stripe ENTERPRISE price id | |

---

## DB Migrations

Flyway로 관리합니다. 마이그레이션 파일은 `src/main/resources/db/migration/`에 있습니다.

| 버전 | 내용 |
|---|---|
| V1 | PostgreSQL extension 초기화 |
| V2 | tenants, plans 초기화 |
| V3 | users, oauth_identities 등 인증 기본 테이블 |
| V16 | RLS 정책 활성화 |
| V17 | 공통 trigger 생성 |
| V18 | plan quota seed |
| V19 | totp_credentials 생성 |
| V20 | oauth_identities.access_token_enc 추가 (기존 환경 보정용 — V3에 포함됨) |
| V21 | refresh_tokens 생성 |
| V22 | totp_credentials에서 mfa_credentials로 이관 |
| V23 | refresh_tokens(user_id) unique index 추가 |
| V24 | subscriptions 생성 |
| V25 | payment_history 생성 |
| V26 | processed_events 생성 |
| V27 | device_tokens 생성 |
| V28 | refresh_tokens 멀티 디바이스 허용 (user_id unique 제거, FIFO 다중 세션) |
| V29 | audit_logs 생성 |
| V30 | outbox_events 생성 |
| V31 | notifications 생성 |
| V32 | users.status / suspended_at 추가 (계정 상태 관리) |

---

## Architecture Notes

- 패키지 루트는 `com.synapse.platform`입니다.
- Spring Modulith 검증을 통해 모듈 간 직접 import를 제한합니다(`ApplicationModules.verify()` CI 자동 검증).
- 모듈 간 공유가 필요한 기능은 공개 API 패키지 또는 공통 인프라 타입을 통해 접근하고, 부수효과(세션 무효화 등)는 직접 호출 대신 이벤트로 전달합니다.
- Refresh Token 원문은 저장하지 않고 SHA-256 hash만 DB에 저장하며, 사용자당 최대 5개 멀티 디바이스 세션을 FIFO로 관리합니다(HttpOnly Cookie 전달).
- JWT 서명 알고리즘은 RS256을 사용합니다.
- Kafka 직렬화는 Confluent Avro + Schema Registry(bare typed record)로 통일하고, 유실 방지는 Outbox 패턴, 중복 방지는 `eventId` 기반 멱등으로 보장합니다.
- `notification` 모듈은 `auth` 내부 구현 타입을 직접 import하지 않고, 보안 필터는 `jakarta.servlet.Filter` bean으로 주입받습니다.

## Related Docs

- `AGENTS.md`
- `docs/ai/current/HANDOFF.md`
- `docs/ai/current/TASK.md`
- `docs/project-management/README.md`
- `docs/synapse-platform-svc_ARCHITECTURE.md`
