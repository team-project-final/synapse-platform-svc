# Arch Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the current Gradle multi-module repository back into a single Spring Boot 4.0.0 + Spring Modulith 2.0.6 application under `src/`.

**Architecture:** The root package is `io.synapse.platform`. Direct subpackages `auth`, `user`, `notification`, `admin`, and `shared` are Spring Modulith modules. Cross-module access from `auth` to `user` goes through `user.api` only, exposed with `@NamedInterface("api")`.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Spring Boot 4.0.0, Spring Modulith 2.0.6, Spring Security OAuth2 Client, Spring Data JPA, Flyway, Redis, jjwt 0.12.6, JUnit 5.

---

## File Map

- Modify: `settings.gradle.kts` — remove all `include(...)` entries.
- Replace: `build.gradle.kts` — single Spring Boot application build with Modulith 2.0.6.
- Create: `src/main/java/io/synapse/platform/PlatformApplication.java` — single app entrypoint.
- Create: `src/main/java/io/synapse/platform/{auth,user,notification,admin,shared}/package-info.java` — Modulith module declarations.
- Create: `src/main/java/io/synapse/platform/user/api/package-info.java` — named interface for user API.
- Create: `src/main/java/io/synapse/platform/user/api/UserInfo.java` — external user DTO.
- Create: `src/main/java/io/synapse/platform/user/api/OAuthUserCreateCommand.java` — OAuth user creation command.
- Create: `src/main/java/io/synapse/platform/user/api/UserApi.java` — cross-module user API.
- Move: `auth-service/src/main/java/io/synapse/platform/auth/**` except `auth/user/**` to `src/main/java/io/synapse/platform/auth/**`.
- Move: `auth-service/src/main/java/io/synapse/platform/auth/user/**` to `src/main/java/io/synapse/platform/user/**`.
- Move: `platform-common/src/main/java/io/synapse/platform/common/**` to `src/main/java/io/synapse/platform/shared/**`.
- Move: `auth-service/src/main/resources/**` to `src/main/resources/**`.
- Move: `auth-service/src/test/resources/application.yml` to `src/test/resources/application.yml`.
- Move: `auth-service/src/test/java/io/synapse/platform/auth/**` to `src/test/java/io/synapse/platform/auth/**`.
- Move: `platform-common/src/test/java/io/synapse/platform/common/**` to `src/test/java/io/synapse/platform/shared/**`.
- Create: `src/test/java/io/synapse/platform/PlatformApplicationTests.java`.
- Create: `src/test/java/io/synapse/platform/PlatformModuleStructureTest.java`.
- Delete after tests pass: `auth-service/`, `billing-service/`, `audit-service/`, `notification-service/`, `platform-common/`.

---

## Task 1: Baseline And Build Files

**Files:**
- Modify: `settings.gradle.kts`
- Replace: `build.gradle.kts`

- [ ] **Step 1: Capture current state**

Run:
```powershell
git status --short --branch
rg --files auth-service platform-common | Measure-Object
```

Expected:
- Branch is `feature/PLAT-004-stripe-billing`.
- Only docs are dirty before implementation.

- [ ] **Step 2: Replace `settings.gradle.kts`**

Set the full file to:
```kotlin
rootProject.name = "synapse-platform-svc"
```

- [ ] **Step 3: Replace root `build.gradle.kts`**

Use the exact build from `docs/ai/current/HANDOFF.md` Phase A:
- Spring Boot plugin `4.0.0`
- dependency-management plugin `1.1.7`
- SpotBugs plugin `6.0.9`
- `checkstyle`
- Spring Modulith BOM `2.0.6`
- `spring-boot-starter-webmvc`
- `spring-modulith-starter-core`
- `spring-modulith-starter-test`

- [ ] **Step 4: Verify Gradle file parses**

Run:
```powershell
.\gradlew.bat tasks --no-daemon
```

Expected:
- Gradle lists root project tasks.
- If dependency resolution fails due network sandboxing, rerun with escalated network permission.

---

## Task 2: Scaffold Single Application Structure

**Files:**
- Create: `src/main/java/io/synapse/platform/PlatformApplication.java`
- Create: `src/main/java/io/synapse/platform/auth/package-info.java`
- Create: `src/main/java/io/synapse/platform/user/package-info.java`
- Create: `src/main/java/io/synapse/platform/user/api/package-info.java`
- Create: `src/main/java/io/synapse/platform/notification/package-info.java`
- Create: `src/main/java/io/synapse/platform/admin/package-info.java`
- Create: `src/main/java/io/synapse/platform/shared/package-info.java`

