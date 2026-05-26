# HANDOFF

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Director (Claude)

## TO

Worker (Codex)

## DATE

2026-05-26

## SUBJECT

Refresh Token → HttpOnly Cookie 전환 (D-028)

---

## 배경

OAuth 로그인 성공 시 현재 Access Token + Refresh Token을 모두 redirect query string으로 전달하고 있다.
프론트엔드 팀과 합의하여 아래 방식으로 전환한다.

- Access Token → redirect query string 유지 (JS가 읽어서 flutter_secure_storage 저장)
- Refresh Token → HttpOnly Cookie (JS 접근 불가, XSS 방어)

결정 근거: DECISION_LOG.md D-028 참고.

---

## 브랜치

`feature/PLAT-008-httponly-refresh-cookie` (이미 생성됨, dev 기준)

---

## 변경 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `src/main/java/com/synapse/platform/auth/service/OAuth2SuccessHandler.java` | Refresh Token Cookie Set, redirect에서 refresh_token 제거 |
| `src/main/java/com/synapse/platform/auth/controller/AuthController.java` | `/refresh`: RequestBody → @CookieValue, response에서 refreshToken 제거 후 Cookie Set |
| `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java` | CORS allowCredentials + 명시적 origin 추가 |
| `src/main/resources/application.yml` | forward-headers-strategy, cookie/cors 기본값 추가 |
| `src/main/resources/application-prod.yml` | prod 쿠키 설정 (SameSite=None; Secure) |

---

## 구현 명세

### 1. OAuth2SuccessHandler.java

현재 코드:
```java
String redirectUrl = UriComponentsBuilder.fromUriString(clientRedirectUri)
        .queryParam("access_token", accessToken)
        .queryParam("refresh_token", refreshToken)
        .build().toUriString();
response.sendRedirect(redirectUrl);
```

변경 후:
```java
// Refresh Token → HttpOnly Cookie
ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
        .httpOnly(true)
        .path("/api/v1/auth")
        .maxAge(Duration.ofDays(7))
        .sameSite(sameSite)
        .secure(secure)
        .build();
response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

// Access Token만 redirect
String redirectUrl = UriComponentsBuilder.fromUriString(clientRedirectUri)
        .queryParam("access_token", accessToken)
        .build().toUriString();
response.sendRedirect(redirectUrl);
```

`sameSite`, `secure`는 `@Value`로 주입:
```java
@Value("${app.cookie.same-site}") String sameSite
@Value("${app.cookie.secure}") boolean secure
```

---

### 2. AuthController.java — `/refresh` 엔드포인트

현재:
```java
@PostMapping("/refresh")
public TokenRefreshResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
    String refreshToken = request.refreshToken();
    ...
    return new TokenRefreshResponse(newAccessToken, newRefreshToken);
}

public record TokenRefreshRequest(@NotBlank String refreshToken) {}
public record TokenRefreshResponse(String accessToken, String refreshToken) {}
```

변경 후:
```java
@PostMapping("/refresh")
public ResponseEntity<TokenRefreshResponse> refresh(
        @CookieValue(name = "refresh_token", required = false) String refreshToken,
        HttpServletResponse response) {

    if (refreshToken == null || refreshToken.isBlank()) {
        throw new UnauthorizedTokenException("Refresh token cookie missing");
    }
    if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
        throw new UnauthorizedTokenException("Invalid refresh token");
    }

    UUID userId = jwtTokenProvider.getUserId(refreshToken);
    if (!refreshTokenService.isValid(userId, refreshToken)) {
        throw new UnauthorizedTokenException("Refresh token does not match stored token");
    }

    String newAccessToken = jwtTokenProvider.createAccessToken(userId, AuthRoles.DEFAULT_USER_ROLES);
    String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
    refreshTokenService.rotate(userId, refreshToken, newRefreshToken);

    // 새 Refresh Token → Cookie 교체
    ResponseCookie newRefreshCookie = ResponseCookie.from("refresh_token", newRefreshToken)
            .httpOnly(true)
            .path("/api/v1/auth")
            .maxAge(Duration.ofDays(7))
            .sameSite(sameSite)
            .secure(secure)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, newRefreshCookie.toString());

    return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken));
}

// refreshToken 필드 제거
public record TokenRefreshResponse(String accessToken) {}
// TokenRefreshRequest record 삭제
```

`sameSite`, `secure`는 `@Value`로 주입 (OAuth2SuccessHandler와 동일하게).

---

### 3. SecurityConfig.java — CORS 추가

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

Bean 추가:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins); // @Value("${app.cors.allowed-origins}")
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

### 4. application.yml 추가 설정

```yaml
server:
  forward-headers-strategy: native

app:
  cors:
    allowed-origins: http://127.0.0.1:8088
  cookie:
    same-site: Lax
    secure: false
```

### 5. application-prod.yml 추가

```yaml
app:
  cors:
    allowed-origins: https://app.synapse.io
  cookie:
    same-site: None
    secure: true
```

---

## 테스트 작성 필수

아래 케이스를 포함하는 통합 테스트 또는 단위 테스트를 작성한다.

| 케이스 | 검증 포인트 |
|--------|-----------|
| OAuth 로그인 성공 | `Set-Cookie` 헤더에 `refresh_token` 존재, `HttpOnly` 확인, redirect URL에 `refresh_token` 쿼리 파라미터 없음 |
| `POST /api/v1/auth/refresh` — 정상 | Cookie에서 refresh_token 읽어 처리, response body에 `accessToken`만 존재, 새 `Set-Cookie` 헤더 존재 |
| `POST /api/v1/auth/refresh` — Cookie 없음 | 401 반환 |
| `POST /api/v1/auth/refresh` — 유효하지 않은 토큰 | 401 반환 |

기존 `TokenRefreshRequest` body 방식 테스트는 삭제한다.

---

## 제약 사항

- 쿠키 `path`는 `/api/v1/auth`로 제한 (다른 경로 요청 시 자동 전송 방지)
- `TokenRefreshResponse`에서 `refreshToken` 필드 제거 필수
- `TokenRefreshRequest` record 삭제 필수 (더 이상 사용 안 함)
- 테스트 커버리지 80% 이상 유지
- 빌드 후 `./gradlew test` 전체 통과 확인

---

## 필요한 출력 형식

구현 완료 후 아래 항목 기록:

- [ ] 변경된 파일 목록
- [ ] 추가/삭제된 테스트 목록
- [ ] `./gradlew test` 결과 (통과 여부)
- [ ] 빌드 성공 여부

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-26
