# Step 3 JWT + MFA 기초 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OAuth 로그인 사용자에게 RS256 JWT Access/Refresh Token을 발급하고, Refresh Token rotation과 TOTP MFA 등록/검증 기초 API를 구현한다.

**Architecture:** auth 모듈에 JWT, Refresh Token, MFA 기능을 배치하고 shared 모듈에는 암호화 유틸리티만 둔다. Refresh Token은 Redis에만 저장하고, TOTP secret은 AES-256-GCM으로 암호화해 `totp_credentials` 테이블에 저장한다. 루트 애플리케이션은 `@ConfigurationPropertiesScan`만 사용해 auth 내부 타입 직접 참조로 인한 Modulith 경계 위반을 피한다.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Security, Spring Data Redis, JJWT 0.12.6, dev.samstevens.totp 1.7.1, Spring Data JPA, Flyway, Redis, AES-256-GCM, JUnit 5, Mockito, MockMvc, Testcontainers.

---

## 작업 원칙

- 구현 기준은 `docs/ai/current/HANDOFF.md`와 `docs/ai/current/CONTEXT.md`이다.
- 샘플링 확정 패턴은 `docs/spike/step3-jwt-mfa/SAMPLING_STEP3_RESULT.md`를 따른다.
- TASK.md의 TOTP 라이브러리 표기는 `GoogleAuth`로 되어 있지만, HANDOFF/CONTEXT/샘플링에서 확정된 `dev.samstevens.totp:totp:1.7.1`을 사용한다.
- HANDOFF의 `OAuth2AuthenticationSuccessHandler`는 현재 코드 기준으로 `OAuth2SuccessHandler`를 의미한다.
- `POST /api/v1/auth/refresh`, `POST /api/v1/auth/mfa/setup`, `POST /api/v1/auth/mfa/verify`는 룰 2.1의 동사 금지와 형식상 충돌하지만 Director가 API 계약으로 확정한 경로이므로 변경하지 않는다.
- 완료된 항목은 이 문서와 `docs/ai/current/TASK.md`, `docs/ai/current/HANDOFF.md`의 체크박스를 갱신한다.
- Refresh Token은 Redis 전용이다. DB 저장 파일/컬럼을 만들지 않는다.
- JWT는 RS256만 사용한다. HS256은 코드/테스트/문서에 구현 예외 없이 금지한다.
- TOTP secret은 평문 저장하지 않는다. 응답에서만 최초 1회 반환하고 DB에는 암호문만 저장한다.

---

## 파일 작업 범위

### 수정

- `build.gradle.kts`
- `src/main/java/com/synapse/platform/PlatformSvcApplication.java`
- `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- `src/main/java/com/synapse/platform/auth/oauth/OAuth2SuccessHandler.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- `src/test/resources/application.yml`
- `docs/ai/current/TASK.md`
- `docs/ai/current/HANDOFF.md`
- `docs/ai/current/PLAN.md`

### 생성

- `src/main/java/com/synapse/platform/auth/jwt/JwtProperties.java`
- `src/main/java/com/synapse/platform/auth/jwt/JwtTokenProvider.java`
- `src/main/java/com/synapse/platform/auth/jwt/RefreshTokenService.java`
- `src/main/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilter.java`
- `src/main/java/com/synapse/platform/auth/AuthController.java`
- `src/main/java/com/synapse/platform/auth/mfa/TotpCredential.java`
- `src/main/java/com/synapse/platform/auth/mfa/TotpCredentialRepository.java`
- `src/main/java/com/synapse/platform/auth/mfa/TotpService.java`
- `src/main/java/com/synapse/platform/auth/mfa/MfaController.java`
- `src/main/java/com/synapse/platform/auth/exception/UnauthorizedTokenException.java`
- `src/main/java/com/synapse/platform/auth/exception/MfaVerificationException.java`
- `src/main/java/com/synapse/platform/shared/crypto/FieldEncryptor.java`
- `src/main/java/com/synapse/platform/shared/crypto/package-info.java`
- `src/main/resources/db/migration/V19__create_totp_credentials.sql`
- `src/test/java/com/synapse/platform/auth/jwt/JwtTokenProviderTest.java`
- `src/test/java/com/synapse/platform/auth/jwt/RefreshTokenServiceTest.java`
- `src/test/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilterTest.java`
- `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`
- `src/test/java/com/synapse/platform/auth/mfa/TotpServiceTest.java`
- `src/test/java/com/synapse/platform/auth/mfa/MfaControllerTest.java`
- `src/test/java/com/synapse/platform/shared/crypto/FieldEncryptorTest.java`

