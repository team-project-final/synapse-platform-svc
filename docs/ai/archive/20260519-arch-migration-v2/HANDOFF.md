# HANDOFF — Arch Migration: Gradle 멀티모듈 → Spring Modulith

**TO**: Worker (Codex)
**FROM**: Director (Claude)
**DATE**: 2026-05-19 (v2 — Worker 점검 보정)
**BRANCH**: `feature/PLAT-004-stripe-billing` (현재 브랜치 그대로 사용)

---

## 작업 목표

commit #15로 만들어진 Gradle 멀티모듈 구조를
`docs/synapse-platform-svc_ARCHITECTURE_v2.md` 기준 **Spring Modulith 단일 앱**으로 전환한다.

**핵심 규칙**:
- 기능 변경 없음 — 코드 이동 + 패키지 rename + UserApi 인터페이스 도입
- 이동 완료 후 구 모듈 디렉토리 삭제
- `./gradlew test` 전체 통과 확인 후 종료

---

## Phase A — Gradle 빌드 재구성

### 1. `settings.gradle.kts` 교체

```kotlin
rootProject.name = "synapse-platform-svc"
```

(서브모듈 `include(...)` 전체 제거)

### 2. 루트 `build.gradle.kts` 전면 교체

> **[D-018]** Spring Modulith 2.0.6 — Boot 4.0 호환 확정 버전.

```kotlin
plugins {
    java
    checkstyle
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.0.9"
}

group = "io.synapse"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.6")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")   // ← web 아님, webmvc
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // Persistence
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // MFA
    implementation("dev.samstevens.totp:totp:1.7.1")

    // Utils
    implementation("com.github.f4b6a3:uuid-creator:5.3.3")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

configure<org.gradle.api.plugins.quality.CheckstyleExtension> {
    toolVersion = "10.12.5"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}
```

---

## Phase B — 루트 src/ 구조 생성

### 1. 디렉토리 생성

```
src/
├── main/
│   ├── java/io/synapse/platform/
│   │   ├── auth/
│   │   ├── user/
│   │   │   └── api/
│   │   ├── notification/
│   │   ├── admin/
│   │   └── shared/
│   └── resources/
│       └── db/migration/
└── test/
    ├── java/io/synapse/platform/
    └── resources/
```

### 2. `PlatformApplication.java`

경로: `src/main/java/io/synapse/platform/PlatformApplication.java`

```java
package io.synapse.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
```

### 3. 각 모듈 `package-info.java` 생성

`src/main/java/io/synapse/platform/auth/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule
package io.synapse.platform.auth;
```

`src/main/java/io/synapse/platform/user/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule
package io.synapse.platform.user;
```

`src/main/java/io/synapse/platform/notification/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule
package io.synapse.platform.notification;
```

`src/main/java/io/synapse/platform/admin/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule
package io.synapse.platform.admin;
```

`src/main/java/io/synapse/platform/shared/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.synapse.platform.shared;
```

> `shared`는 OPEN 타입 — 모든 모듈이 자유롭게 import 가능.

### 4. **[D-019]** `user/api/package-info.java` — @NamedInterface 필수

`src/main/java/io/synapse/platform/user/api/package-info.java`:
```java
@org.springframework.modulith.NamedInterface("api")
package io.synapse.platform.user.api;
```

> Modulith 기본 규칙: subpackage는 internal. `user.api`를 auth 모듈이 참조하려면 이 어노테이션이 필수.

### 5. 리소스 파일 복사 (auth-service → 루트)

복사 대상:
- `auth-service/src/main/resources/application.yml` → `src/main/resources/application.yml`
- `auth-service/src/main/resources/application-local.yml` → `src/main/resources/application-local.yml`
- `auth-service/src/main/resources/application-dev.yml` → `src/main/resources/application-dev.yml`
- `auth-service/src/main/resources/application-prod.yml` → `src/main/resources/application-prod.yml`
- `auth-service/src/main/resources/logback-spring.xml` → `src/main/resources/logback-spring.xml`
- `auth-service/src/main/resources/db/migration/V*.sql` → `src/main/resources/db/migration/` (전체, gap 있는 번호 그대로)
- `auth-service/src/test/resources/application.yml` → `src/test/resources/application.yml`

---

## Phase C — auth 모듈 코드 이동

`auth-service/src/main/java/io/synapse/platform/auth/` 하위 파일을
`src/main/java/io/synapse/platform/auth/` 로 **그대로** 이동.

**단, 아래는 Phase D에서 처리하므로 Phase C에서 제외**:
- `auth/user/` 하위 전체 → Phase D에서 처리

