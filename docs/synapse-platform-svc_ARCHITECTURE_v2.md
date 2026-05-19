# synapse-platform-svc — ARCHITECTURE

> **Wiki 03번** 기준선 + **03-D 어댑터 표준** 적용
> **Version**: v2.0 | **Updated**: 2026-05-18
> 본 문서는 레포 README + 실제 build/소스 구조에서 확인된 사실을 기반으로 작성됨

---

## 1. 책임 범위

Synapse 플랫폼의 **핵심 기반 서비스 모놀리스**. Spring Modulith로 도메인 모듈을 격리하되 **단일 Spring Boot 애플리케이션 + 단일 Dockerfile**로 배포.

| 모듈 | 책임 |
|------|------|
| `auth` | 인증/인가 — JWT RS256, OAuth2 (Google/GitHub), MFA TOTP, Refresh Token |
| `user` | 사용자 프로필 관리 |
| `notification` | 알림 — FCM 푸시, AWS SES 이메일 |
| `admin` | 관리자 기능, **Audit Log**, **Kafka Consumer** (감사 이벤트 수집) |
| `shared` | 공통 유틸리티 (FieldEncryptor 등) |

⚠️ **billing 모듈 부재** — Wiki 03번에 명시된 billing은 현재 이 레포에 없음. 향후 추가 또는 별도 레포 결정 필요.

---

## 2. 레포 구조 (실제)

```
synapse-platform-svc/
├── .github/                                # CI/CD
├── config/                                 # 외부 설정
├── docs/                                   # 아키텍처/가이드
├── gradle/wrapper/
├── src/
│   ├── main/
│   │   ├── java/io/synapse/platform/       # ★ 루트 패키지 (추정)
│   │   │   ├── PlatformApplication.java
│   │   │   ├── auth/                       # ★ Modulith 모듈 1
│   │   │   ├── user/                       # ★ Modulith 모듈 2
│   │   │   ├── notification/               # ★ Modulith 모듈 3
│   │   │   ├── admin/                      # ★ Modulith 모듈 4
│   │   │   └── shared/                     # ★ 공통 모듈
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── db/migration/               # Flyway V1 ~ V23
│   │       └── ...
│   └── test/
│       └── java/.../
│           └── *ModuleStructureTest.java   # Modulith 구조 검증
├── build.gradle.kts                        # 단일 빌드 파일
├── settings.gradle.kts
├── Dockerfile                              # 단일 Dockerfile
├── docker-compose.yml                      # 로컬 인프라
├── README.md
└── SECRETS.md
```

### 2.1 Spring Modulith 모듈 구조

Spring Modulith는 **Java 패키지 = 모듈** 컨벤션. 각 모듈은 다음과 같이 분리:

```
io.synapse.platform.<module>/
├── package-info.java                       // @ApplicationModule
├── domain/
│   ├── model/                              // Aggregate, Entity, VO
│   └── port/
│       └── outbound/                       // ★ Port (인터페이스)
├── application/
│   └── service/                            // UseCase = Application Service
├── infrastructure/
│   ├── adapter/
│   │   ├── outbound/                       // ★ Adapter (Port 구현)
│   │   │   ├── kafka/
│   │   │   ├── redis/
│   │   │   └── external/                   // FCM, SES, OAuth provider
│   │   ├── persistence/                    // JPA Repository
│   │   └── inbound/
│   │       ├── rest/                       // @RestController
│   │       └── kafka/                      // @KafkaListener
│   └── config/
├── api/                                    // ★ 다른 모듈에 노출하는 인터페이스
│   └── <Module>Api.java
└── internal/                               // 외부 접근 차단 (Modulith 강제)
```

### 2.2 Modulith 모듈 간 통신 규칙

| 통신 | 방법 |
|------|------|
| 모듈 간 동기 호출 | 호출 대상 모듈의 `api/` 패키지의 인터페이스만 호출 |
| 모듈 간 비동기 | **Spring Application Events** (`@ApplicationModuleListener`) — 같은 JVM 내 이벤트 |
| 외부 svc 호출 (다른 -svc 레포) | **03-D 어댑터**: Port → gRPC/REST/Kafka Adapter |
| 모듈 격리 검증 | `ModuleStructureTest` (Modulith built-in) |