- [ ] **Step 1: Create `PlatformApplication.java`**

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

- [ ] **Step 2: Create module declarations**

Closed modules:
```java
@org.springframework.modulith.ApplicationModule
package io.synapse.platform.auth;
```

Repeat the same pattern for:
- `io.synapse.platform.user`
- `io.synapse.platform.notification`
- `io.synapse.platform.admin`

Open shared module:
```java
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.synapse.platform.shared;
```

Named user API:
```java
@org.springframework.modulith.NamedInterface("api")
package io.synapse.platform.user.api;
```

- [ ] **Step 3: Verify package files exist**

Run:
```powershell
rg --files src\main\java\io\synapse\platform | rg "package-info.java|PlatformApplication.java"
```

Expected:
- Seven files: app entrypoint, five module `package-info.java` files, and `user/api/package-info.java`.

---

## Task 3: Move Resources

**Files:**
- Create/move: `src/main/resources/application.yml`
- Create/move: `src/main/resources/application-local.yml`
- Create/move: `src/main/resources/application-dev.yml`
- Create/move: `src/main/resources/application-prod.yml`
- Create/move: `src/main/resources/logback-spring.xml`
- Create/move: `src/main/resources/db/migration/V*.sql`
- Create/move: `src/test/resources/application.yml`

- [ ] **Step 1: Copy resource files**

Copy all files from `auth-service/src/main/resources/` to `src/main/resources/`, preserving `db/migration`.

- [ ] **Step 2: Copy test resource file**

Copy `auth-service/src/test/resources/application.yml` to `src/test/resources/application.yml`.

- [ ] **Step 3: Verify resource count**

Run:
```powershell
Get-ChildItem -LiteralPath src\main\resources\db\migration -Filter V*.sql | Measure-Object
Test-Path src\main\resources\application.yml
Test-Path src\test\resources\application.yml
```

Expected:
- Migration count equals the source count from `auth-service/src/main/resources/db/migration`.
- Both `Test-Path` checks return `True`.

---

## Task 4: Move Auth Module Code

**Files:**
- Move: `auth-service/src/main/java/io/synapse/platform/auth/**`
- Exclude: `auth-service/src/main/java/io/synapse/platform/auth/AuthServiceApplication.java`
- Exclude for Task 5: `auth-service/src/main/java/io/synapse/platform/auth/user/**`

- [ ] **Step 1: Move auth files except app entrypoint and user package**

Move all listed Phase C files from HANDOFF into `src/main/java/io/synapse/platform/auth/`, preserving subdirectories.

- [ ] **Step 2: Verify excluded files**

Run:
```powershell
Test-Path src\main\java\io\synapse\platform\auth\AuthServiceApplication.java
Test-Path src\main\java\io\synapse\platform\auth\user
```

Expected:
- Both return `False`.

- [ ] **Step 3: Verify auth module files exist**

Run:
```powershell
rg --files src\main\java\io\synapse\platform\auth | Measure-Object
```

Expected:
- Count includes all moved auth files and `auth/package-info.java`.

---

## Task 5: Split User Module And Add User API

**Files:**
- Move/modify: `src/main/java/io/synapse/platform/user/UserController.java`
- Replace: `src/main/java/io/synapse/platform/user/UserService.java`
- Move/modify: `src/main/java/io/synapse/platform/user/domain/User.java`
- Move/modify: `src/main/java/io/synapse/platform/user/domain/UserSettings.java`
- Move/modify: `src/main/java/io/synapse/platform/user/repository/UserRepository.java`
- Move/modify: `src/main/java/io/synapse/platform/user/repository/UserSettingsRepository.java`
- Create: `src/main/java/io/synapse/platform/user/api/UserInfo.java`
- Create: `src/main/java/io/synapse/platform/user/api/OAuthUserCreateCommand.java`
- Create: `src/main/java/io/synapse/platform/user/api/UserApi.java`

- [ ] **Step 1: Move user package**

Move `auth-service/src/main/java/io/synapse/platform/auth/user/**` into `src/main/java/io/synapse/platform/user/**`.

- [ ] **Step 2: Rename package declarations and imports**

