# 6. 인증/토큰 RULE — Auth & Token

> **참조**: [전체 Rule 목록](../rules/) | [준수 체크리스트](appendix-c-checklist.md)

---

## 6.1 JWT \[MUST\]

### 토큰 사양

| 항목 | 값 | 비고 |
|---|---|---|
| Access Token TTL | **15분** | 짧게 유지해서 탈취 피해 최소화 |
| Refresh Token TTL | **7일** | 재발급 시 이전 Refresh 즉시 무효화 |
| 서명 알고리즘 | **RS256** (RSA-SHA256) | 대칭키(HS256) 금지 |
| 헤더 필수 필드 | `kid` (Key ID) | 키 로테이션 시 어떤 키로 서명했는지 식별 |

### 토큰 구조 예시

```
Header:
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "synapse-key-2026-05"
}

Payload:
{
  "sub": "user-uuid-1234",
  "iss": "synapse-auth",
  "iat": 1747036800,
  "exp": 1747037700,       // +15분
  "roles": ["MEMBER"],
  "type": "ACCESS"
}
```

### 구현 예시

```java
// ✅ Good — RS256 + kid 헤더 + 짧은 TTL
@Component
public class JwtTokenProvider {

    private final RSAPrivateKey privateKey;
    private final String currentKid;

    public String createAccessToken(UserPrincipal user) {
        return Jwts.builder()
                .setHeaderParam("kid", currentKid)
                .setSubject(user.getId().toString())
                .setIssuer("synapse-auth")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000))
                .claim("roles", user.getRoles())
                .claim("type", "ACCESS")
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String createRefreshToken(UserPrincipal user) {
        return Jwts.builder()
                .setHeaderParam("kid", currentKid)
                .setSubject(user.getId().toString())
                .setIssuer("synapse-auth")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
                .claim("type", "REFRESH")
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
}
```

```java
// ❌ Bad — HS256 + 하드코딩 시크릿 + 긴 TTL
public String createToken(String userId) {
    return Jwts.builder()
            .setSubject(userId)
            .setExpiration(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30일
            .signWith(SignatureAlgorithm.HS256, "my-secret-key")
            .compact();
}
```

> **이유**: HS256은 서버 간 시크릿 공유가 필요해서 MSA에서 관리가 어렵고, 긴 TTL은 토큰 탈취 시 피해 기간을 늘려.

---

## 6.2 OAuth \[MUST\]

### 지원 소셜 로그인

| Provider | 용도 | 필수 스코프 |
|---|---|---|
| **Google** | 일반 사용자 로그인 | `openid`, `email`, `profile` |
| **GitHub** | 개발자 로그인 | `read:user`, `user:email` |

### PKCE Flow \[SHOULD\]

SPA/모바일 클라이언트는 Authorization Code + PKCE flow를 사용해.
`code_verifier` → SHA256 해시 → `code_challenge`로 CSRF/인터셉트 공격 방지.

### Spring Security OAuth2 설정 예시

```yaml
# ✅ Good — application.yml OAuth2 설정
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user, user:email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
```

```java
// ✅ Good — OAuth2 로그인 성공 후 JWT 발급
@Component
public class OAuth2AuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        UserPrincipal user = userService.processOAuth2User(oAuth2User);

        String accessToken = tokenProvider.createAccessToken(user);
        String refreshToken = tokenProvider.createRefreshToken(user);

        String redirectUrl = UriComponentsBuilder.fromUriString(clientRedirectUri)
                .queryParam("access_token", accessToken)
                .queryParam("refresh_token", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
```

```java
// ❌ Bad — client-secret을 코드에 하드코딩
@Bean
public ClientRegistration googleClientRegistration() {
    return ClientRegistration.withRegistrationId("google")
            .clientId("123456.apps.googleusercontent.com")
            .clientSecret("GOCSPX-hardcoded-secret-here")  // 절대 안 됨
            .build();
}
```

> **이유**: OAuth client-secret이 코드에 노출되면 공격자가 우리 앱으로 위장할 수 있어. 환경변수/시크릿 매니저에서 주입해야 해.

---

## 6.3 MFA \[SHOULD\]

### TOTP 사양

| 항목 | 값 |
|---|---|
| 알고리즘 | HMAC-SHA1 (RFC 6238) |
| 자릿수 | **6자리** |
| 유효 시간 | **30초** |
| 허용 오차 | 전후 1 스텝 (±30초) |
| 복구 코드 | **10개** 발급, 일회용 |

### 구현 예시

