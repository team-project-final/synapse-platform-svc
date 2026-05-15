# SAMPLING RESULT — Step 3: JWT + MFA 기초

> 실행일: 2026-05-14
> 대상: Spring Boot 4.0.0 + Java 21 + Spring Modulith 1.3.0

## 결과 요약

| 항목 | 결과 | 확정 내용 | 이슈 |
|------|------|-----------|------|
| A. jjwt RS256 | ✅ | `Jwts.builder().header().add("kid", ...).and()` 및 `Jwts.parser().verifyWith(publicKey)` 패턴 사용 | 없음 |
| B. 키 바인딩 | ✅ | `@ConfigurationProperties(prefix = "jwt")` record에서 Base64 PKCS8/X509 키를 RSA key 객체로 변환 | 루트 애플리케이션에서 auth 타입 직접 참조 시 Modulith 위반 발생. `@ConfigurationPropertiesScan`으로 해결 |
| C. TOTP | ✅ | `dev.samstevens.totp:totp:1.7.1`로 secret 생성, otpauth URI 생성, ±1 step 검증 가능 | URI의 secret padding은 `%3D`로 URL 인코딩됨 |
| D. Redis | ⚠️ | `StringRedisTemplate` 기반 `refresh:{userId}` 저장/조회/삭제/TTL 코드 작성 및 Testcontainers 테스트 작성 | 현재 환경에서 Docker daemon 미실행으로 Testcontainers 실검증 불가. 테스트는 `disabledWithoutDocker = true`로 skip 처리 |
| E. Filter | ✅ | `OncePerRequestFilter`에서 Bearer 토큰 검증 후 `SecurityContextHolder` 인증 설정 가능 | 없음 |

## Director 판단용 결론

- Step 3 본 구현에 `jjwt 0.12.6`을 사용할 수 있다.
- JWT 서명 방식은 기존 결정대로 RS256으로 진행 가능하다.
- RSA 키는 `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` 환경변수 문자열을 `@ConfigurationProperties`로 바인딩하는 방식이 동작한다.
- TOTP 라이브러리는 `dev.samstevens.totp:totp:1.7.1`로 진행 가능하다.
- Refresh Token 저장소는 Redis + `StringRedisTemplate` 방식으로 구현 가능하다.
- JWT 인증 필터는 `UsernamePasswordAuthenticationFilter` 앞에 등록하는 패턴으로 진행 가능하다.
- 단, Redis 항목은 Docker daemon이 켜진 환경에서 Testcontainers 재검증이 필요하다.

## 본 구현 시 권고안

| 결정 항목 | 권고 |
|-----------|------|
| JWT 라이브러리 | `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6` 유지 |
| JWT 알고리즘 | RS256 고정 |
| JWT 키 설정 | `jwt.private-key`, `jwt.public-key`, `jwt.kid`, `jwt.issuer` |
| ConfigurationProperties 등록 | `@ConfigurationPropertiesScan` 사용 |
| Access Token TTL | 15분 |
| Refresh Token TTL | 7일 |
| Refresh Token 저장 | Redis only, key pattern `refresh:{userId}` |
| TOTP | `DefaultSecretGenerator(20)`, 6 digits, 30 seconds, discrepancy 1 |
| JWT Filter 위치 | `UsernamePasswordAuthenticationFilter` 이전 |

## 실행한 검증

```bash
.\gradlew.bat build -x test
```

결과: 성공

```bash
.\gradlew.bat test
```

결과: 성공

비고: `RefreshTokenServiceTest` 4건은 Docker 환경 부재로 skip.

```bash
.\gradlew.bat test --tests "*ModuleStructureTest"
```

결과: 성공

## 발견된 문제와 처리

### Modulith 경계 위반

- 원인: `PlatformSvcApplication`에서 `@EnableConfigurationProperties(JwtProperties.class)`로 auth 모듈 내부 타입을 직접 참조했다.
- 처리: `@ConfigurationPropertiesScan`으로 변경해 루트 모듈이 auth 내부 타입을 직접 참조하지 않도록 했다.

### Testcontainers 실행 불가

- 원인: Docker CLI는 설치되어 있으나 Docker daemon에 연결할 수 없다.
- 확인 메시지: `Could not find a valid Docker environment`
- 처리: Redis 샘플링 테스트에 `@Testcontainers(disabledWithoutDocker = true)` 적용.
- 후속 확인 방법: Docker daemon 실행 후 `.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.RefreshTokenServiceTest"` 재실행.

## 본 구현 반영 메모

- JWT 서명은 RS256으로 유지한다.
- Refresh Token은 Redis key `refresh:{userId}`, TTL 7일로 저장 가능하다.
- JWT properties 등록은 root application 직접 참조 대신 configuration properties scan 방식을 사용한다.
- TOTP URI 검증 시 URL 인코딩된 query parameter를 기준으로 테스트한다.

## Done When 체크

- [x] A. jjwt RS256 토큰 생성/파싱 검증
- [x] B. RSA 키 페어 바인딩 검증
- [x] C. TOTP secret/URI/verify 검증
- [ ] D. Redis TTL CRUD 실컨테이너 검증
- [x] E. JWT 필터 인증 컨텍스트 설정 검증
- [x] Spring Boot 4 / Java 21 컴파일 검증
- [x] Modulith 구조 검증

## Director 확인 필요

1. Docker daemon이 가능한 환경에서 Redis Testcontainers 테스트를 다시 실행할지 결정.
2. 본 구현에서 Refresh Token 재발급 시 기존 토큰 즉시 무효화 정책을 Step 3에 포함할지 결정.
3. MFA secret 저장 위치와 암호화/마스킹 정책을 본 구현 전에 확정.
4. OAuth 성공 후 JWT를 redirect query로 전달할지, 쿠키/응답 body로 전달할지 확정.
