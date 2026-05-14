# TASK — Step 2-fix: 룰북 준수 수정

> 출처: Director 직접 작성 (룰북 점검 결과 기반)

## 상태

- Phase: 구현 완료
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-14
- 목표 완료일: 2026-05-14

---

## Step Goal

Step 2 OAuth 구현 코드가 프로젝트 룰북([MUST] 7건, [SHOULD] 3건)을 모두 준수한다.

## Done When

- [x] URI prefix: `/auth/callback` → `/api/v1/auth/callback`
- [x] OAuth2SuccessHandler, OAuth2FailureHandler redirect 경로 동일하게 수정
- [x] SecurityConfig permitAll 경로 수정
- [x] 관련 테스트 URI 전체 수정 (기존 25건 유지)
- [x] GlobalExceptionHandler (RFC 7807) 생성
- [x] BusinessException 추상 계층 + OAuthProcessingException 예시 생성
- [x] User 엔티티 `@SQLRestriction("deleted_at IS NULL")` 추가
- [x] CorsConfig 생성 + application.yml cors 설정
- [x] `@Transactional(propagation = Propagation.REQUIRED)` 명시
- [x] Checkstyle + SpotBugs 플러그인 추가 (기존 위반은 suppression 격리)
- [x] application-dev.yml, application-prod.yml 생성
- [x] logback-spring.xml JSON 구조화 로깅 설정
- [x] `./gradlew build` 성공

## Scope

- In Scope:
  - 위 Done When 항목 전체
  - 기존 테스트 URI 수정 (신규 테스트 추가는 선택)
  - Checkstyle/SpotBugs config 파일 생성 (suppression 포함)
- Out of Scope:
  - Step 3 JWT/MFA 관련 코드 신규 작성
  - 기존 Checkstyle 위반의 전수 수정 (suppression으로 격리)
  - DB 마이그레이션 추가

## Input

- `docs/ai/current/HANDOFF.md` — 상세 구현 스펙
- `docs/ai/current/CONTEXT.md` — 제약 및 위반 목록
- `docs/rules/` — 룰북 참조

## Instructions

1. URI prefix 변경: `AuthCallbackController`, `SecurityConfig`, `OAuth2SuccessHandler`, `OAuth2FailureHandler`
2. 관련 테스트 URI 수정
3. `shared/exception/` 패키지: `BusinessException`, `GlobalExceptionHandler` 생성
4. `auth/exception/` 패키지: `OAuthProcessingException` 생성
5. `User.java` — `@SQLRestriction` 추가
6. `CorsConfig.java` 생성 + `application.yml` cors 설정
7. `CustomOAuth2UserService` — propagation 명시
8. `build.gradle.kts` — Checkstyle + SpotBugs 플러그인 + suppression 설정
9. `application-dev.yml`, `application-prod.yml` 생성
10. `logback-spring.xml` 생성 + `logstash-logback-encoder` 의존성 추가
11. `./gradlew build` 확인

## Output Format

수정/생성 파일 목록 + 테스트 결과 + Checkstyle/SpotBugs 결과

## Constraints

- CORS: `*` 절대 금지, 화이트리스트만
- URI: `/api/v1/` prefix 필수
- RFC 7807 에러 코드 prefix: `PLAT-xxx`
- Checkstyle: 신규 파일 위반 0건 목표, 기존 파일은 suppression으로 격리
- 기존 테스트 25건 깨지면 안 됨

## Duration

0.5일

## Assignee / Reviewer

- Assignee: Worker (Codex)
- Reviewer: Director (Claude)
