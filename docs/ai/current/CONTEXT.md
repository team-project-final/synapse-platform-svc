# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- Step 2 OAuth 구현 완료 (feature/PLAT-004-oauth 브랜치, 25건 테스트 통과)
- 룰북 전체 점검 완료 — [MUST] 위반 7건, [SHOULD] 위반 3건 식별
- Worker 작업 지시 범위: [MUST] 7건 전체 + [SHOULD] 3건

### [MUST] 위반 목록

| # | 규칙 | 위반 내용 | 대상 파일 |
|---|------|-----------|-----------|
| M-1 | 2.1 URI prefix | `/auth/callback` → `/api/v1/auth/callback` | AuthCallbackController.java, SecurityConfig.java |
| M-2 | 2.3 RFC 7807 | 에러 응답이 `Map.of("error", ...)` 형식, GlobalExceptionHandler 없음 | 신규 생성 |
| M-3 | 2.4 @SQLRestriction | User 엔티티 deletedAt 있으나 `@SQLRestriction` 없음 | User.java |
| M-4 | 1.3 CORS | CorsConfig 없음 | 신규 생성 |
| M-5 | 7.1.4 @RestControllerAdvice | GlobalExceptionHandler 없음 (M-2와 동일 파일) | 신규 생성 |
| M-6 | 4.4 정적 분석 | Checkstyle + SpotBugs 미구성 | build.gradle.kts |
| M-7 | 3.3 트랜잭션 | `@Transactional` propagation 미명시 | CustomOAuth2UserService.java |

### [SHOULD] 위반 목록

| # | 규칙 | 위반 내용 |
|---|------|-----------|
| S-1 | 3.6 예외 계층 | BusinessException 커스텀 예외 계층 없음 |
| S-2 | 7.0.2 환경 프로파일 | application-dev/staging/prod 프로파일 없음 |
| S-3 | 9.1 구조화 로깅 | logback-spring.xml JSON 포맷 없음 |

## 현재 미결 사항

- PR #7 취소됨 — 룰 위반 수정 후 재생성 예정
- [MUST] 위반 수정 → 재빌드/재테스트 → 커밋 → PR 재생성

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token: Redis 전용, DB 저장 금지
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- CORS: 화이트리스트 방식, `*` 절대 금지
- URI: 모든 REST API `/api/v1/` prefix 필수
- 에러 응답: RFC 7807 형식 (type/title/status/detail/code/traceId)
- 에러 코드 prefix: `PLAT-xxx` (platform-svc)

## 참고할 공식 문서

- docs/rules/01-security.md (CORS, Secrets)
- docs/rules/02-function.md (URI, RFC 7807, Soft Delete)
- docs/rules/03-technical.md (트랜잭션, 예외)
- docs/rules/04-quality.md (정적 분석)
- docs/rules/07-platform-spring.md (Entity, ExceptionHandler)
- docs/rules/09-observability.md (로깅)