---

## Task 1. 기준 상태와 의존성 추가

**Files**
- Modify: `build.gradle.kts`
- Modify: `src/main/java/com/synapse/platform/PlatformSvcApplication.java`
- Modify: `src/main/resources/application.yml`
- Modify: profile yaml files

- [x] 현재 브랜치와 작업 트리를 확인한다.

```powershell
git status --short --branch
```

기대:
- 브랜치가 `feature/PLAT-005-jwt-mfa`이다.
- 사용자가 만든 문서 변경 외 미확인 코드 변경이 있으면 건드리기 전에 확인한다.

- [x] 현재 기준 빌드를 확인한다.

```powershell
.\gradlew.bat build
```

기대:
- Step 2 기준 빌드가 성공한다.
- 실패 시 Step 3 구현 전에 원인을 분리한다.

- [x] `build.gradle.kts`에 JWT/TOTP/Redis/Testcontainers 의존성을 추가한다.

추가 대상:

```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
implementation("dev.samstevens.totp:totp:1.7.1")
implementation("org.springframework.boot:spring-boot-starter-data-redis")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:testcontainers")
```

- [x] 루트 애플리케이션에 `@ConfigurationPropertiesScan`을 추가한다.

금지:
- `@EnableConfigurationProperties(JwtProperties.class)` 사용 금지.

- [x] 설정 파일에 JWT, OAuth redirect, AES key 설정을 추가한다.

`application.yml`:

```yaml
app:
  oauth2:
    redirect-uri: ${APP_OAUTH2_REDIRECT_URI:http://localhost:3000/auth/callback}
  crypto:
    aes-secret-key: ${AES_SECRET_KEY}
```

`application-local.yml`:

```yaml
jwt:
  private-key: ${JWT_PRIVATE_KEY}
  public-key: ${JWT_PUBLIC_KEY}
  kid: synapse-key-2026-05
  issuer: synapse-auth
```

dev/prod/test에도 실행 가능한 값을 맞춘다.

- [x] 의존성/설정 추가 후 컴파일을 확인한다.

```powershell
.\gradlew.bat compileJava
```

---

## Task 2. JwtProperties와 JwtTokenProvider

**Files**
- Create: `src/main/java/com/synapse/platform/auth/jwt/JwtProperties.java`
- Create: `src/main/java/com/synapse/platform/auth/jwt/JwtTokenProvider.java`
- Test: `src/test/java/com/synapse/platform/auth/jwt/JwtTokenProviderTest.java`

- [x] `JwtTokenProviderTest`를 먼저 작성한다.

검증 항목:
- Access Token 생성 후 `sub`, `iss`, `roles`, `type=ACCESS`, `kid`, `exp` 확인
- Refresh Token 생성 후 `type=REFRESH`, roles 없음 확인
- 만료 토큰은 `validateToken()`이 `false`
- 위조 토큰은 `validateToken()`이 `false`
- `getUserId()`는 UUID 반환
- `getAuthentication()`은 roles를 `SimpleGrantedAuthority`로 변환

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.JwtTokenProviderTest"
```

기대:
- `JwtProperties`, `JwtTokenProvider` 부재 또는 미구현으로 실패.

- [x] `JwtProperties`를 구현한다.

필수 시그니처:

```java
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String privateKey, String publicKey, String kid, String issuer) {
    RSAPrivateKey rsaPrivateKey() { ... }
    RSAPublicKey rsaPublicKey() { ... }
}
```

구현 기준:
- `privateKey`: Base64 PKCS8
- `publicKey`: Base64 X509
- 파싱 실패 시 `IllegalArgumentException`

- [x] `JwtTokenProvider`를 구현한다.

필수 메서드:

```java
public String createAccessToken(UUID userId, List<String> roles)
public String createRefreshToken(UUID userId)
public boolean validateToken(String token)
public UUID getUserId(String token)
public Authentication getAuthentication(String token)
```

구현 기준:
- Access Token TTL: 15분
- Refresh Token TTL: 7일
- JJWT 0.12.x API 사용
- `Jwts.builder().header().add("kid", properties.kid()).and()`
- `Jwts.parser().verifyWith(properties.rsaPublicKey()).build()`
- `validateToken()`은 만료/위조 예외를 던지지 않고 `false`

- [x] JWT 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.JwtTokenProviderTest"
```

