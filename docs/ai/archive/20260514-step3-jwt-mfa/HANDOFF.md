# HANDOFF

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Director (Claude)

## TO

Worker (Codex)

## 요청 내용

Step 3 JWT + MFA 기초 구현. 아래 순서대로 진행한다.
설계 결정은 이미 완료됐으므로 CONTEXT.md와 이 문서의 명세대로만 구현한다.

---

### URI 예외 명시 (룰 2.1 충돌)

> **Director 명시 경로 우선 적용** — 아래 URI는 룰 2.1 "동사 사용 금지"와 형식상 충돌하지만,
> 프론트엔드/API 계약이 이미 확정된 경로다. Worker가 임의로 변경해서는 안 된다.
>
> | URI | 동사 포함 이유 | 처리 |
> |-----|--------------|------|
> | `POST /api/v1/auth/refresh` | `refresh`가 행위명 | Director 명시 경로 우선 적용 |
> | `POST /api/v1/auth/mfa/setup` | `setup`이 행위명 | Director 명시 경로 우선 적용 |
> | `POST /api/v1/auth/mfa/verify` | `verify`가 행위명 | Director 명시 경로 우선 적용 |

---

### 사전 확인 필수

구현 시작 전 아래 파일을 반드시 확인한다.

- `docs/ai/current/CONTEXT.md` — 확정된 것 + 활성 제약
- `docs/spike/step3-jwt-mfa/SAMPLING_STEP3_RESULT.md` — 확정된 코드 패턴
- 기존 Flyway 마이그레이션 파일 버전 확인 (`src/main/resources/db/migration/`)
- 기존 `SecurityConfig.java` 위치 및 현재 필터 체인 확인

---

### 구현 순서

#### 1. 의존성 추가 (`build.gradle.kts`)

```kotlin
// JWT
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

// TOTP
implementation("dev.samstevens.totp:totp:1.7.1")

// Redis
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

루트 `@SpringBootApplication`에는 `@ConfigurationPropertiesScan`을 추가한다.
(`@EnableConfigurationProperties(JwtProperties.class)` 사용 금지 — Modulith 경계 위반)

---

#### 2. JwtProperties record

파일: `src/main/java/com/synapse/platform/auth/jwt/JwtProperties.java`

```
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String privateKey, String publicKey, String kid, String issuer) {
    RSAPrivateKey rsaPrivateKey() { ... }   // Base64 PKCS8 → RSAPrivateKey
    RSAPublicKey rsaPublicKey() { ... }     // Base64 X509 → RSAPublicKey
}
```

확정 패턴: `SAMPLING_STEP3_RESULT.md` B항목 참조.

`application-local.yml` 추가:
```yaml
jwt:
  private-key: ${JWT_PRIVATE_KEY}
  public-key: ${JWT_PUBLIC_KEY}
  kid: synapse-key-2026-05
  issuer: synapse-auth
```

---

#### 3. JwtTokenProvider

파일: `src/main/java/com/synapse/platform/auth/jwt/JwtTokenProvider.java`

- `createAccessToken(UUID userId, List<String> roles)` → String
  - TTL 15분
  - claims: `sub`(userId), `iss`(issuer), `iat`, `exp`, `roles`, `type="ACCESS"`
  - header: `kid`
  - 서명: RS256 (RSAPrivateKey)
- `createRefreshToken(UUID userId)` → String
  - TTL 7일
  - claims: `sub`, `iss`, `iat`, `exp`, `type="REFRESH"`
  - header: `kid`
- `validateToken(String token)` → boolean
  - 만료/위조 토큰 → false 반환 (예외 던지지 않음)
- `getUserId(String token)` → UUID
- `getAuthentication(String token)` → UsernamePasswordAuthenticationToken

확정 패턴: `SAMPLING_STEP3_RESULT.md` A항목 참조.
jjwt 0.12.x API 사용: `Jwts.builder().header().add("kid", ...).and()`, `Jwts.parser().verifyWith(publicKey)`

---

#### 4. RefreshTokenService

파일: `src/main/java/com/synapse/platform/auth/jwt/RefreshTokenService.java`

- `StringRedisTemplate` 주입
- `save(UUID userId, String refreshToken)`: `refresh:{userId}` 키, TTL 7일
- `get(UUID userId)` → Optional<String>
- `delete(UUID userId)`: 키 삭제
- `rotate(UUID userId, String newRefreshToken)`: **기존 키 삭제 후 신규 저장** (재발급 시 즉시 무효화, rule 06-auth-token.md 6.1 [MUST])
- `isValid(UUID userId, String token)` → boolean: Redis 저장값과 일치 여부 확인

확정 패턴: `SAMPLING_STEP3_RESULT.md` D항목 참조.

---

#### 5. OAuth2AuthenticationSuccessHandler 수정

파일: 기존 `OAuth2AuthenticationSuccessHandler.java` 수정

OAuth 로그인 성공 시:
1. `JwtTokenProvider.createAccessToken()` 호출
2. `JwtTokenProvider.createRefreshToken()` 호출
3. `RefreshTokenService.save()` 로 Redis 저장
4. redirect URL에 `access_token`, `refresh_token` query param 추가

```java
String redirectUrl = UriComponentsBuilder.fromUriString(clientRedirectUri)
        .queryParam("access_token", accessToken)
        .queryParam("refresh_token", refreshToken)
        .build().toUriString();