```java
// ✅ Good — TOTP 등록 + 검증
@Service
public class TotpService {

    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int ALLOWED_SKEW = 1;

    public TotpSetupResponse setupTotp(Long userId) {
        String secretKey = generateSecretKey();  // Base32 인코딩된 20바이트
        List<String> recoveryCodes = generateRecoveryCodes(10);

        totpRepository.save(new TotpCredential(userId, secretKey, recoveryCodes));

        String otpAuthUri = String.format(
            "otpauth://totp/Synapse:%s?secret=%s&issuer=Synapse&digits=%d&period=%d",
            userEmail, secretKey, CODE_DIGITS, TIME_STEP_SECONDS
        );

        return new TotpSetupResponse(otpAuthUri, recoveryCodes);
    }

    public boolean verifyCode(Long userId, String code) {
        TotpCredential credential = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new MfaNotConfiguredException());

        long currentInterval = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;

        for (int i = -ALLOWED_SKEW; i <= ALLOWED_SKEW; i++) {
            String expected = generateTotp(credential.getSecretKey(), currentInterval + i);
            if (MessageDigest.isEqual(expected.getBytes(), code.getBytes())) {
                return true;
            }
        }
        return false;
    }

    private List<String> generateRecoveryCodes(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> RandomStringUtils.randomAlphanumeric(8).toUpperCase())
                .collect(Collectors.toList());
    }
}
```

```java
// ❌ Bad — MFA 코드를 단순 equals로 비교 (타이밍 공격 취약)
if (inputCode.equals(expectedCode)) {
    return true;
}
```

> **이유**: `String.equals()`는 첫 불일치 지점에서 바로 리턴해서 타이밍 사이드채널 공격에 취약해. 반드시 `MessageDigest.isEqual()` 같은 상수 시간 비교를 써야 해.

---

## 6.4 세션/블랙리스트 \[MUST\]

### Redis 기반 토큰 블랙리스트

로그아웃하면 해당 Access Token을 **즉시 Redis 블랙리스트에 등록**해.
남은 TTL 만큼만 Redis에 보관하면 되니까 메모리 낭비 없어.

| 동작 | Redis 키 패턴 | TTL |
|---|---|---|
| 로그아웃 | `blacklist:{jti}` | Access Token 남은 만료 시간 |
| Refresh 무효화 | `refresh:revoked:{jti}` | Refresh Token 남은 만료 시간 |
| 활성 세션 추적 | `session:{userId}:{deviceId}` | 7일 |

### Redis 블랙리스트 구현

```java
// ✅ Good — 로그아웃 시 즉시 블랙리스트 등록
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider tokenProvider;

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String REFRESH_REVOKED_PREFIX = "refresh:revoked:";

    public void logout(String accessToken, String refreshToken) {
        // Access Token 블랙리스트 등록
        Claims accessClaims = tokenProvider.parseClaims(accessToken);
        String accessJti = accessClaims.getId();
        long accessTtl = accessClaims.getExpiration().getTime() - System.currentTimeMillis();

        if (accessTtl > 0) {
            redisTemplate.opsForValue()
                    .set(BLACKLIST_PREFIX + accessJti, "logout",
                         accessTtl, TimeUnit.MILLISECONDS);
        }

        // Refresh Token 무효화
        Claims refreshClaims = tokenProvider.parseClaims(refreshToken);
        String refreshJti = refreshClaims.getId();
        long refreshTtl = refreshClaims.getExpiration().getTime() - System.currentTimeMillis();

        if (refreshTtl > 0) {
            redisTemplate.opsForValue()
                    .set(REFRESH_REVOKED_PREFIX + refreshJti, "revoked",
                         refreshTtl, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isBlacklisted(String token) {
        Claims claims = tokenProvider.parseClaims(token);
        String jti = claims.getId();
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
```

### JWT 필터에서 블랙리스트 체크

```java
// ✅ Good — 요청마다 블랙리스트 확인
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && tokenProvider.validateToken(token)) {
            if (blacklistService.isBlacklisted(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                return;
            }

            Authentication auth = tokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

```java
// ❌ Bad — 블랙리스트 체크 없이 토큰 유효성만 확인
if (token != null && tokenProvider.validateToken(token)) {
    Authentication auth = tokenProvider.getAuthentication(token);
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

> **이유**: 블랙리스트 체크가 없으면 로그아웃한 사용자의 토큰이 만료될 때까지 최대 15분간 유효해. 계정 탈취 시 즉시 대응이 불가능해져.

### 전 기기 로그아웃

```java
// ✅ Good — 특정 사용자의 모든 세션 무효화
public void logoutAllDevices(Long userId) {
    Set<String> sessionKeys = redisTemplate.keys("session:" + userId + ":*");
    if (sessionKeys != null && !sessionKeys.isEmpty()) {
        redisTemplate.delete(sessionKeys);
    }
    // 해당 사용자의 모든 Refresh Token도 무효화
    refreshTokenRepository.revokeAllByUserId(userId);
}
```

```java
// ❌ Bad — DB에서만 세션 삭제하고 Redis 캐시는 그대로
public void logoutAll(Long userId) {
    sessionRepository.deleteByUserId(userId);  // Redis에는 여전히 남아있음
}
```

> **이유**: Redis와 DB 둘 다 정리해야 해. 한쪽만 지우면 캐시된 세션으로 여전히 접근 가능해.

---

_마지막 업데이트: 2026-05-12_
