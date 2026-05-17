# SAMPLING — Step 3: JWT + MFA 기초

> **목적**: Step 3 구현 전, 별도 샘플 프로젝트에서 JWT/MFA 핵심 기술을 검증한다.
> 샘플링 결과를 바탕으로 본 프로젝트 설계 및 구현 방향을 확정한다.

---

## 샘플링 환경

| 항목 | 내용 |
|------|------|
| 기반 프로젝트 | synapse-platform-svc 복사본 |
| 기술 스택 | Spring Boot 4.0.0 + Java 21 + Spring Modulith 1.3.0 |
| 빌드 | Gradle (Kotlin DSL) |
| 목표 기간 | 1일 |

---

## 샘플링 목표 전체 목록

| # | 항목 | 리스크 | 핵심 검증 포인트 |
|---|------|--------|----------------|
| A | jjwt 0.12.6 RS256 동작 | HIGH | API 변화, Spring Boot 4 호환성 |
| B | RS256 키 페어 env var 바인딩 | HIGH | PEM → RSAKey 객체 로드 |
| C | dev.samstevens.totp TOTP | MEDIUM | RFC 6238 플로우, 타이밍 안전 비교 |
| D | Spring Data Redis + StringRedisTemplate | MEDIUM | Spring Boot 4 자동설정, TTL CRUD |
| E | JwtAuthenticationFilter | LOW | OncePerRequestFilter + SecurityContext |

---

## A. jjwt 0.12.6 RS256 동작 검증

### 목적

jjwt는 0.11.x → 0.12.x에서 API가 대폭 변경됨.
Spring Boot 4 + Java 21 환경에서 RS256 토큰 생성/파싱/검증이 정상 동작하는지 확인.

### 의존성

```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

### 검증 항목

- [ ] `Jwts.builder()` 0.12.x API로 RS256 Access Token 생성 성공
- [ ] `kid` 헤더 포함 토큰 생성 (`header().add("kid", ...)`)
- [ ] claims: `sub`, `iss`, `iat`, `exp`, `roles`, `type` 포함 확인
- [ ] `Jwts.parser().verifyWith(publicKey)` 로 토큰 검증 성공
- [ ] 만료 토큰 파싱 시 `ExpiredJwtException` 발생 확인
- [ ] 서명 불일치 토큰 파싱 시 `JwtException` 발생 확인
- [ ] Spring Boot 4 / Java 21 빌드 성공 확인

### 검증 코드 패턴

```java
// Access Token 생성 (0.12.x API)
String token = Jwts.builder()
        .header().add("kid", "synapse-key-2026-05").and()
        .subject(userId.toString())
        .issuer("synapse-auth")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000))
        .claim("roles", List.of("MEMBER"))
        .claim("type", "ACCESS")
        .signWith(privateKey)   // 0.12.x: algorithm 자동 감지
        .compact();