Apply these replacements in `src/main/java/io/synapse/platform/user/**`:
```text
io.synapse.platform.auth.user.domain -> io.synapse.platform.user.domain
io.synapse.platform.auth.user.repository -> io.synapse.platform.user.repository
io.synapse.platform.auth.user -> io.synapse.platform.user
```

- [ ] **Step 3: Create `UserInfo.java`**

```java
package io.synapse.platform.user.api;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String displayName,
        UUID defaultTenantId
) {
}
```

- [ ] **Step 4: Create `OAuthUserCreateCommand.java`**

```java
package io.synapse.platform.user.api;

import java.util.UUID;

public record OAuthUserCreateCommand(
        String email,
        String slug,
        String displayName,
        String avatarUrl,
        UUID defaultTenantId
) {
}
```

- [ ] **Step 5: Create `UserApi.java`**

```java
package io.synapse.platform.user.api;

import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserInfo> findById(UUID userId);

    Optional<UserInfo> findByEmail(String email);

    UserInfo createForOAuth(OAuthUserCreateCommand command);
}
```

- [ ] **Step 6: Replace `UserService.java`**

Use the exact implementation from `docs/ai/current/HANDOFF.md` Phase D, with `public class UserService implements UserApi`.

- [ ] **Step 7: Verify user package no longer mentions auth.user**

Run:
```powershell
rg "io\.synapse\.platform\.auth\.user" src\main\java\io\synapse\platform\user
```

Expected:
- No matches.

---

## Task 6: Refactor Auth To Use UserApi

**Files:**
- Modify: `src/main/java/io/synapse/platform/auth/oauth/OAuthUserResolver.java`
- Modify: `src/main/java/io/synapse/platform/auth/oauth/CustomOAuth2UserService.java`
- Modify: `src/main/java/io/synapse/platform/auth/oauth/CustomOidcUserService.java`
- Modify: `src/main/java/io/synapse/platform/auth/oauth/OAuth2SuccessHandler.java` if needed by constructor or imports
- Modify: `src/main/java/io/synapse/platform/auth/mfa/TotpService.java`

- [ ] **Step 1: Change `OAuthUserResolver` dependencies**

Replace user repository fields with:
```java
private final UserApi userApi;
```

Imports:
```java
import io.synapse.platform.user.api.OAuthUserCreateCommand;
import io.synapse.platform.user.api.UserApi;
import io.synapse.platform.user.api.UserInfo;
```

- [ ] **Step 2: Change `OAuthUserResolver.resolveUser` return type**

Change:
```java
public User resolveUser(OAuthAttributes attributes, String accessToken)
```

To:
```java
public UserInfo resolveUser(OAuthAttributes attributes, String accessToken)
```

- [ ] **Step 3: Change identity lookup branch**

Use:
```java
return userApi.findById(existing.getUserId())
        .orElseThrow(() -> new IllegalStateException("User not found"));
```

- [ ] **Step 4: Change email lookup branch**

Use:
```java
Optional<UserInfo> existingUser = userApi.findByEmail(attributes.email());
if (existingUser.isPresent()) {
    UserInfo user = existingUser.get();
    oauthIdentityRepository.save(OAuthIdentity.of(
            user.id(),
            attributes.provider(),
            attributes.providerId(),
            attributes.email(),
            accessTokenEnc));
    return user;
}
```

- [ ] **Step 5: Change OAuth signup branch**

Use `OAuthUserCreateCommand` and `userApi.createForOAuth(command)` exactly as specified in HANDOFF Phase D.

- [ ] **Step 6: Change `CustomOAuth2UserService`**

Replace `User user = oAuthUserResolver.resolveUser(...)` with:
```java
UserInfo user = oAuthUserResolver.resolveUser(attributes, accessToken(request));
```

Replace:
```java
enrichedAttributes.put("userId", user.getId().toString());
```

With:
```java
enrichedAttributes.put("userId", user.id().toString());
```

- [ ] **Step 7: Change `CustomOidcUserService`**

Apply the same `UserInfo` import and accessor pattern if it reads `User` from `OAuthUserResolver`.

- [ ] **Step 8: Change `TotpService`**

Replace `UserRepository` with `UserApi`. In `setup(UUID userId)`, use:
```java
UserInfo user = userApi.findById(userId)
        .orElseThrow(() -> new UnauthorizedTokenException("Authentication required"));
```

Replace:
```java
.label(user.getEmail())
```

