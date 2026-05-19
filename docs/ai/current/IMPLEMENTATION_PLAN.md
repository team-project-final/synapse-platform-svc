# Template Structure Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `synapse-platform-svc`의 Java package root와 모듈 디렉토리 구조를 `docs/synapse-svc-template-skeleton-platform-w1` 기준으로 정렬한다.

**Architecture:** 기능 코드는 변경하지 않고 package 선언, import, 파일 위치, Gradle group만 변경한다. 각 Phase는 컴파일 가능한 중간 상태를 만든 뒤 커밋한다. Spring Modulith NamedInterface인 `auth/api`와 `user/api`는 package root만 바꾸고 하위 위치는 이동하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Modulith, Gradle Kotlin DSL, JUnit 5, Checkstyle, SpotBugs, JaCoCo.

---

## 작업 원칙

- 기능 로직 변경 금지
- SQL migration 변경 금지
- 의존성 추가/삭제 금지
- 테스트 assertion 의미 변경 금지
- `auth/api/package-info.java`, `user/api/package-info.java` 위치 이동 금지
- Phase별 검증 통과 후 다음 Phase 진행
- Phase별 커밋 메시지는 `HANDOFF.md` 지시를 따른다

## 사전 확인

**Files:**
- Read: `docs/ai/current/HANDOFF.md`
- Read: `docs/ai/current/CONTEXT.md`
- Read: `docs/ai/current/TASK.md`
- Read: `docs/synapse-svc-template-skeleton-platform-w1/README.md`

- [x] **Step 1: 현재 브랜치 확인**

Run:

```powershell
git branch --show-current
```

Expected:

```plain text
refactor/PLAT-REF-001-template-align
```

- [x] **Step 2: 작업 전 상태 확인**

Run:

```powershell
git status --short
```

Expected: Director가 제공한 문서 변경 외에 작업자가 만든 미확인 코드 변경이 없어야 한다.

- [x] **Step 3: 템플릿 존재 확인**

Run:

```powershell
Test-Path docs\synapse-svc-template-skeleton-platform-w1
```

Expected:

```plain text
True
```

## Phase 1: 패키지 루트 `io.synapse` -> `com.synapse`

**Files:**
- Move: `src/main/java/io/synapse/platform/` -> `src/main/java/com/synapse/platform/`
- Move: `src/test/java/io/synapse/platform/` -> `src/test/java/com/synapse/platform/`
- Modify: all `*.java`
- Modify: `build.gradle.kts`

- [x] **Step 1: package/import 문자열 치환**

Run:

```powershell
Get-ChildItem -Path src\main\java,src\test\java -Recurse -Filter *.java | ForEach-Object {
    $path = $_.FullName
    $content = Get-Content -LiteralPath $path -Raw
    $updated = $content.Replace("io.synapse", "com.synapse")
    if ($updated -ne $content) {
        Set-Content -LiteralPath $path -Value $updated -NoNewline
    }
}
$files = @("build.gradle.kts", "config\spotbugs\exclude.xml")
foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file -Raw
    $updated = $content.Replace("io.synapse", "com.synapse")
    if ($updated -ne $content) {
        Set-Content -LiteralPath $file -Value $updated -NoNewline
    }
}
```

- [x] **Step 2: main Java 디렉토리 이동**

Run:

```powershell
New-Item -ItemType Directory -Force -Path src\main\java\com | Out-Null
Move-Item -LiteralPath src\main\java\io\synapse -Destination src\main\java\com\synapse
Remove-Item -LiteralPath src\main\java\io -Recurse
```

- [x] **Step 3: test Java 디렉토리 이동**

Run:

```powershell
New-Item -ItemType Directory -Force -Path src\test\java\com | Out-Null
Move-Item -LiteralPath src\test\java\io\synapse -Destination src\test\java\com\synapse
Remove-Item -LiteralPath src\test\java\io -Recurse
```

- [x] **Step 4: 잔여 `io.synapse` 검색**

Run:

```powershell
rg -n "io\.synapse" src build.gradle.kts config\spotbugs\exclude.xml
```

Expected: no output.

