# HANDOFF — Step 2 룰북 준수 수정

> **FROM**: Director (Claude)
> **TO**: Worker (Codex)
> **날짜**: 2026-05-14
> **브랜치**: `feature/PLAT-004-oauth`
> **참조**: `docs/ai/current/CONTEXT.md`

---

## 요청 개요

Step 2 OAuth 구현 완료 후 룰북 전체 점검 결과, [MUST] 위반 7건 / [SHOULD] 위반 3건 발견.
아래 항목을 순서대로 수정하고 `./gradlew build` 성공을 완료 기준으로 한다.

**완료 기준**: `./gradlew build` 성공 + 기존 테스트 25건 유지 + 신규 테스트 통과

---

## 수정 범위

### [MUST] M-1: URI prefix 추가 (규칙 2.1)

**파일**: `src/main/java/com/synapse/platform/auth/AuthCallbackController.java`

```java
// Before
@RequestMapping("/auth")
public class AuthCallbackController {
    @GetMapping("/callback")

// After
@RequestMapping("/api/v1/auth")
public class AuthCallbackController {
    @GetMapping("/callback")
```

**파일**: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`

```java
// permitAll 경로 수정
.requestMatchers("/actuator/**", "/oauth2/**", "/login/**", "/api/v1/auth/callback").permitAll()
```

**파일**: `src/main/java/com/synapse/platform/auth/oauth/OAuth2SuccessHandler.java`

```java
// Before
response.sendRedirect("/auth/callback?userId=" + userId);

// After
response.sendRedirect("/api/v1/auth/callback?userId=" + userId);
```

**파일**: `src/main/java/com/synapse/platform/auth/oauth/OAuth2FailureHandler.java`

```java
// Before
response.sendRedirect("/auth/callback?error=" + error);

// After
response.sendRedirect("/api/v1/auth/callback?error=" + error);
```

**주의**: 기존 테스트(`OAuth2LoginIntegrationTest`, `OAuth2SuccessHandlerTest`, `OAuth2FailureHandlerTest` 등)에서 `/auth/callback` 경로를 직접 참조하는 곳도 함께 수정.

---

### [MUST] M-2 + M-5: GlobalExceptionHandler 생성 (규칙 2.3, 7.1.4)

**신규 파일**: `src/main/java/com/synapse/platform/shared/exception/BusinessException.java`

```java
package com.synapse.platform.shared.exception;

public abstract class BusinessException extends RuntimeException {
    private final String errorCode;
    private final int status;