---

## 3. 기술 스택 (README 기반)

| 영역 | 선택 |
|------|------|
| Language / Build | Java 21 + Gradle Kotlin DSL |
| Framework | **Spring Boot 4.0.0** + **Spring Modulith 1.3.0** |
| DB | PostgreSQL 16 |
| Cache | Redis 7 |
| Message Broker | **Kafka (AWS MSK)** |
| Migration | Flyway (V1 ~ V23) |
| JWT | jjwt 0.12.6 (**RS256**) |
| Test | JUnit 5 + Testcontainers + Spring Modulith verification |
| 정적 분석 | Checkstyle + Spotbugs |

---

## 4. Modulith 모듈별 책임 + 03-D Port/Adapter

### 4.1 `auth` 모듈

**도메인**: Tenant, User, OAuthIdentity, MfaCredential, RefreshToken (V21~V23 마이그레이션 기반)

**Inbound (외부에서 들어오는 진입점)**:
- REST: `/api/v1/auth/refresh`, `/auth/mfa/setup`, `/auth/mfa/verify`, `/oauth2/authorization/{google|github}`
- OAuth Callback: `?userId={uuid}`와 함께 `successRedirectUri`로 redirect

**Outbound Port → Adapter**:
| Port (도메인) | Adapter (인프라) | 호출 대상 |
|---------------|-----------------|----------|
| `OAuthProviderPort` | `GoogleOAuthAdapter` / `GithubOAuthAdapter` | Google / GitHub OAuth API |
| `RefreshTokenStorePort` | `RedisRefreshTokenAdapter` + `JpaRefreshTokenAdapter` | Redis + DB (백업) |
| `MfaSecretEncryptorPort` | `AesGcmEncryptorAdapter` | shared.FieldEncryptor |
| `UserLifecycleEventPublisher` | `UserEventKafkaAdapter` | Kafka (`user.*` 토픽) |

### 4.2 `user` 모듈

**도메인**: UserProfile, UserSettings (V16~V18 마이그레이션)

**Inbound**:
- REST: `/api/v1/users/me`, `/users/me/settings` 등
- Module API: `UserApi.getById(UUID)` — 다른 모듈(`notification` 등)이 호출

**Outbound Port → Adapter**: (현재 단순 — JPA 외 외부 의존성 적음)
| Port | Adapter |
|------|--------|
| `UserRepository` | JPA + RLS |

다른 모듈은 `UserApi` 인터페이스로만 user에 접근.

### 4.3 `notification` 모듈

**도메인**: Notification, NotificationPreference, DeviceToken, NotificationTemplate

**Inbound**:
- REST: `/api/v1/notifications/**`
- Kafka Listener: 다른 svc의 모든 도메인 이벤트 (card.reviewed, gamification.*, community.*, billing.*, note.* 등)
- Spring ApplicationEvent Listener: 같은 JVM 내 `auth`/`user` 이벤트 (예: 회원가입 환영 알림)

**Outbound Port → Adapter**:
| Port | Adapter | 호출 대상 |
|------|---------|----------|
| `PushNotificationGateway` | `FcmPushAdapter`, `ApnsPushAdapter` | FCM / APNs |
| `EmailGateway` | `SesEmailAdapter` | **AWS SES** |
| `UserPort` (모듈 간) | `UserApiAdapter` (직접 호출) | user 모듈 |
| `NotificationDeliveryEventPublisher` | `NotificationEventKafkaAdapter` | Kafka |

### 4.4 `admin` 모듈

**도메인**: AuditLog (append-only)

**Inbound**:
- REST: `/api/v1/admin/audit/**` (관리자 권한 필요)
- **Kafka Consumer**: 전 시스템의 모든 도메인 이벤트를 구독하여 audit_logs에 적재

