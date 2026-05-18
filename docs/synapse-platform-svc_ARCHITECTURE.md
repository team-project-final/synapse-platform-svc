# synapse-platform-svc — ARCHITECTURE

> **Synapse Wiki**: [03_프로젝트_아키텍처_정의서](https://github.com/team-project-final/documents/wiki/03_%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8_%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98_%EC%A0%95%EC%9D%98%EC%84%9C) 기준선
> **Version**: v1.0 | **Updated**: 2026-05-18

---

## 1. 책임 범위

Synapse 플랫폼의 **공통 기반 서비스 묶음**. 인증, 결제, 감사, 알림 — 다른 모든 도메인 서비스가 의존하는 횡단 관심사.

| 포함 서비스 | Wiki 03.2.4 책임 |
|------------|-----------------|
| **Auth Service** | OAuth (Google/GitHub/Apple/Microsoft), JWT 발급, MFA, 세션 관리, 테넌트 생성 |
| **Billing Service** | 플랜 관리, Stripe 연동, Webhook 처리, 사용량 제한, 인보이스 |
| **Audit Service** | Kafka 이벤트 소비 → audit_logs 적재, 90일 보존, 관리자 조회 API |
| **Notification Service** | Kafka 이벤트 소비 → notifications 적재, FCM/APNs/SES 발송, 사용자 설정 |

User 정보 관리는 **Auth Service 내부**(users 테이블 + 프로필 API).

---

## 2. 레포 구조 (Gradle 멀티모듈)

```
synapse-platform-svc/
├── build.gradle.kts                    # 루트 빌드
├── settings.gradle.kts                 # 모듈 선언
├── gradle/
│   └── libs.versions.toml              # Version Catalog
├── platform-common/                    # 공통 모듈
│   ├── src/main/java/io/synapse/platform/common/
│   │   ├── exception/
│   │   ├── tenant/                     # TenantContext, Interceptor
│   │   ├── auth/                       # JwtUtils, RoleEnum
│   │   ├── cloudevents/                # CloudEvents 빌더/파서
│   │   ├── outbox/                     # Outbox Relay 공통 구현
│   │   ├── kafka/                      # ProducerConfig, ConsumerConfig
│   │   ├── grpc/                       # ServerConfig, AuthInterceptor
│   │   └── observability/              # OTel, MDC
├── auth-service/                       # 독립 Spring Boot 앱
│   ├── build.gradle.kts
│   ├── src/main/java/io/synapse/platform/auth/
│   │   ├── AuthApplication.java
│   │   ├── domain/                     # User, Tenant, RefreshToken
│   │   ├── application/                # AuthService, OAuthService
│   │   ├── infrastructure/
│   │   │   ├── persistence/            # JPA Repository
│   │   │   ├── oauth/                  # 외부 IdP 어댑터
│   │   │   ├── redis/                  # Refresh Token 저장
│   │   │   └── outbox/                 # Outbox Publisher
│   │   └── interfaces/
│   │       ├── rest/                   # REST Controller
│   │       ├── grpc/                   # AuthService gRPC 구현
│   │       └── kafka/                  # (이 서비스는 발행만, 구독 없음)
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       ├── db/migration/               # Flyway
│       └── proto/                      # gRPC 파일 (synapse-shared 참조)
├── billing-service/                    # 독립 Spring Boot 앱
│   └── ... (auth와 동일 구조)
├── audit-service/                      # 독립 Spring Boot 앱
│   └── ...
└── notification-service/               # 독립 Spring Boot 앱
    └── ...
```

### 모듈 의존성

```
[auth-service]──┐
[billing-service]├─→ [platform-common] ──→ [synapse-shared] (외부 레포)
[audit-service]──┤
[notification-service]┘
```

각 서비스는 **독립 배포 단위** — 별도 Docker 이미지, 별도 K8s Deployment.

---

## 3. 도메인 모델 핵심

### 3.1 Auth Service

```
Tenant (테넌트)
  └─ Users (소속 사용자)
       ├─ TenantMembership (역할: owner/admin/member)
       ├─ OAuthIdentity (provider별 외부 ID)
       ├─ MfaSecret (암호화 저장)
       └─ RefreshToken (Redis + DB 백업)
```

**Aggregate Root**: `User`, `Tenant`

### 3.2 Billing Service

```
Subscription (구독)
  ├─ Plan (Free/Pro/Team/Enterprise)
  ├─ StripeCustomer (외부 ID)
  ├─ Invoices (이력)
  └─ UsageCounter (월별 사용량)
```

**Aggregate Root**: `Subscription`

### 3.3 Audit Service

```
AuditLog (불변, append-only)
  ├─ eventCategory (auth, authz, billing, ...)
  ├─ severity (INFO/WARN/CRITICAL)
  ├─ actor (USER/SYSTEM/API_KEY/ADMIN)
  └─ details (JSONB)
```

### 3.4 Notification Service

```
Notification (인앱 알림)
  ├─ NotificationPreference (사용자별 설정)
  ├─ DeviceToken (FCM/APNs 토큰)
  └─ NotificationTemplate (다국어, 채널별)
```

상세 ERD는 Wiki 02번 참조.

---

## 4. 외부 인터페이스

### 4.1 REST API (Gateway 경유)

| 경로 | 서비스 | Wiki 04번 참조 |
|------|--------|---------------|
| `/api/v1/auth/**` | auth-service | 로그인, 회원가입, OAuth, MFA, 토큰 |
| `/api/v1/users/**` | auth-service | 프로필 조회/수정, 사용자 삭제 |
| `/api/v1/tenants/**` | auth-service | 테넌트 멤버 관리, 초대 |
| `/api/v1/billing/**` | billing-service | 플랜 변경, Checkout, Invoice |
| `/api/v1/notifications/**` | notification-service | 인앱 알림 조회, 읽음 처리, 설정 |
| `/api/v1/audit/**` (관리자만) | audit-service | 감사 로그 검색 |
| `/ws/notifications` | notification-service | WebSocket 실시간 푸시 |

### 4.2 Webhook (외부 → 내부)

| 경로 | 출처 | 처리 |
|------|------|------|
| `/webhooks/stripe` | Stripe | 결제/구독 이벤트 → 검증(`Stripe-Signature`) → 내부 처리 |
| `/webhooks/oauth/{provider}/callback` | OAuth Provider | Authorization Code → ID Token 검증 → 사용자 매핑 |

### 4.3 gRPC API (내부 전용)

```protobuf
// synapse-shared/proto/synapse/internal/platform/v1/auth_service.proto
service AuthService {
  rpc Introspect(IntrospectRequest) returns (IntrospectResponse);
  rpc GetUserById(GetUserRequest) returns (UserSummary);
}

service UserService {
  rpc GetById(GetUserRequest) returns (User);
  rpc BatchGetByIds(BatchGetRequest) returns (BatchGetResponse);
}
```

호출자: 모든 다른 서비스 (JWT 검증, 사용자 정보 조회).

### 4.4 Kafka

**Producer** (이 레포가 발행):
- `user.registered` ← auth-service
- `user.deleted` ← auth-service
- `billing.subscription.changed` ← billing-service
- `notification.send` ← notification-service (자체 라우팅용은 거의 없음, 주로 소비자)
- `audit.event` (Audit Service가 자체 발행하지 않음 — 다른 svc가 발행)

**Consumer** (이 레포가 구독):
- `user.registered`, `user.deleted` → notification-service (환영/안내)
- `billing.subscription.changed` → notification-service, audit-service
- `card.review.due` → notification-service (복습 리마인더)
- `gamification.xp.earned`, `gamification.badge.earned`, `gamification.level.up` → notification-service
- `community.deck.shared`, `community.note.shared`, `community.group.joined`, `community.report.created` → notification-service
- **모든 도메인 이벤트** → audit-service (감사 로그 수집)

---

## 5. 데이터 저장소

### 5.1 PostgreSQL

각 서비스별 별도 스키마 (또는 별도 DB — Phase 2):

| 서비스 | 스키마 | 핵심 테이블 |
|--------|--------|------------|
| auth | `platform_auth` | tenants, users, tenant_memberships, refresh_tokens, oauth_identities, mfa_secrets |
| billing | `platform_billing` | subscriptions, invoices, usage_counters, plans |
| audit | `platform_audit` | audit_logs (월별 파티셔닝) |
| notification | `platform_notification` | notifications, notification_preferences, device_tokens, notification_templates |

**RLS 활성화**: `tenants`, `users`, `audit_logs` 등 — Wiki 03.3 멀티테넌시 정책.

### 5.2 Redis

| 키 패턴 | 서비스 | 용도 | TTL |
|---------|--------|------|-----|
| `auth:refresh:{tokenHash}` | auth | Refresh Token 메타 | 7d |
| `auth:session:{userId}` | auth | 활성 세션 set | 24h |
| `auth:loginfail:{ip}` | auth | 로그인 실패 카운터 | 1h |
| `auth:oauth:pkce:{state}` | auth | PKCE codeVerifier | 10m |
| `billing:usage:{tenantId}:{period}` | billing | 사용량 카운터 | 월말 |
| `notif:unread:{userId}` | notification | 미읽음 카운트 | 영구 |
| `notif:ratelimit:{userId}:{channel}` | notification | 알림 발송 빈도 제한 | 1h |

### 5.3 S3 / 외부 저장소

- 첨부파일: knowledge-svc 소관 (이 레포 아님)
- 감사 로그 콜드 스토리지: 90일 이전 audit_logs를 S3 Glacier로 이관

---

## 6. 외부 의존성

### 6.1 다른 svc 호출 (gRPC)

이 레포는 **다른 svc에 거의 의존하지 않음**. 다른 모든 svc가 platform을 호출하는 구조.

예외:
- notification-service → 사용자 정보가 필요할 때 자체 auth-service의 UserService 호출 (동일 레포 내)

### 6.2 외부 API

| 서비스 | 호출 대상 | 용도 |
|--------|----------|------|
| auth | Google OAuth, GitHub OAuth, Apple Sign In, Microsoft Identity | 로그인 |
| billing | Stripe API | 결제, 구독, Webhook |
| notification | FCM (Android/Web), APNs (iOS) | 푸시 알림 |
| notification | AWS SES | 이메일 발송 |
| notification | Slack Incoming Webhook (선택) | 관리자 알림 |

**Resilience4j 설정**: 03-A 문서 A.3.3 표 참조.

### 6.3 시크릿 의존성

- AWS Secrets Manager → External Secrets Operator → K8s Secret → env 주입
- 키 카테고리: 03-B 문서 B.7.3 표 참조

---

## 7. 빌드 / 배포

### 7.1 Gradle 빌드

```kotlin
// settings.gradle.kts
rootProject.name = "synapse-platform-svc"
include(
    ":platform-common",
    ":auth-service",
    ":billing-service",
    ":audit-service",
    ":notification-service"
)
```

```kotlin
// auth-service/build.gradle.kts
plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm")
    kotlin("plugin.spring")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation(project(":platform-common"))
    implementation("io.synapse:synapse-shared:1.0.0")    // 외부 레포 publish
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.x")
    implementation("net.devh:grpc-server-spring-boot-starter:3.x")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.opentelemetry:opentelemetry-api")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-core")
}
```

### 7.2 Docker

```dockerfile
# auth-service/Dockerfile
FROM eclipse-temurin:21-jre-alpine
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-javaagent:/opt/otel/opentelemetry-javaagent.jar", \
  "-jar", "/app.jar"]
```

각 서비스는 **별도 이미지**: `ghcr.io/team-project-final/auth-service:{version}` 등.

### 7.3 K8s 매니페스트

`synapse-gitops` 레포의 `manifests/platform/` 하위에 서비스별 Deployment / Service / VirtualService 정의. 표준 리소스는 Wiki 03.6 표 참조.

---

## 8. 관측성

### 8.1 메트릭

| 메트릭 | 서비스 | 라벨 |
|--------|--------|------|
| `auth_login_total` | auth | `result`, `provider`, `tenant` |
| `auth_token_issued_total` | auth | `tokenType`, `tenant` |
| `billing_payment_total` | billing | `status`, `plan` |
| `audit_events_consumed_total` | audit | `eventType`, `severity` |
| `notification_sent_total` | notification | `channel`, `templateCode`, `status` |
| `notification_send_duration_seconds` | notification | `channel` |

### 8.2 로그

표준 JSON 포맷 (Wiki 03-A.7.2와 동일):

```json
{ "@timestamp":"...", "service":"auth-service", "traceId":"...", "tenantId":"...", "userId":"...", "level":"INFO", "message":"...", "context":{...} }
```

민감 필드 마스킹: `mfa_secret`, `refresh_token`, `password`, `stripe_secret_key`, `email` (일부).

### 8.3 트레이싱

모든 REST/gRPC/Kafka에 OTel 자동 계측. Jaeger 대시보드.

---

## 9. 보안

### 9.1 인증/인가

| 엔드포인트 | 인증 | 인가 |
|-----------|------|------|
| `/api/v1/auth/login`, `/signup`, `/refresh` | Public | - |
| `/api/v1/auth/me`, `/users/me/**` | JWT 필수 | 본인만 |
| `/api/v1/tenants/{id}/**` | JWT 필수 | 해당 tenant 멤버 |
| `/api/v1/audit/**` | JWT 필수 | `tenant.owner` 역할 |
| `/webhooks/stripe` | Stripe Signature 검증 | - |
| `/internal/**` | mTLS (Istio) | AuthorizationPolicy |

### 9.2 민감 데이터

- 비밀번호: BCrypt (cost factor 12)
- MFA Secret: AES-256-GCM Envelope Encryption (03-B.10.3)
- Refresh Token: SHA-256 해시 후 저장 (평문 저장 금지)

### 9.3 외부 호출 보호

- Stripe Webhook: 서명 검증 + Idempotency-Key
- OAuth Callback: state + PKCE 검증
- FCM/APNs: 인증서 자동 갱신 (Phase 2: AWS Secrets Manager 회전)

---

## 10. 로컬 개발

### 10.1 사전 준비

```bash
# JDK 21 + Gradle 8.x
sdk install java 21.0.5-tem
./gradlew --version
```

### 10.2 의존 인프라 기동

```bash
# 루트의 docker-compose.dev.yml
docker compose -f docker-compose.dev.yml up -d \
  postgres redis kafka schema-registry mailhog stripe-mock
```

| 컴포넌트 | 호스트 | 용도 |
|---------|--------|------|
| postgres | localhost:5432 | DB |
| redis | localhost:6379 | 캐시/세션 |
| kafka | localhost:9092 | 이벤트 버스 |
| schema-registry | localhost:8081 | Apicurio Registry |
| mailhog | localhost:8025 | 이메일 발송 테스트 |
| stripe-mock | localhost:12111 | Stripe API 모킹 |

### 10.3 단일 서비스 실행

```bash
./gradlew :auth-service:bootRun --args='--spring.profiles.active=dev'
```

### 10.4 전체 서비스 실행 (Tilt)

```bash
tilt up                                  # Tiltfile 기반
```

### 10.5 테스트

```bash
./gradlew test                           # 단위
./gradlew integrationTest                # 통합 (Testcontainers)
./gradlew :auth-service:test --tests "AuthFlowIT"
```

---

## 11. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| OAuth callback 401 | state 불일치 (Redis 만료) | TTL 확인, 사용자에게 재시도 안내 |
| Stripe Webhook 400 | 서명 검증 실패 (시크릿 환경 불일치) | 환경별 Webhook 시크릿 분리 |
| Refresh Token reuse | 토큰 탈취 또는 클라이언트 동시 요청 | 전 세션 무효화 (03-B.6.3) |
| FCM 401 | 서버 키 만료 | Firebase Console에서 키 재발급 → Secrets Manager 갱신 |
| Audit 토픽 lag 증가 | Audit Service 처리 지연 | Consumer concurrency 증설, batch 크기 확인 |

---

## 12. 참고 문서

- **Wiki 03_프로젝트_아키텍처_정의서** — 시스템 아키텍처 기준선
- **Wiki 02_ERD_문서** — DB 스키마
- **Wiki 04_API_명세서** — REST API 상세
- **03-A_통신_운영_상세서** — gRPC/Kafka 운영
- **03-B_내부외부_경계_보안_명세** — 인증/인가/시크릿
- **03-C_이벤트_스키마_진화_가이드** — Kafka 이벤트 스키마
- [Stripe Webhook Docs](https://stripe.com/docs/webhooks)
- [Spring Boot 4 Reference](https://docs.spring.io/spring-boot/)