- [x] **Step 5: Phase 1 컴파일 검증**

Run:

```powershell
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat compileTestJava --no-daemon
```

Expected: both pass.

- [x] **Step 6: Phase 1 커밋**

Run:

```powershell
git add build.gradle.kts config/spotbugs/exclude.xml src
git commit -m "refactor(infra): 패키지 루트 io.synapse → com.synapse 변경"
```

## Phase 2: `shared` 모듈을 `global`로 변경

**Files:**
- Move: `src/main/java/com/synapse/platform/shared/` -> `src/main/java/com/synapse/platform/global/`
- Move: `src/test/java/com/synapse/platform/shared/` -> `src/test/java/com/synapse/platform/global/`
- Modify: imports referencing `com.synapse.platform.shared`

- [x] **Step 1: shared 디렉토리 이동**

Run:

```powershell
Move-Item -LiteralPath src\main\java\com\synapse\platform\shared -Destination src\main\java\com\synapse\platform\global
Move-Item -LiteralPath src\test\java\com\synapse\platform\shared -Destination src\test\java\com\synapse\platform\global
```

- [x] **Step 2: package/import 문자열 치환**

Run:

```powershell
Get-ChildItem -Recurse -Include *.java src\main\java,src\test\java | ForEach-Object {
    $path = $_.FullName
    $content = Get-Content -LiteralPath $path -Raw
    $updated = $content.Replace("com.synapse.platform.shared", "com.synapse.platform.global")
    if ($updated -ne $content) {
        Set-Content -LiteralPath $path -Value $updated -NoNewline
    }
}
```

- [x] **Step 3: 잔여 shared package 검색**

Run:

```powershell
rg -n "com\.synapse\.platform\.shared|platform/shared|platform\\shared" src
```

Expected: no output.

- [x] **Step 4: Phase 2 컴파일 검증**

Run:

```powershell
.\gradlew.bat compileJava --no-daemon
```

Expected: pass.

- [x] **Step 5: Phase 2 커밋**

Run:

```powershell
git add src
git commit -m "refactor(infra): shared 모듈 → global로 이름 변경 (W2 표준)"
```

## Phase 3: auth 모듈 서브패키지 평탄화

**Files:**
- Move: `src/main/java/com/synapse/platform/auth/AuthController.java` -> `auth/controller/AuthController.java`
- Move: `src/main/java/com/synapse/platform/auth/AuthCallbackController.java` -> `auth/controller/AuthCallbackController.java`
- Move: `src/main/java/com/synapse/platform/auth/mfa/MfaController.java` -> `auth/controller/MfaController.java`
- Move: `auth/TenantService.java`, `auth/jwt/*Service.java`, `auth/jwt/JwtTokenProvider.java`, `auth/mfa/TotpService.java`, `auth/oauth/*Service.java`, `auth/oauth/*Handler.java`, `auth/oauth/OAuthUserResolver.java` -> `auth/service/`
- Move: `auth/domain/*.java`, `auth/mfa/MfaCredential.java`, `auth/jwt/RefreshToken.java` -> `auth/entity/`
- Move: `auth/mfa/MfaCredentialRepository.java`, `auth/jwt/RefreshTokenRepository.java` -> `auth/repository/`
- Move: `auth/oauth/OAuthAttributes.java` -> `auth/dto/OAuthAttributes.java`
- Move: `auth/jwt/JwtAuthenticationFilter.java`, `auth/jwt/JwtProperties.java` -> `auth/config/`
- Move: matching test files according to `HANDOFF.md`

- [x] **Step 1: auth 목표 디렉토리 생성**

Run:

```powershell
$auth = "src\main\java\com\synapse\platform\auth"
New-Item -ItemType Directory -Force -Path "$auth\controller","$auth\service","$auth\entity","$auth\dto" | Out-Null
$authTest = "src\test\java\com\synapse\platform\auth"
New-Item -ItemType Directory -Force -Path "$authTest\controller","$authTest\service","$authTest\entity","$authTest\dto","$authTest\config","$authTest\util" | Out-Null
```

- [x] **Step 2: auth main 파일 이동**

