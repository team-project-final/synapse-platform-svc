# PLAT-086 Context

## Current State
- #84 OpenAPI/SpringDoc PR #92는 `dev`에 merge 완료됐고 이슈도 닫혔다.
- #91 OAuth provider 컬럼 정합 PR #93은 `dev`에 merge 완료됐다.
- 현재 작업 브랜치: `fix/PLAT-086-admin-role-contract`
- 기준 브랜치: `dev`
- 현재 작업 대상 이슈: #86 `[F8] 관리자 ADMIN role 발급 메커니즘 부재 - 모더레이션 E2E 차단`

## Issue Summary
#86은 W5 관리자 모더레이션 E2E가 platform의 ADMIN role 발급 메커니즘 부재로 차단된다는 문제다.

이슈 본문은 origin/main 실측 기준으로 아래 문제를 지적했다.

1. role 저장소가 없다.
2. 모든 사용자가 고정 일반 role만 받는다.
3. ADMIN 승격 경로가 없다.
4. platform은 `ROLE_ADMIN`, engagement는 `ADMIN`을 기대해 문자열 계약이 맞지 않을 수 있다.

현재 `dev`는 이슈 작성 당시보다 앞서 있다. role 저장소와 기본 role 부여는 이미 구현되어 있으므로, 작업 시작 전 stale 항목과 실제 잔여 갭을 분리해야 한다.

## Verified Platform Evidence

현재 `dev` 기준으로 확인한 내용:

| 항목 | 확인 결과 |
|---|---|
| role schema | `user_roles(user_id, role)` 테이블 존재 |
| allowed roles | `ROLE_USER`, `ROLE_ADMIN` |
| 기존 사용자 백필 | 삭제되지 않은 사용자에게 `ROLE_USER` 부여 |
| 신규 이메일 가입 | 기본 `ROLE_USER` 저장 |
| 신규 OAuth 가입 | 기본 `ROLE_USER` 저장 |
| role 조회 | `UserService.findRoles(UUID)`가 DB role 조회 |
| fallback | role row가 없으면 `ROLE_USER` 반환 |
| 로그인 access token | DB role로 JWT 발급 |
| OAuth success token | DB role로 JWT 발급 |
| refresh access token | DB role로 JWT 재발급 |
| admin endpoint | Spring Security `hasRole('ADMIN')`, 즉 `ROLE_ADMIN` 필요 |
| 수동 관리자 부여 | `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md` 존재 |

관련 파일:
- `src/main/resources/db/migration/V20260609140528__create_user_roles.sql`
- `src/main/java/com/synapse/platform/user/entity/UserRole.java`
- `src/main/java/com/synapse/platform/user/repository/UserRoleRepository.java`
- `src/main/java/com/synapse/platform/user/service/UserService.java`
- `src/main/java/com/synapse/platform/auth/service/JwtTokenProvider.java`
- `src/main/java/com/synapse/platform/auth/service/EmailPasswordAuthService.java`
- `src/main/java/com/synapse/platform/auth/service/OAuth2SuccessHandler.java`
- `src/main/java/com/synapse/platform/auth/controller/AuthController.java`
- `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`

## Engagement Evidence

engagement repo는 확인만 했다. 수정하지 않는다.

확인 내용:
- `CurrentUser.requireAdmin()`은 JWT `roles` claim 컬렉션에 `ADMIN`이 있는지 검사한다.
- 단일 `role` claim이 `ADMIN`인 경우도 허용한다.
- 테스트 token도 `List.of("ADMIN")`을 사용한다.

관련 파일:
- `C:\workspace\team_project_2\synapse-engagement-svc\src\main\java\com\synapse\engagement\shared\CurrentUser.java`
- `C:\workspace\team_project_2\synapse-engagement-svc\src\test\java\com\synapse\engagement\shared\CurrentUserTests.java`
- `C:\workspace\team_project_2\synapse-engagement-svc\src\test\java\com\synapse\engagement\community\api\ReportControllerWebMvcTest.java`

## Remaining Gap

현재 platform은 DB와 Spring Security 기준으로 `ROLE_ADMIN`을 사용한다. 이는 platform 내부 admin endpoint에는 맞다.

하지만 engagement는 JWT에서 `ADMIN`을 찾는다. platform JWT가 `roles: ["ROLE_ADMIN"]`만 담으면 engagement 모더레이션 API가 이를 관리자 token으로 인정하지 못할 가능성이 있다.

이번 작업에서는 다른 repo를 수정하지 않는 조건을 지키기 위해 platform JWT claim에서 호환 가능한 표현을 함께 제공하는 방향으로 처리했다.

정리된 계약:
- DB role 정본: `ROLE_USER`, `ROLE_ADMIN`
- platform 내부 authority: `ROLE_USER`, `ROLE_ADMIN`
- JWT `roles` claim: `ROLE_ADMIN` 원본 + engagement 호환 alias
  - `ROLE_ADMIN` -> `ROLE_ADMIN`, `ADMIN`
- token을 다시 platform 인증으로 읽을 때 bare alias는 Spring authority로 정규화한다.

## Constraints

- 다른 repo는 수정하지 않는다.
- env/profile 설정은 수정하지 않는다.
- 자동 seed admin 또는 bootstrap admin은 만들지 않는다.
- 운영 DB는 직접 변경하지 않는다.
- `TASK_platform.md`는 수정하지 않는다.
- 현재 수동 관리자 부여 방향은 유지한다.

## Risk Notes
- JWT `roles` claim을 바꾸면 platform 자체 인증과 다른 서비스 인증 해석에 동시에 영향을 준다.
- platform 내부 Spring Security는 `ROLE_ADMIN` 권한이 필요하므로, 내부 authority 변환이 깨지면 안 된다.
- 단순히 DB role을 `ADMIN`으로 바꾸는 방식은 현재 check constraint와 기존 admin 보안 정책에 맞지 않는다.
- 자동 admin seed는 운영 정책과 profile/env 제약을 건드릴 수 있어 이번 범위에서 제외한다.

## Resolution
- `JwtTokenProvider.createAccessToken()`에서 access token `roles` claim을 외부 서비스 호환 형태로 확장했다.
- `JwtTokenProvider.getAuthentication()`의 authority 변환은 bare role alias를 Spring Security authority로 정규화하도록 보강했다.
- 최초 어드민 부여 방식은 기존 런북의 DB 수동 grant로 유지했다.
- engagement/shared/gitops/frontend/gateway repo는 수정하지 않았다.

## Verification Direction
필수 검증:
- `UserServiceTest`
- `JwtTokenProviderTest`
- `EmailPasswordAuthServiceTest`
- `OAuth2SuccessHandlerTest`
- `AuthControllerTest`
- `AdminSecurityIntegrationTest`
- `AuditLogControllerTest`
- `clean build`

필요 시 추가 검증:
- JWT claim에 `ROLE_ADMIN`과 engagement 호환 표현이 함께 들어가는지 디코드 테스트
- `getAuthentication()`이 기존 `ROLE_ADMIN` authority를 유지하는지 테스트

통과한 검증:
- `.\gradlew.bat test --tests "*JwtTokenProviderTest"`
- `.\gradlew.bat test --tests "*UserServiceTest" --tests "*EmailPasswordAuthServiceTest" --tests "*OAuth2SuccessHandlerTest" --tests "*AuthControllerTest" --tests "*AdminSecurityIntegrationTest" --tests "*AuditLogControllerTest"`
- `.\gradlew.bat clean build`
