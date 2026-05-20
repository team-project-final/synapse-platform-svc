# HANDOFF — W1~W2 완료 기준 점검

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Director (Claude)

## TO

Worker (Codex)

## 배경

W2까지 작업하면서 아래 주요 리팩토링이 있었습니다:
- Gradle 멀티모듈 → Spring Modulith 단일 앱 전환 (D-017)
- 패키지 재편: `shared` → `global`, `audit` 모듈 구조 변경
- 여러 PR에서 반복적인 구조 변경

W3 착수 전, W1~W2 Done When 기준이 현재 코드에서도 충족되는지 항목별로 검증하고 미충족 항목을 수정하세요.

---

## 요청 내용

각 항목을 순서대로 확인하고, 결과를 아래 **결과 기록 섹션**에 OK / NG(+이유)로 채워주세요.
NG 항목은 즉시 수정 후 재검증하세요.

---

### [A] 모듈 구조 무결성

**A-1. ApplicationModulesTest 실행**
```
./gradlew test --tests "*ModuleStructureTest"
```
- 기대: BUILD SUCCESSFUL, 0 violations

**A-2. 각 모듈 package-info.java 존재 확인**
- 확인 대상: `src/main/java/com/synapse/platform/` 하위
  - `auth/package-info.java`
  - `user/package-info.java`
  - `billing/package-info.java`
  - `notification/package-info.java`
  - `global/package-info.java`
  - `admin/package-info.java`
- 없는 파일은 `@ApplicationModule` 어노테이션 포함하여 생성

---

### [B] Step 2 Done When 검증 — OAuth

**B-1. OAuth 3종 설정 확인**
- `application.yml` (또는 `application-*.yml`)에서 Google / GitHub / Apple OAuth2 Client 설정 존재 확인
- Apple은 OIDC 방식으로 구현되어 있음 (CustomOidcUserService)

**B-2. oauth_identities 테이블 마이그레이션 확인**
- Flyway 파일(V1~V3 중)에 `oauth_identities` 테이블 DDL 존재 확인
- 컬럼: `id, user_id, provider, provider_id, access_token_enc, created_at`
- `provider + provider_id` UNIQUE 제약 존재 확인

**B-3. 신규 가입 시 테넌트 자동 생성 로직 확인**
- `OAuthUserResolver` 또는 `CustomOAuth2UserService`에서 신규 유저 생성 시 테넌트도 함께 생성하는 코드 확인

**B-4. OAuth 통합 테스트 실행**
```
./gradlew test --tests "*OAuth2LoginIntegrationTest"
./gradlew test --tests "*CustomOAuth2UserServiceTest"
./gradlew test --tests "*CustomOidcUserServiceTest"
```

---

### [C] Step 3 Done When 검증 — JWT/MFA

**C-1. Access Token 만료 시간 확인**
- `JwtTokenProvider` 또는 `JwtProperties`에서 Access Token TTL = 15분(900초 또는 PT15M) 확인

**C-2. Refresh Token 만료 시간 확인**
- Refresh Token TTL = 7일 확인 (Redis TTL + DB `expires_at` 계산)

**C-3. Refresh Token 이중 저장 확인**
- `RefreshTokenService`에서 DB 저장 (`refresh_tokens` 테이블) + Redis 저장 모두 존재 확인
- `V21__create_refresh_tokens.sql` 존재 확인

**C-4. 엔드포인트 3개 존재 확인**
- `POST /api/v1/auth/refresh` — AuthController
- `POST /api/v1/auth/mfa/setup` — MfaController
- `POST /api/v1/auth/mfa/verify` — MfaController

**C-5. JWT/MFA 테스트 실행**
```
./gradlew test --tests "*JwtTokenProviderTest"
./gradlew test --tests "*RefreshTokenServiceTest"
./gradlew test --tests "*TotpServiceTest"
./gradlew test --tests "*MfaControllerTest"
```

---

### [D] Step 4 Done When 검증 — Stripe