// 검증
Claims claims = Jwts.parser()
        .verifyWith(publicKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
```

### 기대 결과

| 케이스 | 기대 결과 |
|--------|----------|
| 정상 토큰 | claims 정상 파싱 |
| 만료 토큰 | `ExpiredJwtException` |
| 위조 토큰 | `JwtException` |

---

## B. RS256 키 페어 env var 바인딩 검증

### 목적

RSA 키를 Base64 인코딩 PEM 문자열로 환경변수에 저장하고,
Spring Boot `@ConfigurationProperties`로 `RSAPrivateKey` / `RSAPublicKey` 객체를 로드하는 방법 확인.

### 검증 항목

- [ ] OpenSSL로 RSA 2048 키 페어 생성 후 Base64 단일 라인 인코딩 확인
- [ ] `application-local.yml`에 Base64 PEM 키 설정 패턴 확인
- [ ] `@ConfigurationProperties`로 문자열 → `RSAPrivateKey` 변환 성공
- [ ] `@ConfigurationProperties`로 문자열 → `RSAPublicKey` 변환 성공
- [ ] 변환된 키로 A 항목의 토큰 생성/검증 성공

### 키 생성 명령어 (샘플링 시 참고)

```bash
# RSA 2048 private key
openssl genrsa -out private.pem 2048
# public key 추출
openssl rsa -in private.pem -pubout -out public.pem
# Base64 단일 라인 (환경변수용)
base64 -w 0 private.pem   # Linux
# Windows: certutil -encode private.pem private.b64
```

### application-local.yml 설정 패턴

```yaml
jwt:
  private-key: ${JWT_PRIVATE_KEY:MIIEv...base64...}
  public-key: ${JWT_PUBLIC_KEY:MIIBIj...base64...}
  kid: synapse-key-2026-05
  issuer: synapse-auth
```

### 변환 코드 패턴

```java
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        String kid,
        String issuer
) {
    public RSAPrivateKey rsaPrivateKey() {
        byte[] decoded = Base64.getDecoder().decode(
                privateKey.replaceAll("-----.*-----|\n|\r", ""));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public RSAPublicKey rsaPublicKey() {
        byte[] decoded = Base64.getDecoder().decode(
                publicKey.replaceAll("-----.*-----|\n|\r", ""));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
```

### 기대 결과

| 케이스 | 기대 결과 |
|--------|----------|
| 정상 Base64 PEM | `RSAPrivateKey` / `RSAPublicKey` 객체 생성 성공 |
| 잘못된 Base64 | `InvalidKeySpecException` |
| 해당 키로 서명/검증 | A 항목과 연계 정상 동작 |

---

## C. dev.samstevens.totp TOTP 플로우 검증

### 목적

`dev.samstevens.totp:totp:1.7.1` 라이브러리로 RFC 6238 TOTP 생성/검증이
Spring Boot 4 환경에서 정상 동작하는지 확인.
타이밍 공격 방지를 위한 `MessageDigest.isEqual()` 사용 패턴 확인.

### 의존성

```kotlin
implementation("dev.samstevens.totp:totp:1.7.1")
```

### 검증 항목

- [ ] Spring Boot 4 / Java 21 빌드 성공 (의존성 충돌 없음)
- [ ] 20바이트 Base32 시크릿 생성 성공
- [ ] `otpauth://totp/` 형식 QR URL 생성 확인 (issuer, account 포함)
- [ ] 현재 시간 기준 6자리 TOTP 코드 생성 성공
- [ ] 생성된 코드 검증 성공 (`±1 step` 허용 오차 적용)
- [ ] 잘못된 코드 검증 실패 확인
- [ ] `MessageDigest.isEqual()` 기반 타이밍 안전 비교 동작 확인

### 검증 코드 패턴

```java
// 시크릿 생성
SecretGenerator secretGenerator = new DefaultSecretGenerator(20);
String secret = secretGenerator.generate();  // Base32

// QR URL 생성
QrData qrData = new QrData.Builder()
        .label("user@example.com")
        .secret(secret)
        .issuer("Synapse")
        .digits(6)
        .period(30)
        .build();
String otpAuthUri = qrData.getUri();  // otpauth://totp/...

// 코드 검증 (타이밍 안전)
TimeProvider timeProvider = new SystemTimeProvider();
CodeGenerator codeGenerator = new DefaultCodeGenerator();
CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
verifier.setAllowedTimePeriodDiscrepancy(1);  // ±1 step
boolean valid = verifier.isValidCode(secret, inputCode);
```

### 기대 결과

| 케이스 | 기대 결과 |
|--------|----------|
| 현재 시간 코드 검증 | `true` |
| 잘못된 코드 | `false` |
| 의존성 충돌 | 없어야 함 |

---

## D. Spring Data Redis + StringRedisTemplate 검증

### 목적

Spring Boot 4 환경에서 `spring-boot-starter-data-redis` 자동설정이 올바르게 동작하고,
`StringRedisTemplate`으로 `refresh:{userId}` 키의 TTL 기반 CRUD가 정상 동작하는지 확인.

### 의존성

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

### 검증 항목

- [ ] `spring-boot-starter-data-redis` 추가 후 Spring Boot 4 빌드 성공
- [ ] Redis 자동설정으로 `StringRedisTemplate` Bean 주입 성공
- [ ] `refresh:{userId}` 키로 Refresh Token 저장 + TTL 7일 설정 성공
- [ ] 저장된 값 조회 성공 (`opsForValue().get()`)
- [ ] 키 삭제 성공 (`delete()`)
- [ ] TTL 조회로 만료 시간 설정 확인 (`getExpire()`)
- [ ] 존재하지 않는 키 조회 시 `null` 반환 확인
- [ ] `application-local.yml` Redis 설정 패턴 확인

### application-local.yml 설정 패턴

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 검증 코드 패턴

```java
// 저장 (TTL 7일)
redisTemplate.opsForValue().set(
        "refresh:" + userId,
        refreshToken,
        7, TimeUnit.DAYS
);

// 조회
String stored = redisTemplate.opsForValue().get("refresh:" + userId);

// TTL 확인
Long ttl = redisTemplate.getExpire("refresh:" + userId, TimeUnit.SECONDS);

// 삭제 (로그아웃/재발급 시)
redisTemplate.delete("refresh:" + userId);
```

### 테스트 환경

```java
// Testcontainers Redis로 통합 테스트
@Testcontainers
class RefreshTokenServiceTest {
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7").withExposedPorts(6379);
}
// 또는 embedded-redis 사용 가능 여부 확인
```

### 기대 결과

| 케이스 | 기대 결과 |
|--------|----------|
| 저장 후 조회 | 저장값 반환 |
| TTL 설정 | 7일 근사값 확인 |
| 삭제 후 조회 | `null` |
| 없는 키 조회 | `null` |

---

## E. JwtAuthenticationFilter 검증

### 목적

`OncePerRequestFilter` 기반 JWT 검증 필터가 Spring Security 7.0.0 (Spring Boot 4) 환경에서
올바르게 동작하고, SecurityContextHolder에 Authentication이 설정되는지 확인.

### 검증 항목

- [ ] `OncePerRequestFilter` 구현 후 `SecurityFilterChain`에 등록 성공
- [ ] `Authorization: Bearer {token}` 헤더 추출 성공
- [ ] 유효한 토큰 → `SecurityContextHolder`에 `Authentication` 설정 확인
- [ ] 토큰 없음 → 필터 통과 (다음 필터로 이동)
- [ ] 만료/위조 토큰 → 401 응답 확인
- [ ] `permitAll()` 경로는 토큰 없이 접근 가능 확인
- [ ] 인증 필요 경로는 유효 토큰 없이 403 확인

### SecurityConfig 통합 패턴

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/auth/refresh",
                            "/api/v1/auth/callback",
                            "/oauth2/**", "/login/**", "/actuator/**").permitAll()
                    .anyRequest().authenticated())
            .build();
}
```

### 검증 코드 패턴

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtService.validate(token)) {
            Authentication auth = jwtService.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (bearer != null && bearer.startsWith("Bearer "))
                ? bearer.substring(7) : null;
    }
}
```

### 기대 결과

| 케이스 | 기대 결과 |
|--------|----------|
| 유효 토큰 + 인증 필요 경로 | 200 |
| 토큰 없음 + permitAll 경로 | 200 |
| 토큰 없음 + 인증 필요 경로 | 401 또는 302 |
| 만료/위조 토큰 | 401 |

---

## 샘플링 결과 기록 양식

샘플링 완료 후 아래 표를 채워 `SAMPLING_STEP3_RESULT.md`에 기록한다.

| 항목 | 결과 | 확정 내용 | 이슈 |
|------|------|-----------|------|
| A. jjwt RS256 | ✅ / ❌ | | |
| B. 키 바인딩 | ✅ / ❌ | | |
| C. TOTP | ✅ / ❌ | | |
| D. Redis | ✅ / ❌ | | |
| E. Filter | ✅ / ❌ | | |

---

## 샘플링 후 확정할 항목

- jjwt 0.12.x API 확정 패턴 (builder, parser)
- `JwtProperties` record 최종 필드 구성
- TOTP 라이브러리 최종 확정 (또는 대안)
- Redis Testcontainers vs embedded-redis 테스트 방식 결정
- SecurityConfig 필터 체인 최종 순서