    protected BusinessException(String errorCode, int status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public int getStatus() { return status; }
}
```

**신규 파일**: `src/main/java/com/synapse/platform/shared/exception/GlobalExceptionHandler.java`

RFC 7807 형식 에러 응답:
```json
{
  "type": "https://api.synapse.app/errors/PLAT-001",
  "title": "Bad Request",
  "status": 400,
  "detail": "에러 설명",
  "code": "PLAT-001",
  "traceId": "trace-xyz"
}
```

`ErrorResponse` record + `@RestControllerAdvice` 구현.
처리 대상: `BusinessException`, `MethodArgumentNotValidException`, `Exception` (fallback).

**AuthCallbackController 수정**: 현재 `Map.of("error", ...)` 반환을 RFC 7807 형식으로 교체.
단, OAuth 콜백은 redirect 결과 표시 용도이므로 `400 Bad Request` 응답은 유지.

---

### [MUST] M-3: User 엔티티 @SQLRestriction (규칙 2.4)

**파일**: `src/main/java/com/synapse/platform/auth/domain/User.java`

```java
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User {
```

---

### [MUST] M-4: CorsConfig 생성 (규칙 1.3)

**신규 파일**: `src/main/java/com/synapse/platform/auth/config/CorsConfig.java`

```java
package com.synapse.platform.auth.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

**application.yml 수정**: 환경변수 기반 설정 추가.

```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

**application-local.yml 수정**: 로컬용 오버라이드 추가.

```yaml
cors:
  allowed-origins:
    - http://localhost:3000
    - http://localhost:5173
```

---

### [MUST] M-6: Checkstyle + SpotBugs 설정 (규칙 4.4)

**파일**: `build.gradle.kts`

```kotlin
plugins {
    // 기존 플러그인들...
    checkstyle
    id("com.github.spotbugs") version "6.0.9"
}

checkstyle {
    toolVersion = "10.12.5"
    configFile = file("config/checkstyle/checkstyle.xml")
}

spotbugs {
    toolVersion = "4.8.3"
    excludeFilter = file("config/spotbugs/exclude.xml")
}
```

**신규 파일**: `config/checkstyle/checkstyle.xml` — Google Java Style 기반 최소 설정.
**신규 파일**: `config/checkstyle/suppressions.xml` — 기존 파일 전체 suppress (신규 파일만 게이트 적용).
**신규 파일**: `config/spotbugs/exclude.xml` — 기존 코드 버그 패턴 exclude.

**suppression 전략**: `config/checkstyle/suppressions.xml`에서 기존 파일 전체를 파일 패턴으로 일괄 격리.

```xml
<!-- suppressions.xml -->
<suppressions>
    <!-- 기존 코드 전체 격리 — 신규 파일만 게이트 적용 -->
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

신규 파일(`GlobalExceptionHandler`, `CorsConfig`, `BusinessException`, `OAuthProcessingException`, `logback-spring.xml`)은 suppression 없이 작성하여 위반 0건 목표. 빌드 게이트 설치가 목적이며 기존 코드 전수 수정은 Out of Scope.

---

### [MUST] M-7: @Transactional propagation 명시 (규칙 3.3)

**파일**: `src/main/java/com/synapse/platform/auth/oauth/CustomOAuth2UserService.java`

```java
import org.springframework.transaction.annotation.Propagation;

// Before
@Transactional

// After
@Transactional(propagation = Propagation.REQUIRED)
```

---

### [SHOULD] S-1: BusinessException 도메인 예외 (규칙 3.6)

M-2에서 생성한 `BusinessException`을 base로, auth 도메인 예외 1건 예시 추가.

**신규 파일**: `src/main/java/com/synapse/platform/auth/exception/OAuthProcessingException.java`

```java
package com.synapse.platform.auth.exception;

import com.synapse.platform.shared.exception.BusinessException;

public class OAuthProcessingException extends BusinessException {
    public OAuthProcessingException(String message) {
        super("PLAT-001", 400, message);
    }
}
```

---

### [SHOULD] S-2: 환경 프로파일 추가 (규칙 7.0.2)

**신규 파일**: `src/main/resources/application-dev.yml`
**신규 파일**: `src/main/resources/application-prod.yml`

staging은 인프라팀 담당 범위이므로 Out of Scope. dev/prod 2개만 생성.

`dev`: DB/OAuth 환경변수 참조, `show-sql: true`, CORS에 dev 도메인 추가.
`prod`: 모든 값 환경변수 참조, `show-sql: false`, CORS에 운영 도메인만.

---

### [SHOULD] S-3: 구조화 로깅 설정 (규칙 9.1)

**신규 파일**: `src/main/resources/logback-spring.xml`

local 프로파일 → 텍스트 포맷, 그 외 → JSON (LogstashEncoder).
`traceId`, `spanId` MDC 자동 주입.

**build.gradle.kts 의존성 추가**:
```kotlin
implementation("net.logstash.logback:logstash-logback-encoder:7.4")
```

---

## 주의사항

1. **패키지 구조**: `GlobalExceptionHandler`는 `com.synapse.platform.shared.exception` 패키지에 위치 (모듈 공통)
2. **기존 테스트 유지**: URI 변경으로 인해 `/auth/callback` 참조하는 테스트 전체 수정 필수
3. **RFC 7807 traceId**: `HttpServletRequest`에서 `request.getAttribute("traceId")` 또는 UUID 생성으로 대체 가능 (Step 9 전까지는 UUID 대체 허용)
4. **Checkstyle 위반**: 기존 코드가 Checkstyle 위반이 있을 수 있음 — Warning 0건 목표이므로 발견된 위반도 함께 수정
5. **application-local.yml 비밀번호**: `password: synapse123` — 로컬 전용이므로 유지 가능

---

## 완료 조건

```
[x] AuthCallbackController URI → /api/v1/auth/callback
[x] SecurityConfig permitAll 경로 수정
[x] GlobalExceptionHandler + RFC 7807 ErrorResponse
[x] BusinessException 추상 계층 + OAuthProcessingException 예시
[x] User @SQLRestriction("deleted_at IS NULL")
[x] CorsConfig + application.yml cors 설정
[x] @Transactional(propagation = Propagation.REQUIRED) 명시
[x] Checkstyle + SpotBugs build.gradle.kts 설정
[x] application-dev.yml, application-prod.yml 생성
[x] logback-spring.xml JSON 포맷 설정
[x] ./gradlew build 성공 (기존 25건 + 신규 테스트 통과)
```

## 필요한 출력 형식

```
## 구현 완료 보고
### 생성/수정 파일 목록
### 테스트 결과
### Checkstyle/SpotBugs 결과
### 특이사항
```

## 첨부할 파일

- `docs/ai/agent/worker.md`
- `docs/ai/current/CONTEXT.md`

## 기한

2026-05-14