**D-1. 엔드포인트 3개 존재 확인**
- `POST /api/v1/billing/checkout`
- `POST /api/v1/billing/webhooks`
- `GET /api/v1/billing/subscription`

**D-2. Webhook 서명 검증 로직 확인**
- `BillingService` 또는 `BillingController`에서 Stripe Webhook 서명 검증 코드 존재 확인

**D-3. processed_events 멱등성 테이블 확인**
- `V26__create_processed_events.sql` 존재 확인
- `BillingService`에서 동일 이벤트 중복 처리 방지 로직 존재 확인

**D-4. subscriptions / payment_history 마이그레이션 확인**
- `V24__create_subscriptions.sql`, `V25__create_payment_history.sql` 존재 확인

**D-5. Stripe 테스트 실행**
```
./gradlew test --tests "*BillingServiceTest"
./gradlew test --tests "*BillingControllerTest"
./gradlew test --tests "*BillingSecurityIntegrationTest"
```

---

### [E] Step 5 Done When 검증 — FCM 디바이스

**E-1. 엔드포인트 2개 존재 확인**
- `POST /api/v1/notifications/devices` → 201 반환
- `DELETE /api/v1/notifications/devices/{id}` → 204 반환

**E-2. device_tokens 테이블 스키마 확인**
- `V27__create_device_tokens.sql`에서 아래 항목 확인:
  - `platform` 컬럼: `ios`, `android`, `web` (소문자) CHECK 제약
  - `is_active` 컬럼 존재
  - `token` UNIQUE 제약
- `DeviceTokenService`에서 5개 초과 등록 방지 로직 존재 확인

**E-3. Notification 테스트 실행**
```
./gradlew test --tests "*DeviceTokenIntegrationTest"
./gradlew test --tests "*DeviceTokenServiceTest"
```

---

### [F] 전체 빌드 + 커버리지

**F-1. 전체 check 실행**
```
./gradlew check
```
- 기대: BUILD SUCCESSFUL (checkstyle, spotbugs, test 모두 통과)

**F-2. JaCoCo 커버리지 확인**
```
./gradlew jacocoTestReport
```
- `build/reports/jacoco/test/html/index.html` 라인 커버리지 확인
- 기준: 80% 이상

---

## 필요한 출력 형식

아래 결과 테이블을 채워서 Director에게 전달하세요.

