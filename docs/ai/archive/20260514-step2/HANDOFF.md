# HANDOFF — Step 2 잔여: OAuth2AuthenticationFailureHandler 구현

> **FROM**: Director (Claude)
> **TO**: Worker (Codex)
> **날짜**: 2026-05-14
> **브랜치**: `feature/PLAT-004-oauth`
> **참조**: `docs/ai/current/CONTEXT.md`

---

## 요청 개요

Step 2 WORKFLOW 1.9에 미구현으로 남은 `OAuth2AuthenticationFailureHandler`를 추가한다.
SuccessHandler의 `/auth/callback?userId=` 패턴과 대칭되도록 `/auth/callback?error=` 로 redirect한다.

**완료 기준**: `./gradlew build` 성공 + FailureHandler 테스트 통과

---

## 구현 범위

### 1. OAuth2FailureHandler.java (신규)

패키지: `com.synapse.platform.auth.oauth`

```java
package com.synapse.platform.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String error = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
        response.sendRedirect("/auth/callback?error=" + error);
    }
}
```

### 2. SecurityConfig.java 수정

`oauth2Login` 블록에 `.failureHandler(oAuth2FailureHandler)` 추가.

```java
// 기존 생성자에 OAuth2FailureHandler 파라미터 추가
public SecurityConfig(
        CustomOAuth2UserService customOAuth2UserService,
        OAuth2SuccessHandler oAuth2SuccessHandler,
        OAuth2FailureHandler oAuth2FailureHandler,   // 추가
        ObjectMapper objectMapper) { ... }

// filterChain 내 oauth2Login 블록 수정
.oauth2Login(oauth2 -> oauth2
        .authorizationEndpoint(a -> a
                .authorizationRequestRepository(cookieAuthorizationRequestRepository()))
        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
        .successHandler(oAuth2SuccessHandler)
        .failureHandler(oAuth2FailureHandler))   // 추가
```

### 3. AuthCallbackController.java 수정

`error` 파라미터 처리 추가.

```java
@GetMapping("/callback")
public ResponseEntity<?> callback(
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String error) {
    if (error != null) {
        return ResponseEntity.badRequest().body(Map.of("error", error));
    }
    return ResponseEntity.ok(Map.of("userId", userId));
}
```

### 4. 테스트 — OAuth2FailureHandlerTest.java (신규)

패키지: `com.synapse.platform.auth`

```java
// MockHttpServletRequest/Response 사용
// 1건: onAuthenticationFailure 호출 시 /auth/callback?error=... 로 redirect 확인
```

---

## 주의사항

- `URLEncoder.encode`로 에러 메시지 URL 인코딩 필수 (특수문자 포함 가능)
- `AuthCallbackController`의 `userId` 파라미터를 `required = false`로 변경해야 `error` 단독 호출 가능

---

## 완료 조건

```
[x] OAuth2FailureHandler.java 생성
[x] SecurityConfig에 failureHandler 연결
[x] AuthCallbackController error 파라미터 처리
[x] OAuth2FailureHandlerTest 1건 통과
[x] ./gradlew build 성공
```

## 필요한 출력 형식

```
## 구현 완료 보고
### 생성/수정 파일 목록
### 테스트 결과
### 특이사항
```

## 첨부할 파일

- `docs/ai/agent/worker.md`
- `docs/ai/current/CONTEXT.md`

## 기한

2026-05-14
