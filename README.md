# synapse-platform-svc

> 작성일시: 2026-05-19 18:25 KST

Synapse 플랫폼 핵심 서비스입니다. 인증, 사용자, 결제, 알림, 관리자 기능을 Spring Boot 단일 애플리케이션 안에서 Spring Modulith 모듈 경계로 관리합니다.

## Modules

| Module | 설명 | 현재 구현 상태 |
|---|---|---|
| `auth` | JWT RS256, Refresh Token, Google/GitHub/Apple OAuth2, MFA TOTP | 구현 |
| `user` | 사용자 프로필, 사용자 설정, 모듈 간 사용자 조회 API | 구현 |
| `billing` | Stripe Checkout, 구독 조회, Stripe Webhook, 결제 이력 | 구현 |
| `notification` | FCM 디바이스 토큰 등록/해제 | 구현 |
| `admin` | 관리자 기능, Audit Log, Kafka Consumer | 골격 |
| `global` | 공통 예외 처리, 필드 암호화 등 공통 인프라 | 구현 |

> 실제 FCM 푸시 발송과 `firebase-admin` 연동은 아직 포함하지 않습니다. 현재 notification 모듈은 디바이스 토큰 저장과 해제만 담당합니다.

## Tech Stack

- Java 21
- Spring Boot 4.0.0
- Spring Modulith 2.0.6
- PostgreSQL 16
- Redis 7
- Flyway
- JPA
- Spring Security
- jjwt 0.12.6
- Stripe Java 32.1.0
- Testcontainers
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

최근 검증 결과:

```text
.\gradlew.bat clean build --no-daemon
BUILD SUCCESSFUL

JaCoCo LINE coverage: 92.38% (covered 921, missed 76)
```

---

## API Endpoints

### Auth

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/auth/refresh` | Refresh Token으로 Access Token 갱신 | 불필요 |
| `POST` | `/api/v1/auth/mfa/setup` | TOTP 시크릿 생성 및 QR URL 반환 | 필요 |
| `POST` | `/api/v1/auth/mfa/verify` | TOTP 코드 검증 | 필요 |
| `GET` | `/api/v1/auth/callback` | OAuth 성공 후 프론트 콜백 보조 엔드포인트 | 불필요 |
| `GET` | `/oauth2/authorization/google` | Google OAuth 로그인 시작 | 불필요 |
| `GET` | `/oauth2/authorization/github` | GitHub OAuth 로그인 시작 | 불필요 |
| `GET` | `/oauth2/authorization/apple` | Apple OAuth 로그인 시작 | 불필요 |

OAuth 로그인 성공 시 `?access_token={jwt}&refresh_token={token}` 쿼리 파라미터와 함께 `app.oauth2.redirect-uri`로 리디렉트됩니다.

### Billing

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/v1/billing/checkout` | Stripe Checkout Session 생성 | 필요 |
| `GET` | `/api/v1/billing/subscription` | 현재 사용자의 구독 정보 조회 | 필요 |
| `POST` | `/api/v1/billing/webhooks` | Stripe Webhook 수신 | 불필요 |

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

---

## Environment Variables

| 변수 | 설명 | 예시 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/synapse_platform` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL 사용자 | `synapse` |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL 비밀번호 | `synapse` |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis 포트 | `6379` |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | `xxx.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Client Secret | |
| `GITHUB_CLIENT_ID` | GitHub OAuth Client ID | |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | |
| `APPLE_CLIENT_ID` | Apple OAuth Client ID | |
| `APPLE_CLIENT_SECRET` | Apple OAuth Client Secret | |
| `APP_OAUTH2_REDIRECT_URI` | OAuth 성공 후 프론트 리디렉트 URI | `http://localhost:3000/auth/callback` |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트엔드 Origin 목록 | `http://localhost:3000,http://localhost:5173` |
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

---

## Architecture Notes

- 패키지 루트는 `com.synapse.platform`입니다.
- Spring Modulith 검증을 통해 모듈 간 직접 import를 제한합니다.
- 모듈 간 공유가 필요한 기능은 공개 API 패키지 또는 공통 인프라 타입을 통해 접근합니다.
- Refresh Token 원문은 저장하지 않고 SHA-256 hash만 DB에 저장합니다.
- JWT 서명 알고리즘은 RS256을 사용합니다.
- `notification` 모듈은 `auth` 내부 구현 타입을 직접 import하지 않고, 보안 필터는 `jakarta.servlet.Filter` bean으로 주입받습니다.

## Related Docs

- `AGENTS.md`
- `docs/ai/current/HANDOFF.md`
- `docs/ai/current/TASK.md`
- `docs/ai/current/WORKER_REPORT.md`
- `docs/project-management/README.md`
- `docs/synapse-platform-svc_ARCHITECTURE.md`