---

## Task 3. RefreshTokenService Redis 저장소

**Files**
- Create: `src/main/java/com/synapse/platform/auth/jwt/RefreshTokenService.java`
- Test: `src/test/java/com/synapse/platform/auth/jwt/RefreshTokenServiceTest.java`

- [x] `RefreshTokenServiceTest`를 작성한다.

검증 항목:
- `save()` 후 `get()` 성공
- `rotate()`는 기존 키 삭제 후 신규 저장
- `delete()` 후 `get()` empty
- TTL은 7일에 근사
- `isValid()`는 저장값과 입력 토큰 일치 여부 반환

테스트 조건:
- `@Testcontainers(disabledWithoutDocker = true)` 유지
- Docker daemon이 없는 로컬에서는 skip 가능

- [x] 테스트를 실행해 RED 또는 Docker skip 상태를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.RefreshTokenServiceTest"
```

- [x] `RefreshTokenService`를 구현한다.

필수 메서드:

```java
public void save(UUID userId, String refreshToken)
public Optional<String> get(UUID userId)
public void delete(UUID userId)
public void rotate(UUID userId, String newRefreshToken)
public boolean isValid(UUID userId, String token)
```

구현 기준:
- Redis key: `refresh:{userId}`
- TTL: 7일
- `rotate()`는 `delete(userId)` 후 `save(userId, newRefreshToken)`

- [x] Redis 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.RefreshTokenServiceTest"
```

기대:
- Docker 실행 환경이면 통과.
- Docker 미실행 환경이면 disabledWithoutDocker에 의해 skip.

---

## Task 4. OAuth 로그인 성공 시 JWT 발급 연동

**Files**
- Modify: `src/main/java/com/synapse/platform/auth/oauth/OAuth2SuccessHandler.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/synapse/platform/auth/OAuth2SuccessHandlerTest.java`

- [x] `OAuth2SuccessHandlerTest`를 JWT redirect 기준으로 변경한다.

검증:
- OAuth2User의 `userId`로 Access Token 생성
- Refresh Token 생성
- Refresh Token Redis 저장 호출
- redirect URL에 `access_token`, `refresh_token` query param 포함
- redirect base는 `app.oauth2.redirect-uri`

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuth2SuccessHandlerTest"
```

- [x] `OAuth2SuccessHandler` 생성자에 `JwtTokenProvider`, `RefreshTokenService`, `clientRedirectUri`를 추가한다.

구현 기준:
- `@Value("${app.oauth2.redirect-uri}") String clientRedirectUri`
- `createAccessToken(userId, List.of("ROLE_USER"))`
- `createRefreshToken(userId)`
- `refreshTokenService.save(userId, refreshToken)`
- `UriComponentsBuilder`로 redirect query 생성

- [x] 관련 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuth2SuccessHandlerTest"
```

---

## Task 5. Token 갱신 API

**Files**
- Create: `src/main/java/com/synapse/platform/auth/AuthController.java`
- Create: `src/main/java/com/synapse/platform/auth/exception/UnauthorizedTokenException.java`
- Test: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`

- [x] `AuthControllerTest`를 먼저 작성한다.

검증:
- `POST /api/v1/auth/refresh` 유효 토큰 → 200 + 신규 access/refresh token
- 위조/만료 토큰 → 401 RFC 7807
- Redis 저장값 불일치 → 401 RFC 7807
- 요청 DTO는 `@Valid` 적용

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.AuthControllerTest"
```

- [x] `UnauthorizedTokenException`을 구현한다.

기준:
- `BusinessException` 상속
- errorCode: `PLAT-002`
- status: `401`

- [x] `AuthController`를 구현한다.

API:

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{ "refreshToken": "eyJ..." }
```

응답:

```json
{ "accessToken": "eyJ...", "refreshToken": "eyJ..." }
```

처리 순서:
- `jwtTokenProvider.validateToken(refreshToken)`
- `jwtTokenProvider.getUserId(refreshToken)`
- `refreshTokenService.isValid(userId, refreshToken)`
- 신규 access/refresh 생성
- `refreshTokenService.rotate(userId, newRefreshToken)`

- [x] refresh API 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.AuthControllerTest"
```

---

## Task 6. TOTP 마이그레이션과 엔티티/Repository