getRedirectStrategy().sendRedirect(request, response, redirectUrl);
```

`clientRedirectUri`는 `application.yml`의 `app.oauth2.redirect-uri` 프로퍼티로 관리한다.

---

#### 6. Token 갱신 API

파일: `src/main/java/com/synapse/platform/auth/AuthController.java` (또는 기존 컨트롤러에 추가)

`POST /api/v1/auth/refresh`

요청:
```json
{ "refreshToken": "eyJ..." }
```

처리 순서:
1. `JwtTokenProvider.validateToken(refreshToken)` 검증
2. `getUserId(refreshToken)` 추출
3. `RefreshTokenService.isValid(userId, refreshToken)` — Redis 저장값과 일치 확인
4. 불일치 시 401 반환
5. `JwtTokenProvider.createAccessToken()` + `createRefreshToken()` 신규 발급
6. `RefreshTokenService.rotate()` 호출 (기존 삭제 + 신규 저장)
7. 응답:
```json
{ "accessToken": "eyJ...", "refreshToken": "eyJ..." }
```

에러: 401 Unauthorized (RFC 7807 형식)

---

#### 7. Flyway 마이그레이션 — totp_credentials 테이블

파일: `src/main/resources/db/migration/V{다음버전}__create_totp_credentials.sql`

```sql
CREATE TABLE totp_credentials (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret      TEXT NOT NULL,          -- AES-256-GCM 암호화된 Base32 시크릿
    secret_iv   TEXT NOT NULL,          -- GCM IV (Base64)
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_totp_credentials_user_id ON totp_credentials(user_id);
```

---

#### 8. FieldEncryptor (shared 모듈)

파일: `src/main/java/com/synapse/platform/shared/crypto/FieldEncryptor.java`

AES-256-GCM 암호화/복호화 유틸리티.
환경변수 `AES_SECRET_KEY` (Base64 인코딩 32바이트)에서 키 로딩.
`encrypt(String plainText)` → `"{base64_iv}:{base64_ciphertext}"` 형태 반환.
`decrypt(String encoded)` → 원문 반환.

`application.yml` 추가:
```yaml
app:
  crypto:
    aes-secret-key: ${AES_SECRET_KEY}
```

---

#### 9. TotpService

파일: `src/main/java/com/synapse/platform/auth/mfa/TotpService.java`

- `setup(UUID userId)` → `TotpSetupResponse`
  1. `UserRepository.findById(userId)`로 `userEmail` 조회
     (JWT claim에 email 없음 — DB에서만 가져옴)
  2. `DefaultSecretGenerator(20).generate()` — Base32 시크릿 생성
  3. `FieldEncryptor.encrypt(secret)` — `"{base64_iv}:{base64_ciphertext}"` 형태 반환
  4. `:` 기준으로 분리: 앞부분 → `secret_iv`, 뒷부분 → `secret` 컬럼에 저장
  5. `TotpCredential` 저장 (enabled=false)
  6. `QrData.Builder`로 `otpauth://totp/` URI 생성 (label: userEmail, issuer: Synapse)
  7. 반환: `{ otpAuthUri, secret }` (평문 secret은 사용자에게 한 번만 노출)

- `verify(UUID userId, String code)` → boolean
  1. `TotpCredentialRepository.findByUserId(userId)` 조회
  2. `credential.secretIv + ":" + credential.secret` 으로 재조합
  3. `FieldEncryptor.decrypt(재조합문자열)` — 평문 시크릿 복호화
  4. `DefaultCodeVerifier`로 검증 (discrepancy=1)
  5. 최초 검증 성공 시 `enabled=true` 업데이트

라이브러리 패턴: `SAMPLING_STEP3_RESULT.md` C항목 참조.
타이밍 공격 방지: `DefaultCodeVerifier` 내부적으로 상수 시간 비교 사용.

---

#### 10. MfaController

파일: `src/main/java/com/synapse/platform/auth/mfa/MfaController.java`

`POST /api/v1/auth/mfa/setup`
- 인증 필요 (JWT 필터 통과 후)
- `@AuthenticationPrincipal`로 userId 추출
- `TotpService.setup(userId)` 호출 (userEmail은 서비스 내부에서 UserRepository로 조회)
- 응답 200: `{ "otpAuthUri": "otpauth://...", "secret": "BASE32..." }`

`POST /api/v1/auth/mfa/verify`
- 인증 필요
- 요청: `{ "code": "123456" }`
- `TotpService.verify()` 호출
- 성공 시 200, 실패 시 400 (RFC 7807)

---

#### 11. JwtAuthenticationFilter

파일: `src/main/java/com/synapse/platform/auth/jwt/JwtAuthenticationFilter.java`

`OncePerRequestFilter` 구현:
1. `Authorization: Bearer {token}` 헤더 추출
2. `JwtTokenProvider.validateToken()` 검증
3. 유효하면 `getAuthentication()` 호출 → `SecurityContextHolder` 설정
4. 토큰 없음 → 그냥 통과 (filterChain.doFilter 호출)
5. 만료/위조 토큰 → catch 후 **RFC 7807 Problem Details JSON 형식으로 401 응답**
   - `GlobalExceptionHandler`와 동일한 응답 구조 사용
   - 예: `{ "type": "...", "title": "Unauthorized", "status": 401, "detail": "Token expired" }`
   - `response.setStatus(401)`, `response.setContentType("application/problem+json")` 직접 설정
   - filterChain.doFilter 호출 없이 즉시 리턴

확정 패턴: `SAMPLING_STEP3_RESULT.md` E항목 참조.

---

#### 12. SecurityConfig 수정

기존 SecurityConfig에 아래 추가:

```java
http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

`permitAll()` 경로:
- `/api/v1/auth/refresh`
- `/api/v1/auth/callback`
- `/oauth2/**`, `/login/**`
- `/actuator/**`

나머지는 `.anyRequest().authenticated()`

---

### 테스트 요구사항

#### JwtTokenProviderTest (단위)
- Access Token 정상 생성 및 파싱 확인
- claims 검증 (sub, roles, type, kid, exp)
- 만료 토큰 → `validateToken()` false 반환
- 위조 토큰 → `validateToken()` false 반환

#### RefreshTokenServiceTest (통합 — Testcontainers)
- 저장 후 조회 성공
- rotate() — 기존 키 삭제 + 신규 저장 확인
- 삭제 후 조회 → null
- TTL 설정 확인 (7일 근사)
- `@Testcontainers(disabledWithoutDocker = true)` 유지 (CI에서 자동 실행)

#### TotpServiceTest (단위)
- 시크릿 생성 + QR URI 형식 확인
- 올바른 코드 검증 성공
- 잘못된 코드 검증 실패
- verify 성공 시 enabled=true 업데이트 확인

#### AuthControllerTest (@WebMvcTest)
- `POST /api/v1/auth/refresh` — 유효 토큰 → 200 + 신규 토큰 반환
- `POST /api/v1/auth/refresh` — 위조/만료 토큰 → 401
- `POST /api/v1/auth/refresh` — Redis 불일치 → 401

#### MfaControllerTest (@WebMvcTest)
- `POST /api/v1/auth/mfa/setup` — JWT 없음 → 401
- `POST /api/v1/auth/mfa/setup` — 유효 JWT → 200 + otpAuthUri 반환
- `POST /api/v1/auth/mfa/verify` — 올바른 코드 → 200
- `POST /api/v1/auth/mfa/verify` — 잘못된 코드 → 400

#### JwtAuthenticationFilterTest (통합)
- 유효 토큰 → 인증 필요 경로 200
- 토큰 없음 → permitAll 경로 200
- 토큰 없음 → 인증 필요 경로 401
- 만료/위조 토큰 → 401

---

### 절대 금지

- DB에 Refresh Token 저장
- HS256 서명
- `@EnableConfigurationProperties(JwtProperties.class)` 루트에서 사용
- Refresh Token 재발급 시 기존 키 미삭제
- TOTP 시크릿 평문 저장
- `String.equals()`로 TOTP 코드 비교 (반드시 `DefaultCodeVerifier` 사용)
- 모듈 간 직접 import (shared 제외)

---

### 완료 기준 (Done When)

- [x] OAuth 로그인 성공 시 JWT Access Token (15분) redirect query param으로 전달
- [x] Refresh Token (7일) 발급 + Redis 저장
- [x] `POST /api/v1/auth/refresh` — Access/Refresh Token 갱신 + 기존 Refresh 즉시 무효화
- [x] `POST /api/v1/auth/mfa/setup` — TOTP 시크릿 생성 + QR URI 반환 + DB 암호화 저장
- [x] `POST /api/v1/auth/mfa/verify` — TOTP 코드 검증 + enabled 업데이트
- [x] Security Filter — JWT 검증 + SecurityContext 설정
- [x] `./gradlew build` 성공
- [x] `./gradlew test` 성공 (Redis skip 제외)
- [x] `ModuleStructureTest` 통과

## 필요한 출력 형식

구현한 파일 목록 + 각 파일 전체 코드.
완료 기준 체크리스트 결과 포함.

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md
- docs/spike/step3-jwt-mfa/SAMPLING_STEP3_RESULT.md

## 기한

2026-05-16
