# Step 2 룰북 준수 수정 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Step 2 OAuth 구현 코드가 룰북 [MUST] 7건과 [SHOULD] 3건을 모두 준수하도록 수정한다.

**Architecture:** 기존 OAuth 흐름은 유지하고, API URI를 `/api/v1` 규칙에 맞춘다. 공통 예외 처리는 shared 모듈에 두고 auth는 shared의 추상 예외만 의존한다. 정적 분석 게이트는 먼저 설치하되 기존 파일은 suppression으로 격리하고 신규 파일만 게이트 대상이 되게 한다.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Security OAuth2 Client, Spring Data JPA, Spring Modulith, Checkstyle, SpotBugs, Logback, Logstash Logback Encoder, JUnit 5, MockMvc.

---

## 작업 원칙

- 구현 기준은 `docs/ai/current/HANDOFF.md`이다.
- `CONTEXT.md`의 staging 언급은 이번 범위 밖이다. `application-dev.yml`, `application-prod.yml`만 생성한다.
- Director 권장 순서대로 Checkstyle/SpotBugs 게이트를 먼저 설정한다.
- 기존 코드 전수 스타일 수정은 하지 않는다. `config/checkstyle/suppressions.xml`로 기존 auth 구현 파일을 격리한다.
- 완료된 항목은 `docs/ai/current/TASK.md`, `docs/ai/current/HANDOFF.md`, 이 계획 문서의 체크박스를 갱신한다.

---

## 파일 작업 범위

### 수정

- `build.gradle.kts`
- `src/main/java/com/synapse/platform/auth/AuthCallbackController.java`
- `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- `src/main/java/com/synapse/platform/auth/oauth/OAuth2SuccessHandler.java`
- `src/main/java/com/synapse/platform/auth/oauth/OAuth2FailureHandler.java`
- `src/main/java/com/synapse/platform/auth/oauth/CustomOAuth2UserService.java`
- `src/main/java/com/synapse/platform/auth/domain/User.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- URI를 직접 참조하는 기존 테스트

### 생성

- `config/checkstyle/checkstyle.xml`
- `config/checkstyle/suppressions.xml`
- `config/spotbugs/exclude.xml`
- `src/main/java/com/synapse/platform/shared/exception/BusinessException.java`
- `src/main/java/com/synapse/platform/shared/exception/GlobalExceptionHandler.java`
- `src/main/java/com/synapse/platform/auth/exception/OAuthProcessingException.java`
- `src/main/java/com/synapse/platform/auth/config/CorsConfig.java`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/logback-spring.xml`
- 필요한 신규 테스트

---

## Task 1. 기준 상태 확인

- [x] 현재 git 상태를 확인한다.

```powershell
git status --short --branch
```

- [x] 현재 기준 빌드를 확인한다.

```powershell
.\gradlew.bat build
```

기대 결과:
- build 성공
- 현재 테스트 25건 이상 통과

---

## Task 2. Checkstyle + SpotBugs 게이트 우선 설치

**파일**
- 수정: `build.gradle.kts`
- 생성: `config/checkstyle/checkstyle.xml`
- 생성: `config/checkstyle/suppressions.xml`
- 생성: `config/spotbugs/exclude.xml`

- [x] `build.gradle.kts`에 플러그인을 추가한다.

```kotlin
plugins {
    checkstyle
    id("com.github.spotbugs") version "6.0.9"
}
```

- [x] `build.gradle.kts`에 Checkstyle 설정을 추가한다.

```kotlin
checkstyle {
    toolVersion = "10.12.5"
    configFile = file("config/checkstyle/checkstyle.xml")
}
```

- [x] `build.gradle.kts`에 SpotBugs 설정을 추가한다.

```kotlin
spotbugs {
    toolVersion = "4.8.3"
    excludeFilter = file("config/spotbugs/exclude.xml")
}
```

- [x] `config/checkstyle/checkstyle.xml`을 생성한다.

요구사항:
- `SuppressionFilter`로 `config/checkstyle/suppressions.xml` 연결
- Google Java Style 기반 최소 규칙
- 신규 파일의 기본 스타일 위반을 잡을 정도로 구성

- [x] `config/checkstyle/suppressions.xml`을 생성한다.

HANDOFF 지정 패턴:

```xml
<suppressions>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]oauth[\\/].*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]domain[\\/].*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]repository[\\/].*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]util[\\/].*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]config[\\/]HttpCookie.*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]config[\\/]OAuth2Authorization.*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]config[\\/]SecurityConfig.*\.java" checks=".*"/>
    <suppress files="com[\\/]synapse[\\/]platform[\\/]auth[\\/]AuthCallbackController.*\.java" checks=".*"/>