**Files**
- Create: `src/main/resources/db/migration/V19__create_totp_credentials.sql`
- Create: `src/main/java/com/synapse/platform/auth/mfa/TotpCredential.java`
- Create: `src/main/java/com/synapse/platform/auth/mfa/TotpCredentialRepository.java`

- [x] 다음 Flyway 버전이 `V19`임을 다시 확인한다.

```powershell
Get-ChildItem src\main\resources\db\migration | Select-Object Name | Sort-Object Name
```

- [x] `V19__create_totp_credentials.sql`을 생성한다.

SQL 기준:

```sql
CREATE TABLE totp_credentials (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret      TEXT NOT NULL,
    secret_iv   TEXT NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_totp_credentials_user_id ON totp_credentials(user_id);
```

- [x] `TotpCredential` 엔티티를 구현한다.

기준:
- `@Entity`
- `@Table(name = "totp_credentials")`
- setter 금지
- `enable()` 도메인 메서드 제공
- 암호화된 secret 본문과 iv를 분리 저장

- [x] `TotpCredentialRepository`를 구현한다.

필수 메서드:

```java
Optional<TotpCredential> findByUserId(UUID userId);
```

- [x] 컴파일을 확인한다.

```powershell
.\gradlew.bat compileJava
```

---

## Task 7. FieldEncryptor AES-256-GCM

**Files**
- Create: `src/main/java/com/synapse/platform/shared/crypto/FieldEncryptor.java`
- Create: `src/main/java/com/synapse/platform/shared/crypto/package-info.java`
- Test: `src/test/java/com/synapse/platform/shared/crypto/FieldEncryptorTest.java`

- [x] `FieldEncryptorTest`를 먼저 작성한다.

검증:
- 암호화 결과는 평문과 다름
- 결과 형식은 `{base64_iv}:{base64_ciphertext}`
- 복호화하면 원문과 동일
- 32바이트가 아닌 Base64 key는 생성 실패
- 같은 평문을 두 번 암호화하면 IV가 달라 결과가 다름

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.shared.crypto.FieldEncryptorTest"
```

- [x] `shared::crypto` named interface를 추가한다.

필요 시 `auth/package-info.java` allowedDependencies에 `shared::crypto`를 추가한다.

- [x] `FieldEncryptor`를 구현한다.

필수 메서드:

```java
public String encrypt(String plainText)
public String decrypt(String encoded)
```

구현 기준:
- `@Value("${app.crypto.aes-secret-key}")`
- Base64 decode 결과 32바이트 검증
- `AES/GCM/NoPadding`
- IV 12바이트
- tag 128비트
- 반환 형식: `base64_iv:base64_ciphertext`

- [x] 암호화 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.shared.crypto.FieldEncryptorTest"
```

---

## Task 8. TotpService

**Files**
- Create: `src/main/java/com/synapse/platform/auth/mfa/TotpService.java`
- Create: service DTO records inside `TotpService` or separate focused records if needed
- Test: `src/test/java/com/synapse/platform/auth/mfa/TotpServiceTest.java`

- [x] `TotpServiceTest`를 먼저 작성한다.

검증:
- `setup(userId)`는 `UserRepository.findById(userId)`로 email을 조회하고 Base32 secret과 `otpauth://totp/` URI 반환
- 저장되는 secret은 암호화되며 `secret_iv`, `secret` 컬럼으로 분리됨
- 복호화 검증 시 `secretIv + ":" + secret`으로 재조합함
- 올바른 code는 `verify()` true
- 잘못된 code는 `verify()` false
- 최초 검증 성공 시 `enabled=true`

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.mfa.TotpServiceTest"
```

- [x] `TotpService`를 구현한다.

필수 메서드:

```java
public TotpSetupResponse setup(UUID userId)
public boolean verify(UUID userId, String code)
```

구현 기준:
- `UserRepository.findById(userId)`로 userEmail 조회
- `DefaultSecretGenerator(20).generate()`
- `FieldEncryptor.encrypt(secret)` 결과를 `:` 기준으로 분리해 앞부분은 `secret_iv`, 뒷부분은 `secret`에 저장
- 검증 시 `credential.secretIv + ":" + credential.secret`으로 재조합해 복호화
- `QrData.Builder`로 issuer `Synapse`, label에 userEmail 포함
- `DefaultCodeVerifier` 사용
- discrepancy 1
- `String.equals()`로 TOTP 코드 직접 비교 금지
- `@Transactional(propagation = Propagation.REQUIRED)` 적용
- 조회 메서드에는 `@Transactional(readOnly = true)`가 필요한지 분리 검토

- [x] TOTP service 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.mfa.TotpServiceTest"
```

