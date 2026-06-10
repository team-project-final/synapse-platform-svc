# PLAT-086 Handoff

## Status
구현 및 검증 완료. #86 이슈 코멘트/PR 단계가 남아 있다.

## Branch
- Base: `dev`
- Working branch: `fix/PLAT-086-admin-role-contract`

## Archived
PLAT-091 current 문서는 아래 경로에 보관했다.

- `docs/ai/archive/20260610-plat-091-completed/TASK.md`
- `docs/ai/archive/20260610-plat-091-completed/CONTEXT.md`
- `docs/ai/archive/20260610-plat-091-completed/HANDOFF.md`

## Current Task
#86의 ADMIN role 발급 메커니즘 부재 이슈를 현재 `dev` 기준으로 재검토한다.

이슈 본문은 role 저장소와 ADMIN 발급 경로가 없다고 되어 있지만, 현재 platform에는 이미 아래 구현이 들어와 있다.

- `user_roles` 테이블
- `ROLE_USER`, `ROLE_ADMIN` role constraint
- 신규 사용자 기본 `ROLE_USER` 저장
- DB role 기반 JWT `roles` claim 발급
- 수동 `ROLE_ADMIN` 부여 런북

따라서 남은 핵심은 engagement 모더레이션이 기대하는 `ADMIN` 문자열과 platform의 `ROLE_ADMIN` 문자열 계약을 맞추는 것이다.

## Key Question
platform JWT가 engagement 관리자 판정에 충분한가?

현재 의심되는 갭:
- platform: `roles: ["ROLE_USER", "ROLE_ADMIN"]`
- engagement: `roles` 안에서 `"ADMIN"`을 찾음

이 상태가 맞다면 engagement 모더레이션 API는 platform 발급 admin token을 거부할 수 있다.

## Implemented Direction
현재 코드를 테스트로 확정한 뒤, 다른 repo 수정 없이 platform JWT claim 호환성을 보강했다.

처리 내용:
- `ROLE_ADMIN` DB 정본은 유지한다.
- 최초 어드민 부여는 기존처럼 DB 수동 grant로 유지한다.
- access token `roles` claim에 `ROLE_ADMIN`과 함께 `ADMIN` alias를 넣는다.
- platform 내부 인증으로 읽을 때 bare alias는 Spring authority로 정규화한다.

주의할 점:
- DB role 정본은 `ROLE_*`로 유지한다.
- platform 내부 Spring Security `hasRole('ADMIN')`은 계속 동작해야 한다.
- 자동 admin seed/profile/env 변경은 하지 않는다.
- 최초 admin은 기존 런북의 수동 DB grant 절차를 유지한다.

## Relevant Files

Platform:
- `src/main/resources/db/migration/V20260609140528__create_user_roles.sql`
- `src/main/java/com/synapse/platform/user/entity/UserRole.java`
- `src/main/java/com/synapse/platform/user/repository/UserRoleRepository.java`
- `src/main/java/com/synapse/platform/user/service/UserService.java`
- `src/main/java/com/synapse/platform/auth/service/JwtTokenProvider.java`
- `src/main/java/com/synapse/platform/auth/service/EmailPasswordAuthService.java`
- `src/main/java/com/synapse/platform/auth/service/OAuth2SuccessHandler.java`
- `src/main/java/com/synapse/platform/auth/controller/AuthController.java`
- `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`

Platform tests:
- `src/test/java/com/synapse/platform/user/service/UserServiceTest.java`
- `src/test/java/com/synapse/platform/auth/service/JwtTokenProviderTest.java`
- `src/test/java/com/synapse/platform/auth/service/EmailPasswordAuthServiceTest.java`
- `src/test/java/com/synapse/platform/auth/service/OAuth2SuccessHandlerTest.java`
- `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`
- `src/test/java/com/synapse/platform/auth/config/AdminSecurityIntegrationTest.java`
- `src/test/java/com/synapse/platform/audit/controller/AuditLogControllerTest.java`

Engagement evidence, read-only:
- `C:\workspace\team_project_2\synapse-engagement-svc\src\main\java\com\synapse\engagement\shared\CurrentUser.java`
- `C:\workspace\team_project_2\synapse-engagement-svc\src\test\java\com\synapse\engagement\shared\CurrentUserTests.java`

## Test Commands
```powershell
.\gradlew.bat test --tests "*UserServiceTest"
.\gradlew.bat test --tests "*JwtTokenProviderTest"
.\gradlew.bat test --tests "*EmailPasswordAuthServiceTest" --tests "*OAuth2SuccessHandlerTest" --tests "*AuthControllerTest"
.\gradlew.bat test --tests "*AdminSecurityIntegrationTest" --tests "*AuditLogControllerTest"
.\gradlew.bat clean build
```

검증 결과:
- `.\gradlew.bat test --tests "*JwtTokenProviderTest"` 통과
- `.\gradlew.bat test --tests "*UserServiceTest" --tests "*EmailPasswordAuthServiceTest" --tests "*OAuth2SuccessHandlerTest" --tests "*AuthControllerTest" --tests "*AdminSecurityIntegrationTest" --tests "*AuditLogControllerTest"` 통과
- `.\gradlew.bat clean build` 통과
- Windows Kafka 임시파일 삭제 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`

## Do Not Touch
- engagement/shared/gitops/frontend/gateway repo
- `.env`
- Spring profile 설정
- 자동 seed admin
- 운영 DB 직접 변경
- `TASK_platform.md`

## PR Direction
변경이 필요하면 PR은 `dev` 대상으로 올린다.

예상 PR 제목:
`fix(auth): admin role JWT 계약 정합 보강 (#86)`

코드 변경 없이 정리되는 경우:
`docs(auth): ADMIN role 발급 정합 조사 기록 (#86)`