</suppressions>
```

- [x] `config/spotbugs/exclude.xml`을 생성한다.

최소 유효 XML:

```xml
<FindBugsFilter>
</FindBugsFilter>
```

- [x] 정적 분석 게이트 단독 확인을 실행한다.

```powershell
.\gradlew.bat checkstyleMain spotbugsMain
```

기대 결과:
- Checkstyle/SpotBugs task가 실행됨
- 실패하면 suppression/exclude 범위만 조정하고 기존 코드 전수 수정은 하지 않음

---

## Task 3. URI Prefix 변경

**파일**
- 수정: `AuthCallbackController.java`
- 수정: `SecurityConfig.java`
- 수정: `OAuth2SuccessHandler.java`
- 수정: `OAuth2FailureHandler.java`
- 수정: URI 참조 테스트

- [x] 테스트의 기대 URI를 `/api/v1/auth/callback`으로 먼저 변경한다.

- [x] URI 변경 테스트를 실행해 실패를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuth2SuccessHandlerTest" --tests "com.synapse.platform.auth.OAuth2FailureHandlerTest" --tests "com.synapse.platform.auth.OAuth2LoginIntegrationTest"
```

- [x] production URI를 변경한다.

변경:
- `AuthCallbackController`: `@RequestMapping("/api/v1/auth")`
- `SecurityConfig`: permitAll에 `/api/v1/auth/callback`
- `OAuth2SuccessHandler`: `/api/v1/auth/callback?userId=...`
- `OAuth2FailureHandler`: `/api/v1/auth/callback?error=...`

- [x] URI 관련 테스트를 다시 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuth2SuccessHandlerTest" --tests "com.synapse.platform.auth.OAuth2FailureHandlerTest" --tests "com.synapse.platform.auth.OAuth2LoginIntegrationTest"
```

- [x] 남은 `/auth/callback` 참조를 검색한다.

```powershell
rg "/auth/callback" src/main/java src/test/java
```

기대 결과:
- `/api/v1/auth/callback`만 남아 있음

---

## Task 4. RFC 7807 공통 예외 처리 추가

**파일**
- 생성: `BusinessException.java`
- 생성: `GlobalExceptionHandler.java`
- 생성: `OAuthProcessingException.java`
- 수정: `AuthCallbackController.java`
- 생성 또는 수정: 관련 테스트

- [x] `BusinessException`을 생성한다.

필수:
- 패키지: `com.synapse.platform.shared.exception`
- 필드: `errorCode`, `status`
- getter 제공

- [x] `OAuthProcessingException`을 생성한다.

필수:
- 패키지: `com.synapse.platform.auth.exception`
- `BusinessException` 상속
- errorCode: `PLAT-001`
- status: `400`

- [x] `GlobalExceptionHandler` 테스트를 먼저 작성한다.

검증:
- `OAuthProcessingException` 발생 시 RFC 7807 응답
- `MethodArgumentNotValidException`은 `PLAT-001`, 400 응답
- fallback `Exception`은 `PLAT-999`, 500 응답

- [x] 테스트 실패를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.shared.exception.GlobalExceptionHandlerTest"
```

- [x] `GlobalExceptionHandler`를 구현한다.

필수 응답 record:

```java
public record ErrorResponse(String type, String title, int status, String detail, String code, String traceId) {}
```

traceId:
- `request.getAttribute("traceId")`가 있으면 사용
- 없으면 `UUID.randomUUID().toString()`

- [x] `AuthCallbackController`의 error 응답을 `OAuthProcessingException` 기반으로 변경한다.

변경:
- `error != null`이면 `throw new OAuthProcessingException(error)`
- `userId == null`이면 `throw new OAuthProcessingException("userId is required")`

- [x] 예외/콜백 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.shared.exception.GlobalExceptionHandlerTest" --tests "com.synapse.platform.auth.OAuth2LoginIntegrationTest"
```

---

## Task 5. Soft Delete, Transaction 명시

**파일**
- 수정: `User.java`
- 수정: `CustomOAuth2UserService.java`

- [x] `User` 엔티티에 `@SQLRestriction("deleted_at IS NULL")`을 추가한다.

- [x] `CustomOAuth2UserService`에 propagation을 명시한다.

```java
@Transactional(propagation = Propagation.REQUIRED)
```

- [x] 관련 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.oauth.CustomOAuth2UserServiceTest" --tests "com.synapse.platform.auth.oauth.OAuthSignupRollbackIntegrationTest"
```

