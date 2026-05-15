# synapse-platform-svc

Synapse 플랫폼 핵심 서비스 — Auth · User · Notification · Admin

## Modules

| Module | 설명 |
|---|---|
| `auth` | 인증/인가 (JWT RS256, OAuth2 Google/GitHub, MFA TOTP) |
| `user` | 사용자 프로필 관리 |
| `notification` | 알림 (FCM 푸시, AWS SES 이메일) |
| `admin` | 관리자 기능, Audit Log, Kafka Consumer |
| `shared` | 공통 유틸리티 (FieldEncryptor 등) |

## Tech Stack

- Java 21 · Spring Boot 4.0.0 · Spring Modulith 1.3.0
- PostgreSQL 16 · Redis 7 · Kafka (AWS MSK)
- Flyway · jjwt 0.12.6 · Testcontainers

---

## Getting Started

### 사전 요구사항

- Docker Desktop (PostgreSQL · Redis 컨테이너 실행용)
- JDK 21

### 로컬 실행

```bash
# 인프라 컨테이너 실행
docker compose up -d

# 애플리케이션 실행 (local 프로파일)
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 빌드 및 테스트

```bash
# 전체 빌드
./gradlew build

# 테스트 전체 실행
./gradlew test

# Modulith 구조 검증
./gradlew test --tests "*ModuleStructureTest"

# 정적 분석
./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

> **Windows + Testcontainers**: Docker Desktop Linux engine을 명시해야 합니다.
> ```powershell
> $env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
> ```

---

## API Endpoints

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `POST` | `/api/v1/auth/refresh` | Refresh Token으로 Access Token 갱신 | 불필요 |
| `POST` | `/api/v1/auth/mfa/setup` | TOTP 시크릿 생성 + QR URL 반환 | 필요 |
| `POST` | `/api/v1/auth/mfa/verify` | TOTP 코드 검증 | 필요 |
| `GET`  | `/oauth2/authorization/google` | Google OAuth 로그인 시작 | 불필요 |
| `GET`  | `/oauth2/authorization/github` | GitHub OAuth 로그인 시작 | 불필요 |

OAuth 로그인 성공 시 `?userId={uuid}` 쿼리 파라미터와 함께 `successRedirectUri`로 리디렉트됩니다.

---

## Environment Variables

| 변수 | 설명 | 예시 |
|------|------|------|
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | `xxx.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Client Secret | |
| `GITHUB_CLIENT_ID` | GitHub OAuth Client ID | |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | |
| `JWT_PRIVATE_KEY` | RS256 JWT 서명용 개인키 (PEM) | |
| `JWT_PUBLIC_KEY` | RS256 JWT 검증용 공개키 (PEM) | |
| `AES_SECRET_KEY` | AES-256-GCM 필드 암호화 키 (Base64, 32bytes) | |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `REDIS_PORT` | Redis 포트 | `6379` |

---

## DB Migrations

Flyway로 관리합니다. `src/main/resources/db/migration/` 참조.

| 버전 | 내용 |
|------|------|
| V1~V3 | users, oauth_identities, tenants 기본 테이블 |
| V16~V18 | tenant_members, user_settings 등 |
| V19 | totp_credentials (초기) |
| V20 | oauth_identities.access_token_enc 추가 |
| V21 | refresh_tokens 테이블 생성 |
| V22 | totp_credentials → mfa_credentials 이관 |
| V23 | refresh_tokens(user_id) unique index 추가 |
