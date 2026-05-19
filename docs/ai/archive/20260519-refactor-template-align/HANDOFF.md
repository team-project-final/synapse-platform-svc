# HANDOFF — 리팩토링: 템플릿 구조 정렬

> FROM: Director (Claude)
> TO: Worker (Codex)
> DATE: 2026-05-19
> BRANCH: refactor/PLAT-REF-001-template-align

---

## 목표

`synapse-platform-svc`의 패키지 구조를 팀 표준 템플릿(`docs/synapse-svc-template-skeleton-platform-w1`)에 맞춰 전면 리팩토링한다.
기능 코드는 변경하지 않는다. 순수 구조 변경만.

---

## 참고 템플릿

`docs/synapse-svc-template-skeleton-platform-w1/` — 목표 구조 레퍼런스.

---

## Phase 1: 패키지 루트 변경

### 작업

1. 모든 `.java` 파일 내 `io.synapse` → `com.synapse` 문자열 치환 (package 선언, import 모두)
2. 디렉토리 이동:
   - `src/main/java/io/synapse/platform/` → `src/main/java/com/synapse/platform/`
   - `src/test/java/io/synapse/platform/` → `src/test/java/com/synapse/platform/`
3. `build.gradle.kts`: `group = "io.synapse"` → `group = "com.synapse"`

### 검증

```
./gradlew compileJava
./gradlew compileTestJava
```

컴파일 오류 0개 확인 후 커밋.

```
git commit -m "refactor(infra): 패키지 루트 io.synapse → com.synapse 변경"
```

---

## Phase 2: `shared/` → `global/` 모듈 이름 변경

### 작업

1. 디렉토리 이동:
   - `src/main/java/com/synapse/platform/shared/` → `src/main/java/com/synapse/platform/global/`
   - `src/test/java/com/synapse/platform/shared/` → `src/test/java/com/synapse/platform/global/`
2. 이동한 파일의 package 선언 변경:
   - `package com.synapse.platform.shared.*` → `package com.synapse.platform.global.*`
3. 프로젝트 전체에서 `com.synapse.platform.shared` import → `com.synapse.platform.global` 변경

### 이동 파일 목록

| 현재 경로 | 목표 경로 |
|-----------|-----------|
| `shared/exception/GlobalExceptionHandler.java` | `global/exception/GlobalExceptionHandler.java` |
| `shared/exception/BusinessException.java` | `global/exception/BusinessException.java` |
| `shared/crypto/FieldEncryptor.java` | `global/crypto/FieldEncryptor.java` |
| `shared/package-info.java` | `global/package-info.java` |
| (test) `shared/crypto/FieldEncryptorTest.java` | `global/crypto/FieldEncryptorTest.java` |
| (test) `shared/exception/GlobalExceptionHandlerTest.java` | `global/exception/GlobalExceptionHandlerTest.java` |

### 검증

```
./gradlew compileJava
```

컴파일 오류 0개 확인 후 커밋.

```
git commit -m "refactor(infra): shared 모듈 → global로 이름 변경 (W2 표준)"
```

---

## Phase 3: `auth` 모듈 — 서브패키지 평탄화

### 목표 구조

```
auth/
├── controller/
│   ├── AuthController.java          ← auth/ 루트에서 이동
│   ├── AuthCallbackController.java  ← auth/ 루트에서 이동
│   └── MfaController.java           ← mfa/에서 이동
├── service/
│   ├── TenantService.java           ← auth/ 루트에서 이동
│   ├── RefreshTokenService.java     ← jwt/에서 이동
│   ├── JwtTokenProvider.java        ← jwt/에서 이동
│   ├── TotpService.java             ← mfa/에서 이동
│   ├── CustomOAuth2UserService.java ← oauth/에서 이동
│   ├── CustomOidcUserService.java   ← oauth/에서 이동
│   ├── OAuth2SuccessHandler.java    ← oauth/에서 이동
│   ├── OAuth2FailureHandler.java    ← oauth/에서 이동
│   └── OAuthUserResolver.java       ← oauth/에서 이동
├── entity/
│   ├── Tenant.java                  ← domain/에서 이동 + 이름 변경
│   ├── TenantMember.java            ← domain/에서 이동
│   ├── TenantMemberId.java          ← domain/에서 이동
│   ├── OAuthIdentity.java           ← domain/에서 이동
│   ├── MfaCredential.java           ← mfa/에서 이동
│   └── RefreshToken.java            ← jwt/에서 이동
├── repository/
│   ├── OAuthIdentityRepository.java ← 유지
│   ├── TenantMemberRepository.java  ← 유지
│   ├── TenantRepository.java        ← 유지
│   ├── MfaCredentialRepository.java ← mfa/에서 이동
│   └── RefreshTokenRepository.java  ← jwt/에서 이동
├── dto/
│   └── OAuthAttributes.java         ← oauth/에서 이동
├── config/
│   ├── SecurityConfig.java          ← 유지
│   ├── CorsConfig.java              ← 유지
│   ├── HttpCookieOAuth2AuthorizationRequestRepository.java ← 유지
│   ├── OAuth2AuthorizationRequestDto.java ← config/에서 유지
│   ├── JwtAuthenticationFilter.java ← jwt/에서 이동
│   └── JwtProperties.java           ← jwt/에서 이동
├── api/                             ← Spring Modulith NamedInterface — 절대 이동 금지
│   ├── TenantApi.java
│   ├── TenantInfo.java
│   └── package-info.java
├── exception/
│   ├── MfaVerificationException.java ← 유지
│   ├── OAuthProcessingException.java ← 유지
│   └── UnauthorizedTokenException.java ← 유지
├── util/
│   └── SlugGenerator.java           ← util/에서 유지
├── AuthRoles.java                   ← 루트에 유지 (상수 클래스)
└── package-info.java
```

