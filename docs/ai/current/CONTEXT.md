# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- Step 2 OAuth 구현 + 룰북 준수 수정 완료 (PR #8 dev merge, 29건 테스트 통과)
- Step 3 브랜치 생성: `feature/PLAT-005-jwt-mfa` (dev 기반)
- OAuth 로그인 성공 후 `userId`만 redirect하는 현재 구조 → JWT 발급으로 교체 예정

## 현재 미결 사항

- JWT 라이브러리 선택 (jjwt vs spring-security-oauth2-jose 비교 필요)
- RS256 키 쌍 관리 방식 (env var vs keystore)
- TOTP 라이브러리 선택 (GoogleAuth vs 직접 구현)
- Redis 연동 방식 (Spring Data Redis + RedisTemplate vs ReactiveRedisTemplate)
- Refresh Token key 설계 (userId 단일 키 vs userId:deviceId 복합 키)

## 활성 제약

- JWT 서명: RS256 고정 (HS256 사용 금지)
- Refresh Token: Redis 전용, DB 저장 금지
- Access Token 만료: 15분
- Refresh Token 만료: 7일
- TOTP: RFC 6238 준수
- URI: `/api/v1/` prefix 필수
- 에러 응답: RFC 7807 형식 (GlobalExceptionHandler 사용)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md (Step 3)
- docs/rules/06-auth-token.md (JWT/OAuth 규칙)
- docs/rules/03-technical.md (트랜잭션, Redis)
- docs/rules/02-function.md (URI, RFC 7807)