Run the file moves listed in `HANDOFF.md Phase 3`. Preserve `auth/api`, `auth/exception`, `auth/repository`, `auth/config`, `auth/util`, `AuthRoles.java`, and `package-info.java` positions except for explicitly listed moves.

Execution note: PowerShell wildcard 이동 시 `-LiteralPath "$auth\domain\*"`는 wildcard를 확장하지 않는다. `domain` 파일 이동은 아래 방식으로 처리한다.

```powershell
Get-ChildItem -LiteralPath "$auth\domain" -File | Move-Item -Destination "$auth\entity"
```

- [x] **Step 3: auth test 파일 이동**

Run the test file moves listed in `HANDOFF.md Phase 3` exactly.

- [x] **Step 4: auth package/import 치환**

Apply these replacements across `src/main/java` and `src/test/java`:

```plain text
com.synapse.platform.auth.jwt.JwtAuthenticationFilter -> com.synapse.platform.auth.config.JwtAuthenticationFilter
com.synapse.platform.auth.jwt.JwtProperties -> com.synapse.platform.auth.config.JwtProperties
com.synapse.platform.auth.jwt.JwtTokenProvider -> com.synapse.platform.auth.service.JwtTokenProvider
com.synapse.platform.auth.jwt.RefreshTokenService -> com.synapse.platform.auth.service.RefreshTokenService
com.synapse.platform.auth.jwt.RefreshTokenRepository -> com.synapse.platform.auth.repository.RefreshTokenRepository
com.synapse.platform.auth.jwt.RefreshToken -> com.synapse.platform.auth.entity.RefreshToken
com.synapse.platform.auth.mfa.MfaController -> com.synapse.platform.auth.controller.MfaController
com.synapse.platform.auth.mfa.TotpService -> com.synapse.platform.auth.service.TotpService
com.synapse.platform.auth.mfa.MfaCredential -> com.synapse.platform.auth.entity.MfaCredential
com.synapse.platform.auth.mfa.MfaCredentialRepository -> com.synapse.platform.auth.repository.MfaCredentialRepository
com.synapse.platform.auth.oauth.OAuthAttributes -> com.synapse.platform.auth.dto.OAuthAttributes
com.synapse.platform.auth.oauth.CustomOAuth2UserService -> com.synapse.platform.auth.service.CustomOAuth2UserService
com.synapse.platform.auth.oauth.CustomOidcUserService -> com.synapse.platform.auth.service.CustomOidcUserService
com.synapse.platform.auth.oauth.OAuth2SuccessHandler -> com.synapse.platform.auth.service.OAuth2SuccessHandler
com.synapse.platform.auth.oauth.OAuth2FailureHandler -> com.synapse.platform.auth.service.OAuth2FailureHandler
com.synapse.platform.auth.oauth.OAuthUserResolver -> com.synapse.platform.auth.service.OAuthUserResolver
com.synapse.platform.auth.domain -> com.synapse.platform.auth.entity
package com.synapse.platform.auth.jwt -> package com.synapse.platform.auth.service
package com.synapse.platform.auth.mfa -> package com.synapse.platform.auth.service
package com.synapse.platform.auth.oauth -> package com.synapse.platform.auth.service
package com.synapse.platform.auth.domain -> package com.synapse.platform.auth.entity
```

Then manually correct moved controller/config/dto/repository/entity files whose package differs from the broad rule:

```plain text
AuthController.java, AuthCallbackController.java, MfaController.java -> package com.synapse.platform.auth.controller
JwtAuthenticationFilter.java, JwtProperties.java -> package com.synapse.platform.auth.config
OAuthAttributes.java -> package com.synapse.platform.auth.dto
RefreshTokenRepository.java, MfaCredentialRepository.java -> package com.synapse.platform.auth.repository
RefreshToken.java, MfaCredential.java -> package com.synapse.platform.auth.entity
```

- [x] **Step 5: 제거 대상 auth 디렉토리 확인**

Run:

```powershell
Test-Path src\main\java\com\synapse\platform\auth\jwt
Test-Path src\main\java\com\synapse\platform\auth\mfa
Test-Path src\main\java\com\synapse\platform\auth\oauth
Test-Path src\main\java\com\synapse\platform\auth\domain
```