### 제거되는 서브패키지

- `auth/jwt/` — 내용물 전부 이동 후 디렉토리 삭제
- `auth/mfa/` — 내용물 전부 이동 후 디렉토리 삭제
- `auth/oauth/` — 내용물 전부 이동 후 디렉토리 삭제
- `auth/domain/` — `auth/entity/`로 이름 변경

### 이동 후 필수 업데이트

각 파일의 package 선언과, 이 파일들을 import하는 **모든 파일**의 import 경로 갱신.

주요 import 변경 예시:
```java
// 변경 전
import com.synapse.platform.auth.jwt.JwtAuthenticationFilter;
import com.synapse.platform.auth.jwt.JwtTokenProvider;
import com.synapse.platform.auth.oauth.CustomOAuth2UserService;
import com.synapse.platform.auth.domain.Tenant;
import com.synapse.platform.auth.mfa.MfaCredential;

// 변경 후
import com.synapse.platform.auth.config.JwtAuthenticationFilter;
import com.synapse.platform.auth.service.JwtTokenProvider;
import com.synapse.platform.auth.service.CustomOAuth2UserService;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.MfaCredential;
```

### 테스트 파일 이동

| 현재 경로 | 목표 경로 |
|-----------|-----------|
| `auth/jwt/JwtAuthenticationFilterTest.java` | `auth/config/JwtAuthenticationFilterTest.java` |
| `auth/jwt/JwtTokenProviderTest.java` | `auth/service/JwtTokenProviderTest.java` |
| `auth/jwt/RefreshTokenServiceTest.java` | `auth/service/RefreshTokenServiceTest.java` |
| `auth/jwt/RefreshTokenTest.java` | `auth/entity/RefreshTokenTest.java` |
| `auth/mfa/MfaControllerTest.java` | `auth/controller/MfaControllerTest.java` |
| `auth/mfa/TotpServiceTest.java` | `auth/service/TotpServiceTest.java` |
| `auth/oauth/CustomOAuth2UserServiceTest.java` | `auth/service/CustomOAuth2UserServiceTest.java` |
| `auth/oauth/CustomOidcUserServiceTest.java` | `auth/service/CustomOidcUserServiceTest.java` |
| `auth/oauth/OAuthSignupRollbackIntegrationTest.java` | `auth/service/OAuthSignupRollbackIntegrationTest.java` |
| `auth/oauth/OAuthUserResolverTest.java` | `auth/service/OAuthUserResolverTest.java` |
| `auth/OAuth2FailureHandlerTest.java` | `auth/service/OAuth2FailureHandlerTest.java` |
| `auth/OAuth2LoginIntegrationTest.java` | `auth/controller/OAuth2LoginIntegrationTest.java` |
| `auth/OAuth2SuccessHandlerTest.java` | `auth/service/OAuth2SuccessHandlerTest.java` |
| `auth/OAuthAttributesTest.java` | `auth/dto/OAuthAttributesTest.java` |
| `auth/SlugGeneratorTest.java` | `auth/util/SlugGeneratorTest.java` |

### 검증

```
./gradlew compileJava
./gradlew test --tests "com.synapse.platform.auth.*"
```

테스트 통과 후 커밋.

```
git commit -m "refactor(auth): jwt/mfa/oauth 서브패키지 평탄화 → controller/service/entity/config"
```

---

## Phase 4: `user` 모듈 구조 정렬

### 목표 구조