**Outbound Port → Adapter**:
| Port | Adapter |
|------|--------|
| `AuditLogRepository` | JPA (월별 파티셔닝) |
| `LongTermStoragePort` | `S3GlacierAdapter` (Phase 2 — 90일 이상 데이터) |

### 4.5 `shared` 모듈

순수 공통 유틸 (도메인 없음):
- `FieldEncryptor` — AES-256-GCM Envelope Encryption (`mfa_credentials`, `oauth_identities.access_token_enc` 등 암호화)
- CloudEvents 빌더/파서 (Wiki 03.4 표준 형식)
- TenantContext + Interceptor (멀티테넌시 — Wiki 03.3)
- Outbox 공통 구현 (03-A.6)
- Idempotency Helper (03-A.5.3)
- OpenTelemetry 설정

---

## 5. 외부 인터페이스 요약

### 5.1 REST API (Gateway 경유)

README에 명시된 부분:
- `POST /api/v1/auth/refresh` — Refresh Token으로 Access 갱신
- `POST /api/v1/auth/mfa/setup` — TOTP 시크릿 + QR URL
- `POST /api/v1/auth/mfa/verify` — TOTP 코드 검증
- `GET /oauth2/authorization/google` — Google OAuth 시작
- `GET /oauth2/authorization/github` — GitHub OAuth 시작

추가 엔드포인트는 Wiki 04번 API 명세서 참조.

### 5.2 Kafka

**Producer** (Outbox 패턴 — 03-A.6):
- `user.registered`, `user.deleted` (auth 모듈)
- `user.profile.updated` (user 모듈)
- `notification.sent` (notification 모듈)

**Consumer**:
- `admin` 모듈: **모든** 도메인 이벤트 (audit 적재)
- `notification` 모듈: 알림이 필요한 모든 이벤트

---

## 6. 데이터 저장소

### 6.1 PostgreSQL (단일 DB, 모듈별 스키마/테이블 prefix)

| Flyway 버전 | 핵심 변경 |
|-----|-----|
| V1~V3 | users, oauth_identities, tenants |
| V16~V18 | tenant_members, user_settings |
| V19 | totp_credentials (초기) |
| V20 | oauth_identities.access_token_enc 추가 (암호화 컬럼) |
| V21 | refresh_tokens |
| V22 | totp_credentials → mfa_credentials 이관 |
| V23 | refresh_tokens(user_id) UNIQUE INDEX |

**RLS 활성화**: `users`, `tenants`, `audit_logs` 등 — Wiki 03.3.

### 6.2 Redis (7 — Cluster)

| 키 패턴 | 모듈 | 용도 | TTL |
|--------|------|-----|-----|
| `auth:refresh:{tokenHash}` | auth | Refresh Token 메타 | 7d |
| `auth:oauth:pkce:{state}` | auth | PKCE codeVerifier | 10m |
| `auth:loginfail:{ip}` | auth | 로그인 실패 카운터 | 1h |
| `notif:unread:{userId}` | notification | 미읽음 카운트 | 영구 |
| `notif:ratelimit:{userId}:{channel}` | notification | 알림 발송 빈도 제한 | 1h |

### 6.3 Kafka (AWS MSK)

CloudEvents 1.0 + Binary Mode (Wiki 03.4 + 03-C 참조).

---

## 7. 환경 변수 (README 기반)

| 변수 | 설명 |
|------|------|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | **RS256** 키 쌍 (PEM) |
| `AES_SECRET_KEY` | AES-256-GCM 키 (Base64, 32 bytes) |
| `REDIS_HOST` / `REDIS_PORT` | Redis 접속 |

운영 환경에서는 AWS Secrets Manager + External Secrets Operator 경유 (03-B.7).

---

## 8. 빌드 / 배포

### 8.1 빌드

```bash
./gradlew build
./gradlew test
./gradlew test --tests "*ModuleStructureTest"    # Modulith 구조 검증
./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

### 8.2 Docker

**단일 이미지**: `ghcr.io/team-project-final/synapse-platform-svc:{version}`

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app.jar"]
```