이동할 파일 목록 (패키지 구조 유지):
```
AuthCallbackController.java
AuthController.java
AuthRoles.java
config/CorsConfig.java
config/HttpCookieOAuth2AuthorizationRequestRepository.java
config/OAuth2AuthorizationRequestDto.java
config/SecurityConfig.java
domain/OAuthIdentity.java
domain/Tenant.java
domain/TenantMember.java
domain/TenantMemberId.java
exception/MfaVerificationException.java
exception/OAuthProcessingException.java
exception/UnauthorizedTokenException.java
jwt/JwtAuthenticationFilter.java
jwt/JwtProperties.java
jwt/JwtTokenProvider.java
jwt/RefreshToken.java
jwt/RefreshTokenRepository.java
jwt/RefreshTokenService.java
mfa/MfaController.java
mfa/MfaCredential.java
mfa/MfaCredentialRepository.java
mfa/TotpService.java
oauth/CustomOAuth2UserService.java
oauth/CustomOidcUserService.java
oauth/OAuth2FailureHandler.java
oauth/OAuth2SuccessHandler.java
oauth/OAuthAttributes.java
oauth/OAuthUserResolver.java
repository/OAuthIdentityRepository.java
repository/TenantMemberRepository.java
repository/TenantRepository.java
util/SlugGenerator.java
```

> `AuthServiceApplication.java`는 이동 대상 **아님** — `PlatformApplication.java`로 대체됨.

---

## Phase D — user 모듈 분리 + UserApi 설계 [D-019]

### 1. user.api 타입 생성

> **핵심**: `user.domain.User` 엔티티는 외부 모듈에 노출하지 않는다.
> `UserInfo` DTO와 `OAuthUserCreateCommand`를 `user.api` 패키지에 두어 경계를 명확히 한다.

`src/main/java/io/synapse/platform/user/api/UserInfo.java`:
```java
package io.synapse.platform.user.api;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String displayName,
        UUID defaultTenantId
) {}
```

`src/main/java/io/synapse/platform/user/api/OAuthUserCreateCommand.java`:
```java
package io.synapse.platform.user.api;

import java.util.UUID;

public record OAuthUserCreateCommand(
        String email,
        String slug,
        String displayName,
        String avatarUrl,
        UUID defaultTenantId
) {}
```

`src/main/java/io/synapse/platform/user/api/UserApi.java`:
```java
package io.synapse.platform.user.api;

import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserInfo> findById(UUID userId);
    Optional<UserInfo> findByEmail(String email);
    // OAuth 신규 가입: User + UserSettings 동시 생성
    UserInfo createForOAuth(OAuthUserCreateCommand command);
}
```

### 2. user 모듈 파일 이동 + 패키지 변경

`auth-service/src/main/java/io/synapse/platform/auth/user/` → `src/main/java/io/synapse/platform/user/`

이동 후 패키지 선언 변경:
- `io.synapse.platform.auth.user` → `io.synapse.platform.user`
- `io.synapse.platform.auth.user.domain` → `io.synapse.platform.user.domain`
- `io.synapse.platform.auth.user.repository` → `io.synapse.platform.user.repository`

```
user/UserController.java          (package: io.synapse.platform.user)
user/UserService.java             (package: io.synapse.platform.user)
user/domain/User.java             (package: io.synapse.platform.user.domain)
user/domain/UserSettings.java     (package: io.synapse.platform.user.domain)
user/repository/UserRepository.java         (package: io.synapse.platform.user.repository)
user/repository/UserSettingsRepository.java (package: io.synapse.platform.user.repository)
```

### 3. `UserService`가 `UserApi` 구현 + `createForOAuth` 추가

`UserService.java` 변경:

```java
package io.synapse.platform.user;

import io.synapse.platform.user.api.OAuthUserCreateCommand;
import io.synapse.platform.user.api.UserApi;
import io.synapse.platform.user.api.UserInfo;
import io.synapse.platform.user.domain.User;
import io.synapse.platform.user.domain.UserSettings;
import io.synapse.platform.user.repository.UserRepository;
import io.synapse.platform.user.repository.UserSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserApi {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public UserService(UserRepository userRepository, UserSettingsRepository userSettingsRepository) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    @Override
    public Optional<UserInfo> findById(UUID userId) {
        return userRepository.findById(userId).map(this::toUserInfo);
    }

    @Override
    public Optional<UserInfo> findByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toUserInfo);
    }

    @Override
    @Transactional
    public UserInfo createForOAuth(OAuthUserCreateCommand command) {
        User user = User.ofOAuth(
                command.email(),
                command.slug(),
                command.displayName(),
                command.avatarUrl());
        user.updateDefaultTenantId(command.defaultTenantId());
        User saved = userRepository.save(user);
        userSettingsRepository.save(UserSettings.defaultFor(saved.getId()));
        return toUserInfo(saved);
    }

    private UserInfo toUserInfo(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getDisplayName(), user.getDefaultTenantId());
    }
}
```

### 4. auth 모듈 OAuth 클래스 수정