With:
```java
.label(user.email())
```

- [ ] **Step 9: Verify auth no longer imports user internals**

Run:
```powershell
rg "io\.synapse\.platform\.user\.(domain|repository)|io\.synapse\.platform\.auth\.user" src\main\java\io\synapse\platform\auth
```

Expected:
- No matches.

---

## Task 7: Move Shared Module And Update Imports

**Files:**
- Move/modify: `src/main/java/io/synapse/platform/shared/crypto/FieldEncryptor.java`
- Move/modify: `src/main/java/io/synapse/platform/shared/exception/BusinessException.java`
- Move/modify: `src/main/java/io/synapse/platform/shared/exception/GlobalExceptionHandler.java`
- Move/modify tests under `src/test/java/io/synapse/platform/shared/**`

- [ ] **Step 1: Move common files to shared**

Move:
```text
platform-common/src/main/java/io/synapse/platform/common/crypto/FieldEncryptor.java
platform-common/src/main/java/io/synapse/platform/common/exception/BusinessException.java
platform-common/src/main/java/io/synapse/platform/common/exception/GlobalExceptionHandler.java
```

To:
```text
src/main/java/io/synapse/platform/shared/crypto/FieldEncryptor.java
src/main/java/io/synapse/platform/shared/exception/BusinessException.java
src/main/java/io/synapse/platform/shared/exception/GlobalExceptionHandler.java
```

- [ ] **Step 2: Rename shared packages**

Replace:
```text
package io.synapse.platform.common.crypto;
package io.synapse.platform.common.exception;
```

With:
```text
package io.synapse.platform.shared.crypto;
package io.synapse.platform.shared.exception;
```

- [ ] **Step 3: Update all common imports**

Replace throughout `src/main/java` and `src/test/java`:
```text
io.synapse.platform.common.crypto.FieldEncryptor -> io.synapse.platform.shared.crypto.FieldEncryptor
io.synapse.platform.common.exception.BusinessException -> io.synapse.platform.shared.exception.BusinessException
io.synapse.platform.common.exception.GlobalExceptionHandler -> io.synapse.platform.shared.exception.GlobalExceptionHandler
```

- [ ] **Step 4: Move shared tests**

Move platform-common tests to:
```text
src/test/java/io/synapse/platform/shared/crypto/FieldEncryptorTest.java
src/test/java/io/synapse/platform/shared/exception/GlobalExceptionHandlerTest.java
```

- [ ] **Step 5: Verify no common references remain**

Run:
```powershell
rg "io\.synapse\.platform\.common" src
```

Expected:
- No matches.

---

## Task 8: Add Admin And Notification Placeholders

**Files:**
- Create: `src/main/java/io/synapse/platform/admin/AdminPlaceholder.java`
- Create: `src/main/java/io/synapse/platform/notification/NotificationPlaceholder.java`

- [ ] **Step 1: Create admin placeholder**

```java
package io.synapse.platform.admin;

import org.springframework.stereotype.Component;

@Component
class AdminPlaceholder {
}
```

- [ ] **Step 2: Create notification placeholder**

```java
package io.synapse.platform.notification;

import org.springframework.stereotype.Component;

@Component
class NotificationPlaceholder {
}
```

---

## Task 9: Move And Update Tests

**Files:**
- Move: `auth-service/src/test/java/io/synapse/platform/auth/**`
- Create: `src/test/java/io/synapse/platform/PlatformApplicationTests.java`
- Create: `src/test/java/io/synapse/platform/PlatformModuleStructureTest.java`
- Modify imports in moved tests.

- [ ] **Step 1: Move auth tests**

Move `auth-service/src/test/java/io/synapse/platform/auth/**` to `src/test/java/io/synapse/platform/auth/**`.

- [ ] **Step 2: Replace app smoke test**