| 항목 | 결과 | 비고 |
|------|------|------|
| A-1. ModuleStructureTest | OK | `./gradlew test --tests "*ModuleStructureTest"` BUILD SUCCESSFUL |
| A-2. package-info.java 전체 | OK | auth, user, billing, notification, global, admin 모두 `@ApplicationModule` 선언 |
| B-1. OAuth 3종 설정 | OK | `application.yml`에 Google/GitHub/Apple OAuth2 Client 설정 존재, Apple provider OIDC 설정 존재 |
| B-2. oauth_identities DDL | NG → OK | 최초 확인 시 `provider_user_id` 사용 및 V3 DDL 내 `access_token_enc` 누락. `provider_id`, `access_token_enc`, UNIQUE(provider, provider_id)로 수정 후 `OAuthIdentitySchemaTest` 통과 |
| B-3. 테넌트 자동 생성 로직 | OK | `OAuthUserResolver.signUp()`에서 tenant 생성, OAuth user 생성, tenant owner 저장 확인 |
| B-4. OAuth 통합 테스트 | OK | `OAuth2LoginIntegrationTest`, `CustomOAuth2UserServiceTest`, `CustomOidcUserServiceTest`, `OAuthIdentitySchemaTest` BUILD SUCCESSFUL |
| C-1. Access Token 15분 | OK | `JwtTokenProvider.ACCESS_TOKEN_TTL_SECONDS = 15 * 60` |
| C-2. Refresh Token 7일 | OK | `JwtTokenProvider` refresh TTL 7일, `RefreshTokenService.REFRESH_TOKEN_TTL = Duration.ofDays(7)` |
| C-3. Refresh Token 이중 저장 | OK | `RefreshTokenService` DB 저장 + Redis 저장 확인, `V21__create_refresh_tokens.sql` 존재 |
| C-4. JWT/MFA 엔드포인트 3개 | OK | `POST /api/v1/auth/refresh`, `POST /api/v1/auth/mfa/setup`, `POST /api/v1/auth/mfa/verify` 존재 |
| C-5. JWT/MFA 테스트 | OK | `JwtTokenProviderTest`, `RefreshTokenServiceTest`, `TotpServiceTest`, `MfaControllerTest` BUILD SUCCESSFUL |
| D-1. Billing 엔드포인트 3개 | OK | `POST /api/v1/billing/checkout`, `POST /api/v1/billing/webhooks`, `GET /api/v1/billing/subscription` 존재 |
| D-2. Webhook 서명 검증 | OK | `BillingService.handleWebhook()`에서 `Webhook.constructEvent()`로 Stripe 서명 검증 |
| D-3. processed_events 멱등성 | OK | `V26__create_processed_events.sql` 존재, `insertIfAbsent()` 결과 0이면 중복 처리 중단 |
| D-4. subscriptions/payment_history DDL | OK | `V24__create_subscriptions.sql`, `V25__create_payment_history.sql` 존재 |
| D-5. Stripe 테스트 | OK | `BillingServiceTest`, `BillingControllerTest`, `BillingSecurityIntegrationTest` BUILD SUCCESSFUL |
| E-1. Notification 엔드포인트 2개 | OK | `POST /api/v1/notifications/devices` 201, `DELETE /api/v1/notifications/devices/{id}` 204 반환 코드 확인 |
| E-2. device_tokens 스키마 | OK | `V27__create_device_tokens.sql`에 platform CHECK(`ios`, `android`, `web`), `is_active`, token UNIQUE 존재. `DeviceTokenService` 5개 초과 방지 확인 |
| E-3. Notification 테스트 | OK | `DeviceTokenIntegrationTest`, `DeviceTokenServiceTest` BUILD SUCCESSFUL |
| F-1. `./gradlew check` | OK | BUILD SUCCESSFUL |
| F-2. JaCoCo 80% 이상 | OK | `./gradlew jacocoTestReport` BUILD SUCCESSFUL, LINE 92.38% (921/997) |

NG 항목이 있었다면 수정 내용도 함께 요약해주세요.
최종 `./gradlew check` 결과 (BUILD SUCCESSFUL 또는 FAILED + 오류 메시지)를 포함해주세요.

## 수정 내용 요약

- `oauth_identities` DDL을 현재 완료 기준에 맞춰 `provider_id`, `access_token_enc`, UNIQUE(provider, provider_id) 구조로 보정했습니다.
- `OAuthIdentity` JPA 매핑 컬럼명을 `provider_id`로 맞췄습니다.
- 위 스키마 기준을 고정하는 `OAuthIdentitySchemaTest`를 추가했습니다.

## 실행한 검증

- `./gradlew test --tests "*ModuleStructureTest"` → BUILD SUCCESSFUL
- `./gradlew test --tests "*OAuth2LoginIntegrationTest" --tests "*CustomOAuth2UserServiceTest" --tests "*CustomOidcUserServiceTest" --tests "*OAuthIdentitySchemaTest"` → BUILD SUCCESSFUL
- `./gradlew test --tests "*JwtTokenProviderTest" --tests "*RefreshTokenServiceTest" --tests "*TotpServiceTest" --tests "*MfaControllerTest"` → 최초 Docker daemon 미실행으로 실패, Docker Desktop 시작 후 BUILD SUCCESSFUL
- `./gradlew test --tests "*BillingServiceTest" --tests "*BillingControllerTest" --tests "*BillingSecurityIntegrationTest"` → BUILD SUCCESSFUL
- `./gradlew test --tests "*DeviceTokenIntegrationTest" --tests "*DeviceTokenServiceTest"` → BUILD SUCCESSFUL
- `./gradlew check` → BUILD SUCCESSFUL
- `./gradlew jacocoTestReport` → BUILD SUCCESSFUL, LINE 92.38%

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-20
