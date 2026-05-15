# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- Step 2 OAuth 구현 + 룰북 준수 수정 완료 (PR #8 dev merge, 29건 테스트 통과)
- Step 3 브랜치 생성: `feature/PLAT-005-jwt-mfa` (dev 기반)
- JWT 라이브러리: `jjwt 0.12.6` (샘플링 A 통과)
- RS256 키 관리: env var (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) + `@ConfigurationPropertiesScan` (샘플링 B 통과, Modulith 경계 위반 방지)
- TOTP 라이브러리: `dev.samstevens.totp:totp:1.7.1` (샘플링 C 통과)
- Redis: `StringRedisTemplate`, key pattern `refresh:{userId}`, TTL 7일 (샘플링 D — 코드 확정, 실컨테이너 검증은 CI에서)
- JWT Filter: `OncePerRequestFilter` 기반, `UsernamePasswordAuthenticationFilter` 앞에 등록 (샘플링 E 통과)
- Refresh Token 재발급 시 기존 Redis 키 즉시 삭제 후 신규 저장 (rule 06-auth-token.md 6.1 [MUST])
- MFA 시크릿 저장: `totp_credentials` 별도 테이블 + AES-256-GCM 암호화 [SHOULD] (rule 06-auth-token.md 6.3 + rule 11-data-sovereignty.md 11.1)
- OAuth 성공 후 JWT 전달: redirect query param (`access_token`, `refresh_token`) (rule 06-auth-token.md 6.2)
- `@EnableConfigurationProperties` 대신 `@ConfigurationPropertiesScan` 사용 (루트 모듈이 auth 내부 타입 직접 참조 금지)
- Spring Boot 4 / Java 21 / Modulith 빌드 + 구조 검증 통과 확인 완료

## 현재 미결 사항

- Redis Testcontainers 테스트 (`RefreshTokenServiceTest` 4건): `disabledWithoutDocker = true`로 skip 처리 → Docker daemon 실행 환경(CI)에서 자동 재검증
- TOTP QR URI에서 secret padding이 `%3D`로 URL 인코딩됨 → 본 구현 테스트 작성 시 확인 필요

## 활성 제약

- JWT 서명: RS256 고정 (HS256 사용 금지)
- Refresh Token: Redis 전용, DB 저장 금지
- Access Token 만료: 15분
- Refresh Token 만료: 7일
- Refresh Token 재발급: 기존 키 즉시 삭제 후 신규 저장 (rotation)
- TOTP: RFC 6238 준수 (6자리, 30초, 허용 오차 ±1 step)
- URI: `/api/v1/` prefix 필수
- 에러 응답: RFC 7807 형식 (GlobalExceptionHandler 사용)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- JWT token claims: `sub`(userId), `iss`(synapse-auth), `iat`, `exp`, `roles`, `type` 포함
- JWT header: `kid` 필수 (key rotation 식별용)
- MFA 시크릿: AES-256-GCM 암호화 후 `totp_credentials` 테이블 저장

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md (Step 3)
- docs/rules/06-auth-token.md (JWT/OAuth/MFA 규칙)
- docs/rules/03-technical.md (트랜잭션, Redis)
- docs/rules/02-function.md (URI, RFC 7807)
- docs/rules/11-data-sovereignty.md (AES-256-GCM 암호화)
- docs/rules/07-platform-spring.md (Spring 코드 규칙)
- docs/spike/step3-jwt-mfa/SAMPLING_STEP3_RESULT.md (샘플링 확정 패턴)