Create `src/test/java/io/synapse/platform/PlatformApplicationTests.java`:
```java
package io.synapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PlatformApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 3: Create Modulith structure test**

Create `src/test/java/io/synapse/platform/PlatformModuleStructureTest.java`:
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

- [ ] **Step 4: Update moved test imports**

Apply the same import replacements used in main code:
```text
io.synapse.platform.auth.user -> io.synapse.platform.user
io.synapse.platform.common -> io.synapse.platform.shared
```

- [ ] **Step 5: Verify tests no longer point to old app class**

Run:
```powershell
rg "AuthServiceApplication|auth\.user|io\.synapse\.platform\.common" src\test
```

Expected:
- No matches.

---

## Task 10: Compile, Fix, And Verify Modulith

**Files:**
- Modify only files already moved under `src/` and root Gradle files.

- [ ] **Step 1: Compile Java**

Run:
```powershell
.\gradlew.bat compileJava --no-daemon
```

Expected:
- Compilation succeeds.
- If it fails, fix only package/import/API mismatches introduced by migration.

- [ ] **Step 2: Run Modulith test**

Run:
```powershell
.\gradlew.bat test --tests "*ModuleStructureTest" --no-daemon
```

Expected:
- `PlatformModuleStructureTest` passes.

- [ ] **Step 3: Run full tests**

Run:
```powershell
.\gradlew.bat test --no-daemon
```

Expected:
- All existing tests pass.
- Test count is at least 68.

---

## Task 11: Remove Old Module Directories

**Files:**
- Delete: `auth-service/`
- Delete: `billing-service/`
- Delete: `audit-service/`
- Delete: `notification-service/`
- Delete: `platform-common/`

- [ ] **Step 1: Confirm full tests passed before deletion**

Do not delete old module directories unless Task 10 Step 3 passed.

- [ ] **Step 2: Delete old module directories**

Use PowerShell native removal with literal paths:
```powershell
Remove-Item -Recurse -Force -LiteralPath auth-service
Remove-Item -Recurse -Force -LiteralPath billing-service
Remove-Item -Recurse -Force -LiteralPath audit-service
Remove-Item -Recurse -Force -LiteralPath notification-service
Remove-Item -Recurse -Force -LiteralPath platform-common
```

- [ ] **Step 3: Verify old modules are gone**

Run:
```powershell
Test-Path auth-service
Test-Path billing-service
Test-Path audit-service
Test-Path notification-service
Test-Path platform-common
```

Expected:
- All return `False`.

---

## Task 12: Final Verification

**Files:**
- No planned edits.

- [ ] **Step 1: Run clean build**

Run:
```powershell
.\gradlew.bat clean build --no-daemon
```

Expected:
- Build succeeds.

- [ ] **Step 2: Run full tests again**

Run:
```powershell
.\gradlew.bat test --no-daemon
```

Expected:
- All tests pass.

- [ ] **Step 3: Verify Done When checks**

Run:
```powershell
rg "include\\(" settings.gradle.kts
rg "io\\.synapse\\.platform\\.common|io\\.synapse\\.platform\\.auth\\.user" src
rg --files src\main\java\io\synapse\platform | rg "PlatformApplication.java|package-info.java|UserApi.java|UserInfo.java|OAuthUserCreateCommand.java"
```

Expected:
- First command has no matches.
- Second command has no matches.
- Third command shows the app entrypoint, module declarations, and user API files.

- [ ] **Step 4: Collect final report data**

Run:
```powershell
git status --short
```

Final response must include:
- Written/moved file list summary.
- Test commands and results.
- Done When checklist status.

---

## Self-Review

- Spec coverage: All HANDOFF phases A through H are represented by Tasks 1 through 12.
- Placeholder scan: No build version placeholders remain; Modulith version is fixed to `2.0.6`.
- Type consistency: External user access uses `UserApi`, `UserInfo`, and `OAuthUserCreateCommand`; auth does not import `user.domain` or `user.repository`.

---

## Task 13: Review Follow-up — Docker Compose Local Env

**Files:**
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `docker-compose.yml`

- [x] **Step 1: Create `.env.example`**

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

- [x] **Step 2: Ignore local env files**

Add to `.gitignore`:

```gitignore
.env
.env.local
```

- [x] **Step 3: Wire compose to `.env`**

Update `docker-compose.yml`:

```yaml
  platform-svc:
    env_file:
      - .env
```

Keep the existing DB/Redis environment block.

- [x] **Step 4: Validate compose config**

Run:
```powershell
docker compose config
```

Expected:
- Compose config renders successfully.
- `platform-svc` includes `env_file`.

- [ ] **Step 5: Validate runtime with a real local `.env`**

Status: blocked until a developer-provided `.env` contains valid OAuth, AES, and JWT key values. Do not commit that file.

Run:
```powershell
docker compose up --build
```

Expected:
- `platform-svc` starts on port `8081`.
- `GET http://localhost:8081/actuator/health` responds.
- No real secret values are committed.