### 8.3 K8s 배포

단일 Deployment. HPA는 CPU + 요청률 기반.

---

## 9. 관측성

### 9.1 메트릭 (Micrometer → Prometheus)

| 메트릭 | 모듈 | 라벨 |
|--------|------|------|
| `auth_login_total` | auth | `result`, `provider`, `tenant` |
| `auth_token_issued_total` | auth | `tokenType` |
| `notification_sent_total` | notification | `channel`, `templateCode` |
| `audit_events_consumed_total` | admin | `eventType` |

### 9.2 로그 (구조화 JSON)

```json
{ "@timestamp":"...", "service":"synapse-platform-svc", "module":"auth", "traceId":"...", "tenantId":"...", "userId":"...", "level":"INFO", "message":"..." }
```

민감 필드 마스킹: `password`, `mfa_secret`, `refresh_token`, `access_token`, `oauth_secret`.

---

## 10. 보안 (Wiki 03.7 + 03-B 적용)

| 항목 | 구현 |
|------|------|
| 비밀번호 | BCrypt (cost 12) |
| MFA Secret | AES-256-GCM Envelope Encryption (shared.FieldEncryptor) |
| OAuth access_token (DB 저장) | AES-256-GCM (V20) |
| Refresh Token | SHA-256 해시 후 저장 |
| JWT 서명 | RS256 (RSA 공개키 검증) |
| OAuth Callback | state + PKCE |
| Refresh Token Reuse | 감지 시 전 세션 무효화 (03-B.6.3) |

---

## 11. 로컬 개발

### 11.1 사전 준비

- JDK 21
- Docker Desktop (PostgreSQL + Redis)
- **Windows + Testcontainers**: Linux engine 명시 필수
  ```powershell
  $env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
  ```

### 11.2 실행

```bash
# 인프라
docker compose up -d

# 애플리케이션
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## 12. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| Modulith 구조 검증 실패 | 모듈 간 `internal` 패키지 접근 | `api/`만 외부 노출, 나머지는 internal |
| OAuth Callback 401 | state 불일치 (Redis 만료) | TTL 확인, 사용자 재시도 |
| Refresh Token reuse | 토큰 탈취 또는 동시 요청 | 전 세션 무효화 (정상 동작) |
| FCM 401 | 서버 키 만료 | Firebase Console 재발급 → Secrets Manager 갱신 |
| Audit Kafka lag 증가 | admin 모듈 처리 지연 | Consumer concurrency 증설 |

---

## 13. 안티패턴 (03-D 위반 + Modulith 위반)

- ❌ Controller가 외부 SDK(FCM, OAuth) 직접 호출 — Port 경유
- ❌ `auth` 모듈에서 `user.internal.UserRepository` 직접 import — `UserApi`만 사용
- ❌ Adapter에 비즈니스 로직 — Application Service로 이동
- ❌ Port가 `org.springframework.kafka` import — 도메인은 Kafka 무지
- ❌ JPA Entity를 Controller까지 노출 — Domain Model + DTO 분리

---

## 14. 알려진 갭

| 갭 | 영향 |
|---|---|
| **billing 모듈 부재** | Wiki 03 명시 기능 없음. 후속 결정 필요 |
| 운영 단계의 OAuth provider | Wiki는 4종(Google/GitHub/Apple/MS), 현재 2종만 |
| Refresh Token 회전 정책 (V23) | 구현 여부 확인 필요 |

---

## 15. 참고

- **Wiki 03_프로젝트_아키텍처_정의서**
- **Wiki 02_ERD_문서**
- **Wiki 04_API_명세서**
- **03-A** — 통신 운영, Outbox, 멱등성
- **03-B** — JWT/Cookie/시크릿/감사
- **03-C** — CloudEvents 스키마
- **03-D** — Port/Adapter 표준
- [Spring Modulith Reference](https://docs.spring.io/spring-modulith/reference/)
- [jjwt 0.12 RS256](https://github.com/jwtk/jjwt)