Expected: all `False`.

- [x] **Step 6: Phase 3 검증**

Run:

```powershell
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --tests "com.synapse.platform.auth.*" --no-daemon
```

Expected: both pass.

Execution note: Phase 3 첫 `compileJava`에서 패키지 분리로 인해 기존 same-package 타입 참조 import가 누락됐다. 기능 변경 없이 아래 타입 import를 보강한다.

- `AuthController` -> `AuthRoles`
- `MfaController` -> `TotpService`
- `JwtAuthenticationFilter` -> `JwtTokenProvider`
- `JwtTokenProvider` -> `JwtProperties`
- `RefreshTokenService` -> `RefreshToken`, `RefreshTokenRepository`
- `TotpService` -> `MfaCredential`, `MfaCredentialRepository`
- OAuth service classes -> `OAuthAttributes`
- moved repositories -> moved entities

Execution note: 두 번째 `compileJava`에서 `RefreshToken`, `MfaCredential`, `JwtProperties.rsaPrivateKey()`, `JwtProperties.rsaPublicKey()`가 package-private이라 새 패키지 구조에서 접근할 수 없었다. 구조 분리 후 필요한 타입/메서드는 public으로 조정한다.

Execution note: `compileTestJava`에서도 같은 이유로 테스트의 same-package 참조가 깨졌다. 테스트 파일은 이동된 대상 package에 맞춰 필요한 production/test 타입 import만 보강한다.

- [x] **Step 7: Phase 3 커밋**

Run:

```powershell
git add src
git commit -m "refactor(auth): jwt/mfa/oauth 서브패키지 평탄화 → controller/service/entity/config"
```

## Phase 4: user 모듈 구조 정렬

**Files:**
- Move: `src/main/java/com/synapse/platform/user/UserController.java` -> `user/controller/UserController.java`
- Move: `src/main/java/com/synapse/platform/user/UserService.java` -> `user/service/UserService.java`
- Move: `src/main/java/com/synapse/platform/user/domain/*.java` -> `user/entity/`
- Keep: `src/main/java/com/synapse/platform/user/api/*`

- [x] **Step 1: user 목표 디렉토리 생성 및 이동**

Run:

```powershell
$user = "src\main\java\com\synapse\platform\user"
New-Item -ItemType Directory -Force -Path "$user\controller","$user\service","$user\entity" | Out-Null
Move-Item -LiteralPath "$user\UserController.java" -Destination "$user\controller\UserController.java"
Move-Item -LiteralPath "$user\UserService.java" -Destination "$user\service\UserService.java"
Move-Item -LiteralPath "$user\domain\*" -Destination "$user\entity"
Remove-Item -LiteralPath "$user\domain" -Recurse
```

- [x] **Step 2: user package/import 치환**

Apply across `src/main/java` and `src/test/java`:

```plain text
com.synapse.platform.user.UserController -> com.synapse.platform.user.controller.UserController
com.synapse.platform.user.UserService -> com.synapse.platform.user.service.UserService
com.synapse.platform.user.domain -> com.synapse.platform.user.entity
package com.synapse.platform.user; -> package com.synapse.platform.user.controller; for UserController.java
package com.synapse.platform.user; -> package com.synapse.platform.user.service; for UserService.java
package com.synapse.platform.user.domain -> package com.synapse.platform.user.entity
```

- [x] **Step 3: Phase 4 검증**

Run:

```powershell
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --tests "com.synapse.platform.user.*" --no-daemon
```

Expected: both pass.

Execution note: 현재 `src/test/java`에는 `com.synapse.platform.user.*` 패키지의 user 전용 테스트 클래스가 없어 `.\gradlew.bat test --tests "com.synapse.platform.user.*" --no-daemon`는 `No tests found`로 실패한다. Phase 4 검증은 `compileJava`, `compileTestJava`, 잔여 `user.domain` 참조 검색으로 대체했고, 전체 테스트는 최종 Phase 7에서 수행한다.

- [x] **Step 4: Phase 4 커밋**

Run:

```powershell
git add src
git commit -m "refactor(user): domain → entity, Controller/Service 서브패키지 이동"
```

## Phase 5: billing 모듈 구조 정렬

**Files:**
- Move: `src/main/java/com/synapse/platform/billing/BillingController.java` -> `billing/controller/BillingController.java`
- Move: `src/main/java/com/synapse/platform/billing/BillingService.java` -> `billing/service/BillingService.java`
- Move: `src/main/java/com/synapse/platform/billing/domain/*.java` -> `billing/entity/`
- Move: `src/main/java/com/synapse/platform/billing/dto/CheckoutSessionRequest.java` -> `billing/dto/request/CheckoutSessionRequest.java`
- Move: `src/main/java/com/synapse/platform/billing/dto/CheckoutSessionResponse.java` -> `billing/dto/response/CheckoutSessionResponse.java`
- Move: `src/main/java/com/synapse/platform/billing/dto/SubscriptionResponse.java` -> `billing/dto/response/SubscriptionResponse.java`

- [x] **Step 1: billing 목표 디렉토리 생성 및 이동**

Run:

```powershell
$billing = "src\main\java\com\synapse\platform\billing"
New-Item -ItemType Directory -Force -Path "$billing\controller","$billing\service","$billing\entity","$billing\dto\request","$billing\dto\response" | Out-Null
Move-Item -LiteralPath "$billing\BillingController.java" -Destination "$billing\controller\BillingController.java"
Move-Item -LiteralPath "$billing\BillingService.java" -Destination "$billing\service\BillingService.java"
Move-Item -LiteralPath "$billing\domain\*" -Destination "$billing\entity"
Move-Item -LiteralPath "$billing\dto\CheckoutSessionRequest.java" -Destination "$billing\dto\request\CheckoutSessionRequest.java"
Move-Item -LiteralPath "$billing\dto\CheckoutSessionResponse.java" -Destination "$billing\dto\response\CheckoutSessionResponse.java"
Move-Item -LiteralPath "$billing\dto\SubscriptionResponse.java" -Destination "$billing\dto\response\SubscriptionResponse.java"
Remove-Item -LiteralPath "$billing\domain" -Recurse
```

- [x] **Step 2: billing package/import 치환**

Apply across `src/main/java` and `src/test/java`:

```plain text
com.synapse.platform.billing.BillingController -> com.synapse.platform.billing.controller.BillingController
com.synapse.platform.billing.BillingService -> com.synapse.platform.billing.service.BillingService
com.synapse.platform.billing.domain -> com.synapse.platform.billing.entity
com.synapse.platform.billing.dto.CheckoutSessionRequest -> com.synapse.platform.billing.dto.request.CheckoutSessionRequest
com.synapse.platform.billing.dto.CheckoutSessionResponse -> com.synapse.platform.billing.dto.response.CheckoutSessionResponse
com.synapse.platform.billing.dto.SubscriptionResponse -> com.synapse.platform.billing.dto.response.SubscriptionResponse
package com.synapse.platform.billing; -> package com.synapse.platform.billing.controller; for BillingController.java
package com.synapse.platform.billing; -> package com.synapse.platform.billing.service; for BillingService.java
package com.synapse.platform.billing.domain -> package com.synapse.platform.billing.entity
package com.synapse.platform.billing.dto -> package com.synapse.platform.billing.dto.request for CheckoutSessionRequest.java
package com.synapse.platform.billing.dto -> package com.synapse.platform.billing.dto.response for response DTO files
```

- [x] **Step 3: billing test package 이동 여부 정리**

Move billing domain tests to entity package:

```powershell
$billingTest = "src\test\java\com\synapse\platform\billing"
New-Item -ItemType Directory -Force -Path "$billingTest\entity" | Out-Null
Move-Item -LiteralPath "$billingTest\domain\BillingDomainTest.java" -Destination "$billingTest\entity\BillingDomainTest.java"
Remove-Item -LiteralPath "$billingTest\domain" -Recurse
```

Set test package:

```plain text
package com.synapse.platform.billing.entity;
```

- [x] **Step 4: Phase 5 검증**

Run:

```powershell
.\gradlew.bat compileJava --no-daemon
.\gradlew.bat test --tests "com.synapse.platform.billing.*" --no-daemon
```

Expected: both pass.

Execution note: Phase 5 첫 `compileJava`에서 `BillingController`와 `BillingService`가 서로 다른 패키지로 분리되며 기존 same-package 참조가 깨졌다. 기능 변경 없이 `BillingController`에 `com.synapse.platform.billing.service.BillingService` import를 보강한다.

Execution note: Phase 5 첫 billing 테스트 실행 중 `compileTestJava`에서도 root package 테스트의 same-package 참조가 깨졌다. `BillingControllerTest`, `BillingSecurityIntegrationTest`, `BillingServiceTest`에 이동된 controller/service import를 보강한다.

- [x] **Step 5: Phase 5 커밋**

Run:

```powershell
git add src
git commit -m "refactor(billing): domain → entity, DTO request/response 분리, Controller/Service 서브패키지 이동"
```

## Phase 6: admin / notification package-info 확인

**Files:**
- Check: `src/main/java/com/synapse/platform/admin/package-info.java`
- Check: `src/main/java/com/synapse/platform/notification/package-info.java`

- [ ] **Step 1: package-info 선언 확인**

Run:

```powershell
Get-Content src\main\java\com\synapse\platform\admin\package-info.java
Get-Content src\main\java\com\synapse\platform\notification\package-info.java
```

Expected:

```plain text
package com.synapse.platform.admin;
package com.synapse.platform.notification;
```

## Phase 7: 최종 검증

**Files:**
- No planned edits.

- [ ] **Step 1: 잔여 구 패키지 검색**

Run:

```powershell
rg -n "io\.synapse|com\.synapse\.platform\.shared|com\.synapse\.platform\.(auth|user|billing)\.domain|com\.synapse\.platform\.auth\.(jwt|mfa|oauth)" src build.gradle.kts
```

Expected: no output.

- [ ] **Step 2: 목표 구조 확인**

Run:

```powershell
Test-Path src\main\java\com\synapse\platform\global
Test-Path src\main\java\com\synapse\platform\auth\controller
Test-Path src\main\java\com\synapse\platform\auth\service
Test-Path src\main\java\com\synapse\platform\auth\entity
Test-Path src\main\java\com\synapse\platform\user\entity
Test-Path src\main\java\com\synapse\platform\billing\entity
Test-Path src\main\java\com\synapse\platform\billing\dto\request
Test-Path src\main\java\com\synapse\platform\billing\dto\response
```

Expected: all `True`.

- [ ] **Step 3: NamedInterface 위치 확인**

Run:

```powershell
Test-Path src\main\java\com\synapse\platform\auth\api\package-info.java
Test-Path src\main\java\com\synapse\platform\user\api\package-info.java
```

Expected: both `True`.

- [ ] **Step 4: Modulith 구조 검증**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.PlatformModuleStructureTest" --no-daemon
```

Expected: pass.

- [ ] **Step 5: 전체 빌드 및 테스트**

Run:

```powershell
.\gradlew.bat clean build --no-daemon
.\gradlew.bat test --no-daemon
```

Expected: both pass.

- [ ] **Step 6: 최종 커밋**

Run:

```powershell
git add src build.gradle.kts
git commit -m "refactor(infra): 템플릿 기준 전체 패키지 구조 정렬 완료"
```

## Self-Review

**Spec coverage:** `HANDOFF.md`의 Phase 1-7 요구사항을 모두 작업 단위로 포함했다. `auth/api`와 `user/api` 위치 유지, `shared` to `global`, `domain` to `entity`, billing DTO request/response 분리, final verification을 포함했다.

**Placeholder scan:** 실행 시 결정해야 하는 placeholder는 없다. Phase 3의 대량 move는 `HANDOFF.md`의 명시 목록을 기준으로 수행하도록 고정했다.

**Type consistency:** 계획 전체에서 package root는 `com.synapse.platform`으로 통일했다. `shared` 변경 후 참조는 `global`로만 사용한다. Billing DTO는 request/response package로 분리한다.