---

## Task 6. CORS 설정 추가

**파일**
- 생성: `CorsConfig.java`
- 수정: `application.yml`
- 수정: `application-local.yml`
- 생성 또는 수정: CORS 테스트

- [x] CORS 테스트를 먼저 작성한다.

검증:
- allowed origin은 `/api/**` 요청에서 `Access-Control-Allow-Origin` 반환
- `*` origin을 사용하지 않음

- [x] 실패 확인을 위해 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.CorsConfigTest"
```

- [x] `CorsConfig`를 생성한다.

HANDOFF 기준:
- `@Value("${cors.allowed-origins}")`
- `/api/**`
- methods: GET, POST, PUT, DELETE, PATCH
- headers: Authorization, Content-Type
- credentials true
- maxAge 3600

- [x] `application.yml`에 기본 CORS 환경변수 설정을 추가한다.

```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

- [x] `application-local.yml`에 로컬 whitelist를 추가한다.

```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173
```

- [x] CORS 테스트를 다시 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.CorsConfigTest"
```

---

## Task 7. dev/prod 프로파일 추가

**파일**
- 생성: `application-dev.yml`
- 생성: `application-prod.yml`

- [x] `application-dev.yml`을 생성한다.

포함:
- DB URL/user/password 환경변수 참조
- OAuth client id/secret 환경변수 참조
- `spring.jpa.show-sql: true`
- CORS dev domain whitelist

- [x] `application-prod.yml`을 생성한다.

포함:
- 모든 DB/OAuth 값 환경변수 참조
- `spring.jpa.show-sql: false`
- CORS 운영 도메인 whitelist만 포함
- wildcard `*` 금지

- [x] 설정 파일에 wildcard CORS가 없는지 확인한다.

```powershell
rg "allowed-origins: \\*|allowedOrigins\\(\"\\*\"\\)" src/main/resources src/main/java
```

---

## Task 8. 구조화 로깅 설정 추가

**파일**
- 수정: `build.gradle.kts`
- 생성: `logback-spring.xml`

- [x] `build.gradle.kts`에 Logstash encoder 의존성을 추가한다.

```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
```

- [x] `logback-spring.xml`을 생성한다.

필수:
- local profile: console text pattern
- non-local profile: JSON encoder
- JSON fields에 `traceId`, `spanId` MDC 포함

- [x] resource 로딩 검증을 위해 build를 실행한다.

```powershell
.\gradlew.bat build
```

---

## Task 9. 최종 정적 분석 및 전체 빌드

- [x] Checkstyle/SpotBugs를 명시 실행한다.

```powershell
.\gradlew.bat checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

- [x] 전체 build를 실행한다.

```powershell
.\gradlew.bat build
```

- [x] 테스트 수를 집계한다.

```powershell
$files = Get-ChildItem -LiteralPath build\test-results\test -Filter 'TEST-*.xml'
$tests = 0
$failures = 0
foreach ($file in $files) {
  [xml]$xml = Get-Content $file.FullName
  $tests += [int]$xml.testsuite.tests
  $failures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
}
"tests=$tests failures=$failures files=$($files.Count)"
```

- [x] 금지 패턴을 확인한다.

```powershell
rg "System\.out\.println|ObjectOutputStream|ObjectInputStream|HS256|refresh.*token|allowedOrigins\(\"\\*\"\)" src/main/java src/test/java src/main/resources
```

- [x] 완료 체크박스를 갱신한다.

대상:
- `docs/ai/current/TASK.md`
- `docs/ai/current/HANDOFF.md`
- `docs/ai/current/PLAN.md`

---

## 완료 기준 체크리스트

- [x] URI prefix가 `/api/v1/auth/callback`으로 변경됨
- [x] Success/Failure handler redirect가 새 URI를 사용함
- [x] SecurityConfig permitAll 경로가 새 URI를 허용함
- [x] RFC 7807 GlobalExceptionHandler가 동작함
- [x] BusinessException + OAuthProcessingException이 생성됨
- [x] User soft delete restriction이 적용됨
- [x] CORS whitelist 설정이 적용됨
- [x] Transaction propagation이 명시됨
- [x] Checkstyle + SpotBugs 게이트가 설치되고 통과함
- [x] dev/prod 프로파일이 생성됨
- [x] JSON 구조화 로깅 설정이 생성됨
- [x] `.\gradlew.bat build` 성공
- [x] 기존 25건 이상 테스트 + 신규 테스트 통과