---

## Task 9. MfaController

**Files**
- Create: `src/main/java/com/synapse/platform/auth/mfa/MfaController.java`
- Create: `src/main/java/com/synapse/platform/auth/exception/MfaVerificationException.java`
- Test: `src/test/java/com/synapse/platform/auth/mfa/MfaControllerTest.java`

- [x] `MfaControllerTest`를 먼저 작성한다.

검증:
- JWT 없음 → 401
- 유효 JWT → setup 200 + `otpAuthUri`, `secret`
- verify 올바른 코드 → 200
- verify 잘못된 코드 → 400 RFC 7807
- DTO는 record + `@Valid`

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.mfa.MfaControllerTest"
```

- [x] `MfaVerificationException`을 구현한다.

기준:
- `BusinessException` 상속
- errorCode: `PLAT-003`
- status: `400`

- [x] `MfaController`를 구현한다.

API:

```http
POST /api/v1/auth/mfa/setup
Authorization: Bearer {accessToken}
```

```http
POST /api/v1/auth/mfa/verify
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "code": "123456" }
```

인증 principal 기준:
- `JwtTokenProvider.getAuthentication()`이 principal로 userId 문자열을 넣도록 정하고 컨트롤러에서 UUID 변환한다.
- `MfaController`는 `TotpService.setup(userId)`만 호출하고, userEmail 조회는 서비스 내부에서 `UserRepository.findById(userId)`로 처리한다.

- [x] MFA controller 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.mfa.MfaControllerTest"
```

---

## Task 10. JwtAuthenticationFilter와 SecurityConfig

**Files**
- Create: `src/main/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- Test: `src/test/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilterTest.java`

- [x] `JwtAuthenticationFilterTest`를 먼저 작성한다.

검증:
- 유효 토큰 → 인증 필요 경로 접근 가능
- 토큰 없음 → permitAll 경로 접근 가능
- 토큰 없음 → 인증 필요 경로 401
- 만료/위조 토큰 → 401 + RFC 7807 Problem Details JSON

- [x] 테스트를 실행해 RED를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.JwtAuthenticationFilterTest"
```

- [x] `JwtAuthenticationFilter`를 구현한다.

처리 순서:
- `Authorization` header에서 `Bearer ` token 추출
- 토큰 없음 → `filterChain.doFilter`
- `validateToken(token)` true → `SecurityContextHolder` 설정 후 통과
- 만료/위조 토큰 → `application/problem+json` Content-Type의 RFC 7807 구조로 401 응답 후 즉시 리턴
- 필터 직접 응답 구조는 `GlobalExceptionHandler.ErrorResponse`와 동일한 필드(`type`, `title`, `status`, `detail`, `code`, `traceId`)를 사용
- 요청 종료 후 SecurityContext 오염이 없도록 Spring Security 기본 흐름을 따른다.

- [x] `SecurityConfig`에 필터와 permitAll 경로를 추가한다.

필수:

```java
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

permitAll:
- `/api/v1/auth/refresh`
- `/api/v1/auth/callback`
- `/oauth2/**`
- `/login/**`
- `/actuator/**`

그 외:
- `.anyRequest().authenticated()`

- [x] 필터 테스트를 GREEN으로 만든다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.JwtAuthenticationFilterTest"
```

---

## Task 11. 통합 빌드와 정적 분석

**Files**
- Modify: 문서 체크박스

- [x] JWT/MFA 관련 테스트를 묶어서 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.*" --tests "com.synapse.platform.auth.mfa.*" --tests "com.synapse.platform.auth.AuthControllerTest"
```

- [x] Modulith 구조 검증을 실행한다.

```powershell
.\gradlew.bat test --tests "*ModuleStructureTest"
```

- [x] 정적 분석을 실행한다.

```powershell
.\gradlew.bat checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

- [x] 전체 테스트를 실행한다.

```powershell
.\gradlew.bat test
```

기대:
- Docker 미실행 환경에서는 Redis Testcontainers만 skip 가능.
- 그 외 테스트 실패 0건.

- [x] 전체 빌드를 실행한다.

```powershell
.\gradlew.bat build
```

- [x] 금지 패턴을 검색한다.

```powershell
rg "HS256|ObjectOutputStream|ObjectInputStream|System\.out\.println|refresh.*token.*@Entity|String\.equals\(.*code|allowedOrigins\(\"\\*\"\)" src/main/java src/test/java src/main/resources
```

기대:
- 구현 코드에서 금지 패턴이 나오지 않는다.

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

- [x] 완료 체크박스를 갱신한다.

대상:
- `docs/ai/current/TASK.md`
- `docs/ai/current/HANDOFF.md`
- `docs/ai/current/PLAN.md`

---

## 완료 기준 체크리스트

- [x] OAuth 로그인 성공 시 JWT Access Token 15분 TTL로 발급
- [x] OAuth 로그인 성공 시 Refresh Token 7일 TTL로 발급
- [x] Refresh Token Redis 저장 key가 `refresh:{userId}`임
- [x] Refresh Token rotate 시 기존 키 삭제 후 신규 저장
- [x] `POST /api/v1/auth/refresh`가 신규 Access/Refresh Token 반환
- [x] refresh 실패 케이스가 401 RFC 7807 형식으로 응답
- [x] `totp_credentials` Flyway V19 마이그레이션 생성
- [x] TOTP secret이 AES-256-GCM으로 암호화 저장됨
- [x] `POST /api/v1/auth/mfa/setup`이 `otpAuthUri`, `secret` 반환
- [x] MFA setup의 userEmail은 `UserRepository.findById(userId)`로 조회
- [x] `POST /api/v1/auth/mfa/verify`가 올바른 코드 검증 후 enabled 업데이트
- [x] JWT 필터가 Bearer token을 검증하고 SecurityContext를 설정
- [x] JWT 필터의 만료/위조 토큰 401 응답이 RFC 7807 형식임
- [x] SecurityConfig permitAll/authenticated 경로가 HANDOFF 기준과 일치
- [x] `@ConfigurationPropertiesScan` 사용, `@EnableConfigurationProperties(JwtProperties.class)` 미사용
- [x] `.\gradlew.bat test` 성공 또는 Redis Testcontainers만 Docker 부재로 skip
- [x] `.\gradlew.bat test --tests "*ModuleStructureTest"` 성공
- [x] `.\gradlew.bat checkstyleMain checkstyleTest spotbugsMain spotbugsTest` 성공
- [x] `.\gradlew.bat build` 성공

---

## 리뷰 보정 계획

> 리뷰에서 확인된 인증 경계와 예외 처리 보정 작업이다. 기존 Step 3 완료 항목은 유지하고, 아래 항목은 보정 작업이 끝날 때마다 체크한다.

### 보정 원칙

- Access Token과 Refresh Token은 사용 위치를 명확히 분리한다.
- JWT 검증은 서명, 만료, issuer, token type을 모두 확인한다.
- 필터에서 인증 실패가 발생하면 500으로 새지 않고 401 RFC 7807 응답으로 종료한다.
- MFA setup 재호출과 잘못된 principal은 명시적인 비즈니스 예외로 처리한다.
- 권한 claim의 실제 소스는 별도 정책 결정이 필요하므로, 우선 현재 `ROLE_USER` 고정 발급은 보존하되 상수화하고 Director 확인 항목으로 남긴다.

### Task R1. JWT issuer/type 검증 분리

**Files**
- Modify: `src/main/java/com/synapse/platform/auth/jwt/JwtTokenProvider.java`
- Modify: `src/main/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/synapse/platform/auth/AuthController.java`
- Test: `src/test/java/com/synapse/platform/auth/jwt/JwtTokenProviderTest.java`
- Test: `src/test/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilterTest.java`
- Test: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`

- [x] `JwtTokenProviderTest`에 issuer mismatch 토큰이 실패하는 테스트를 추가한다.
- [x] `JwtTokenProviderTest`에 Access/Refresh token type 판별 테스트를 추가한다.
- [x] `AuthControllerTest`에 Access Token으로 refresh 요청 시 401을 반환하는 테스트를 추가한다.
- [x] `JwtAuthenticationFilterTest`에 Refresh Token으로 보호 API 접근 시 401을 반환하는 테스트를 추가한다.
- [x] `JwtTokenProvider`의 parser에 `requireIssuer(properties.issuer())`를 추가한다.
- [x] `JwtTokenProvider`에 `validateAccessToken(String token)`과 `validateRefreshToken(String token)`을 추가한다.
- [x] `JwtAuthenticationFilter`는 `validateAccessToken()`만 사용하도록 변경한다.
- [x] `AuthController.refresh()`는 `validateRefreshToken()`만 사용하도록 변경한다.
- [x] 관련 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.JwtTokenProviderTest" --tests "com.synapse.platform.auth.jwt.JwtAuthenticationFilterTest" --tests "com.synapse.platform.auth.AuthControllerTest"
```

### Task R2. JWT 필터 예외 경로 401 고정

**Files**
- Modify: `src/main/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilter.java`
- Test: `src/test/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilterTest.java`

- [x] `getAuthentication()`에서 예외가 발생해도 401 RFC 7807로 응답하는 테스트를 추가한다.
- [x] `JwtAuthenticationFilter`에서 token 검증과 authentication 생성 전체를 try/catch로 감싼다.
- [x] 인증 실패 시 `SecurityContextHolder.clearContext()` 후 `writeUnauthorizedResponse()`로 종료한다.
- [x] 필터 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.JwtAuthenticationFilterTest"
```

### Task R3. MFA setup 재호출과 principal 예외 처리

**Files**
- Modify: `src/main/java/com/synapse/platform/auth/mfa/TotpService.java`
- Modify: `src/main/java/com/synapse/platform/auth/mfa/MfaController.java`
- Test: `src/test/java/com/synapse/platform/auth/mfa/TotpServiceTest.java`
- Test: `src/test/java/com/synapse/platform/auth/mfa/MfaControllerTest.java`

- [x] `TotpServiceTest`에 기존 credential이 있을 때 setup 재호출 동작 테스트를 추가한다.
- [x] setup 재호출 정책은 기존 credential을 새 secret으로 교체하는 방식으로 구현한다.
- [x] `TotpService.setup()`에서 user가 없으면 `UnauthorizedTokenException` 또는 명시적 비즈니스 예외를 던지도록 변경한다.
- [x] `MfaControllerTest`에 malformed principal name이 401 RFC 7807로 응답하는 테스트를 추가한다.
- [x] `MfaController.currentUserId()`에서 UUID 파싱 실패를 `UnauthorizedTokenException`으로 변환한다.
- [x] MFA 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.mfa.TotpServiceTest" --tests "com.synapse.platform.auth.mfa.MfaControllerTest"
```

### Task R4. 권한 claim 하드코딩 정리

**Files**
- Modify: `src/main/java/com/synapse/platform/auth/AuthController.java`
- Modify: `src/main/java/com/synapse/platform/auth/oauth/OAuth2SuccessHandler.java`
- Test: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`
- Test: `src/test/java/com/synapse/platform/auth/OAuth2SuccessHandlerTest.java`

- [x] 현재 도메인에 권한 소스가 있는지 `User`, repository, OAuth user save 흐름을 확인한다.
- [x] 권한 소스가 없으면 `DEFAULT_USER_ROLES = List.of("ROLE_USER")` 상수로 분리해 중복 하드코딩을 제거한다.
- [x] 권한 소스가 없음을 확인해 실제 role source 연동은 이번 범위에서 제외한다.
- [x] Director 확인 필요 항목은 보정 결과에 남긴다: Step 3 범위에는 실제 role source가 없어 `DEFAULT_USER_ROLES`를 유지한다.
- [x] 관련 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.AuthControllerTest" --tests "com.synapse.platform.auth.OAuth2SuccessHandlerTest"
```

### Task R5. 전체 검증과 문서 체크

**Files**
- Modify: `docs/ai/current/PLAN.md`
- Modify: `docs/ai/current/TASK.md` if Done When이 변경되는 경우만
- Modify: `docs/ai/current/HANDOFF.md` if Director가 추가 기준을 제시하는 경우만

- [x] 정적 분석을 실행한다.

```powershell
.\gradlew.bat checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

- [x] 전체 테스트를 실행한다.

```powershell
.\gradlew.bat test
```

- [x] Modulith 구조 검증을 실행한다.

```powershell
.\gradlew.bat test --tests "*ModuleStructureTest"
```

- [x] 전체 빌드를 실행한다.

```powershell
.\gradlew.bat build
```

- [x] 금지 패턴을 검색한다.

```powershell
rg "HS256|ObjectOutputStream|ObjectInputStream|System\.out\.println|refresh.*token.*@Entity|String\.equals\(.*code|allowedOrigins\(\"\\*\"\)" src/main/java src/test/java src/main/resources
```

- [x] 완료된 보정 작업 체크박스를 갱신한다.