```
user/
├── controller/
│   └── UserController.java     ← user/ 루트에서 이동
├── service/
│   └── UserService.java        ← user/ 루트에서 이동
├── entity/
│   ├── User.java               ← domain/에서 이동
│   └── UserSettings.java       ← domain/에서 이동
├── repository/
│   ├── UserRepository.java     ← 유지
│   └── UserSettingsRepository.java ← 유지
├── api/                        ← Spring Modulith NamedInterface — 절대 이동 금지
│   ├── UserApi.java
│   ├── OAuthUserCreateCommand.java
│   ├── UserInfo.java
│   └── package-info.java
└── package-info.java
```

### 제거되는 서브패키지

- `user/domain/` — `user/entity/`로 이름 변경

### 검증

```
./gradlew compileJava
./gradlew test --tests "com.synapse.platform.user.*"
```

커밋:

```
git commit -m "refactor(user): domain → entity, Controller/Service 서브패키지 이동"
```

---

## Phase 5: `billing` 모듈 구조 정렬

### 목표 구조

```
billing/
├── controller/
│   └── BillingController.java    ← billing/ 루트에서 이동
├── service/
│   └── BillingService.java       ← billing/ 루트에서 이동
├── entity/
│   ├── Subscription.java         ← domain/에서 이동
│   ├── SubscriptionStatus.java   ← domain/에서 이동
│   ├── PaymentHistory.java       ← domain/에서 이동
│   ├── PlanCode.java             ← domain/에서 이동
│   └── ProcessedEvent.java       ← domain/에서 이동
├── repository/
│   ├── SubscriptionRepository.java    ← 유지
│   ├── PaymentHistoryRepository.java  ← 유지
│   └── ProcessedEventRepository.java  ← 유지
├── dto/
│   ├── request/
│   │   └── CheckoutSessionRequest.java  ← dto/ flat에서 이동
│   └── response/
│       ├── CheckoutSessionResponse.java ← dto/ flat에서 이동
│       └── SubscriptionResponse.java    ← dto/ flat에서 이동
├── config/
│   ├── StripeConfig.java         ← 유지
│   └── StripeProperties.java     ← 유지
├── exception/
│   └── BillingException.java     ← 유지
└── package-info.java
```

### 제거되는 서브패키지

- `billing/domain/` — `billing/entity/`로 이름 변경
- `billing/dto/` (flat) — `billing/dto/request/` + `billing/dto/response/`로 분리

### DTO 이동 후 import 변경 필수

```java
// 변경 전
import com.synapse.platform.billing.dto.CheckoutSessionRequest;
import com.synapse.platform.billing.dto.CheckoutSessionResponse;
import com.synapse.platform.billing.dto.SubscriptionResponse;

// 변경 후
import com.synapse.platform.billing.dto.request.CheckoutSessionRequest;
import com.synapse.platform.billing.dto.response.CheckoutSessionResponse;
import com.synapse.platform.billing.dto.response.SubscriptionResponse;
```

### 검증

```
./gradlew compileJava
./gradlew test --tests "com.synapse.platform.billing.*"
```

커밋:

```
git commit -m "refactor(billing): domain → entity, DTO request/response 분리, Controller/Service 서브패키지 이동"
```

---

## Phase 6: `admin` / `notification` 모듈 — package-info 패키지 선언 확인

Phase 1에서 이미 내용 치환됨. 디렉토리 이동은 불필요 (Placeholder이므로 루트에 유지).
package-info.java의 package 선언만 `com.synapse.platform.*`로 되어 있는지 확인.

---

## Phase 7: 전체 검증

```
./gradlew clean build
./gradlew test
```

모든 테스트 그린 확인 후 최종 커밋:

```
git commit -m "refactor(infra): 템플릿 기준 전체 패키지 구조 정렬 완료"
```

---

## 절대 건드리지 말 것

| 경로 | 이유 |
|------|------|
| `auth/api/package-info.java` | Spring Modulith `@NamedInterface` — 경로 바뀌면 모듈 경계 깨짐 |
| `user/api/package-info.java` | 동일 |
| `src/main/resources/db/migration/` | Flyway SQL 파일은 패키지와 무관 |
| `src/main/resources/application*.yml` | 패키지 경로 미포함 |

---

## 완료 기준

- [ ] `./gradlew clean build` 통과
- [ ] `./gradlew test` 전체 그린
- [ ] `ApplicationModules.verify()` (`PlatformModuleStructureTest`) 통과
- [ ] 패키지 루트가 `com.synapse.platform`으로 통일됨
- [ ] `global/`, `auth/controller/`, `auth/service/`, `auth/entity/` 등 목표 구조 확인

## 첨부 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-19

---

> **완료**: PR #20 `refactor(infra): 팀 표준 템플릿 기준 패키지/디렉토리 구조 정렬` merge 완료