**`OAuthUserResolver.java`** — `UserRepository`, `UserSettingsRepository` 직접 참조 제거, `UserApi`로 교체:

```java
// 변경 전 필드
private final UserRepository userRepository;
private final UserSettingsRepository userSettingsRepository;

// 변경 후 필드
private final UserApi userApi;   // io.synapse.platform.user.api.UserApi
```

`resolveUser()` 변경:
```java
// case 1: identity 존재 → User 조회
// 변경 전: return userRepository.findById(existing.getUserId()).orElseThrow();
// 변경 후:
return userApi.findById(existing.getUserId())
        .orElseThrow(() -> new IllegalStateException("User not found"));

// case 2: 이메일 일치 → OAuthIdentity 연결, User 반환
// 변경 전: Optional<User> existingUser = userRepository.findByEmail(...)
// 변경 후: Optional<UserInfo> existingUser = userApi.findByEmail(...)
// 이후 existingUser.get().id() 사용
```

`signUp()` 변경 — User/UserSettings 직접 생성 제거:
```java
private UserInfo signUp(OAuthAttributes attributes, String accessTokenEnc) {
    String displayName = displayName(attributes);
    String email = email(attributes, displayName);
    String slug = slugGenerator.generate(email);

    // Tenant 생성은 auth 모듈 책임 유지
    Tenant tenant = tenantRepository.save(Tenant.ofPersonal(displayName, slug));

    // User + UserSettings 생성은 user 모듈에 위임
    OAuthUserCreateCommand cmd = new OAuthUserCreateCommand(
            email, slug, displayName, attributes.avatarUrl(), tenant.getId());
    UserInfo savedUser = userApi.createForOAuth(cmd);

    // OAuthIdentity, TenantMember는 auth 모듈 책임 유지
    oauthIdentityRepository.save(OAuthIdentity.of(
            savedUser.id(), attributes.provider(), attributes.providerId(),
            attributes.email(), accessTokenEnc));
    tenantMemberRepository.save(TenantMember.ofOwner(tenant.getId(), savedUser.id()));

    return savedUser;
}
```

`resolveUser()` 반환 타입도 `User` → `UserInfo`로 변경. 호출부(`CustomOAuth2UserService`, `CustomOidcUserService`, `OAuth2SuccessHandler`) 도 `UserInfo`로 맞춤.

**`TotpService.java`** — `UserRepository` 직접 참조 제거:

```java
// 변경 전
private final UserRepository userRepository;
// setup()에서: User user = userRepository.findById(userId).orElseThrow(...)
//              user.getEmail() 사용

// 변경 후
private final UserApi userApi;
// setup()에서: UserInfo user = userApi.findById(userId).orElseThrow(...)
//              user.email() 사용 (record accessor)
```

---

## Phase E — shared 모듈 생성 (platform-common 이동)

### 1. 3개 파일 이동 + 패키지 rename

| 원본 | 목적 | 패키지 |
|------|------|--------|
| `platform-common/src/.../common/crypto/FieldEncryptor.java` | `src/main/java/io/synapse/platform/shared/crypto/FieldEncryptor.java` | `shared.crypto` |
| `platform-common/src/.../common/exception/BusinessException.java` | `src/main/java/io/synapse/platform/shared/exception/BusinessException.java` | `shared.exception` |
| `platform-common/src/.../common/exception/GlobalExceptionHandler.java` | `src/main/java/io/synapse/platform/shared/exception/GlobalExceptionHandler.java` | `shared.exception` |

### 2. import 전수 교체

```
io.synapse.platform.common.crypto.FieldEncryptor
→ io.synapse.platform.shared.crypto.FieldEncryptor

io.synapse.platform.common.exception.BusinessException
→ io.synapse.platform.shared.exception.BusinessException

io.synapse.platform.common.exception.GlobalExceptionHandler
→ io.synapse.platform.shared.exception.GlobalExceptionHandler
```

영향 파일: `auth/mfa/TotpService.java`, `auth/oauth/OAuthUserResolver.java` 및 FieldEncryptor 참조 클래스 전체.

### 3. platform-common 테스트 이동 + 패키지 rename

| 원본 | 목적 | 패키지 |
|------|------|--------|
| `platform-common/src/test/.../common/crypto/FieldEncryptorTest.java` | `src/test/.../shared/crypto/FieldEncryptorTest.java` | `shared.crypto` |
| `platform-common/src/test/.../common/exception/GlobalExceptionHandlerTest.java` | `src/test/.../shared/exception/GlobalExceptionHandlerTest.java` | `shared.exception` |

---

## Phase F — admin/notification placeholder 생성

`src/main/java/io/synapse/platform/admin/AdminPlaceholder.java`:
```java
package io.synapse.platform.admin;

import org.springframework.stereotype.Component;

@Component
class AdminPlaceholder {
    // Audit Log + Kafka Consumer — Step 6에서 구현
}
```

`src/main/java/io/synapse/platform/notification/NotificationPlaceholder.java`:
```java
package io.synapse.platform.notification;

import org.springframework.stereotype.Component;

@Component
class NotificationPlaceholder {
    // FCM + SES — Step 7에서 구현
}
```

---

## Phase G — 테스트 파일 이동 + ModuleStructureTest

### 1. auth-service 테스트 전체 이동

`auth-service/src/test/java/io/synapse/platform/auth/` →
`src/test/java/io/synapse/platform/auth/` (패키지 선언 변경 없음)

### 2. `AuthServiceApplicationTests.java` → `PlatformApplicationTests.java`

`src/test/java/io/synapse/platform/PlatformApplicationTests.java`:
```java
package io.synapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PlatformApplicationTests {
    @Test
    void contextLoads() {}
}
```

### 3. `PlatformModuleStructureTest.java` 생성

`src/test/java/io/synapse/platform/PlatformModuleStructureTest.java`:
```java
package io.synapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class PlatformModuleStructureTest {

    ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);

    @Test
    void modulesAreCompliant() {
        modules.verify();
    }
}
```

---

## Phase H — 구 모듈 디렉토리 삭제

`./gradlew test` 전체 통과 후:

```bash
rm -rf auth-service/
rm -rf billing-service/
rm -rf audit-service/
rm -rf notification-service/
rm -rf platform-common/
```

---

## Phase I — docker compose 로컬 기동 보정 [리뷰 후속]

### 배경

리뷰 결과 `docker compose up` 완료 조건이 아직 엄밀히 충족되지 않는다.

현재 `docker-compose.yml`의 `platform-svc.environment`는 DB/Redis만 주입한다. 하지만 `src/main/resources/application.yml`은 아래 필수 환경변수를 요구한다.

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`
- `APPLE_CLIENT_ID`
- `APPLE_CLIENT_SECRET`
- `AES_SECRET_KEY`

또한 JWT 키는 `application-local.yml`, `application-dev.yml`, `application-prod.yml`에서 `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`를 요구한다. compose에서 앱을 local profile로 띄울 경우 JWT 키도 준비되어야 한다.

### 작업 목표

`docker compose up`을 실행하는 개발자가 어떤 env를 준비해야 하는지 명확히 하고, secret 원문을 커밋하지 않는 방식으로 compose 기동 경로를 완성한다.

### 1. `.env.example` 생성

경로: `.env.example`

```dotenv
SPRING_PROFILES_ACTIVE=local

GOOGLE_CLIENT_ID=local-google-client-id
GOOGLE_CLIENT_SECRET=local-google-client-secret
GITHUB_CLIENT_ID=local-github-client-id
GITHUB_CLIENT_SECRET=local-github-client-secret
APPLE_CLIENT_ID=local-apple-client-id
APPLE_CLIENT_SECRET=local-apple-client-secret

AES_SECRET_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=

JWT_PRIVATE_KEY=replace-with-base64-pkcs8-private-key
JWT_PUBLIC_KEY=replace-with-base64-x509-public-key
```

> 실제 secret은 `.env`에만 작성하고 커밋하지 않는다.

### 2. `.gitignore`에 `.env` 추가

경로: `.gitignore`

```gitignore
.env
.env.local
```

### 3. `docker-compose.yml`에 env 파일 연결

`platform-svc` 서비스에 아래를 추가:

```yaml
    env_file:
      - .env
```

`SPRING_PROFILES_ACTIVE`는 `.env`에서 주입한다.

### 4. 검증

```bash
docker compose config
docker compose up --build
```

성공 기준:
- `platform-svc`가 `8081`로 기동
- `/actuator/health`가 응답
- secret 원문이 git diff에 포함되지 않음

---

## 주의사항 요약

| 항목 | 내용 |
|------|------|
| Modulith BOM 버전 | Boot 4.0 호환 버전 공식 호환표 확인 필수 (D-018) |
| `spring-boot-starter-web` | `webmvc` 사용 — auth-service 현행과 동일 |
| `checkstyle` | plugins 블록에 반드시 추가 |
| `user.api` 패키지 | `@NamedInterface("api")` 없으면 auth에서 접근 불가 (D-019) |
| `UserInfo` 반환 | `User` 엔티티를 외부 모듈에 절대 노출하지 않음 |
| `io.synapse.platform.common.*` | 이동 후 단 하나의 import도 남으면 안 됨 |
| Flyway 파일 | V 번호 gap(V4~V15 없음) 그대로 유지 — Flyway가 gap 허용 |
| 테스트 통과 기준 | `modules.verify()` + 기존 auth 테스트 전체 통과 |
| compose 기동 | `.env.example` + `.env` 기반으로 필수 env 주입 경로를 명확히 할 것 |
