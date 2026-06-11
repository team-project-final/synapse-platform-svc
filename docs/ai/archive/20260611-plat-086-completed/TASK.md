# PLAT-086 Admin role 발급/계약 정합 확인

## Task ID
PLAT-086

## Title
관리자 ADMIN role 발급 메커니즘 및 engagement 모더레이션 권한 계약 정합

## Owner
platform

## Status
DONE

## Priority
P1

## Issue
https://github.com/team-project-final/synapse-platform-svc/issues/86

## Branch
`fix/PLAT-086-admin-role-contract`

## Step Goal
#86에서 제기된 "ADMIN role 발급 메커니즘 부재" 이슈를 현재 `dev` 기준 코드와 다시 대조한다.

현재 platform에는 `user_roles` 테이블, 기본 `ROLE_USER` 부여, `ROLE_ADMIN` 수동 부여 런북, JWT `roles` claim 발급이 이미 존재한다. 따라서 이번 작업은 신규 role 시스템을 처음부터 만드는 작업이 아니라, 아래 잔여 갭을 확인하고 필요한 최소 보강만 진행한다.

- 이슈 본문 중 이미 해결된 항목과 아직 남은 항목을 분리한다.
- platform 내부 권한(`ROLE_ADMIN`)과 engagement 모더레이션 권한 기대값(`ADMIN`)의 JWT 계약 차이를 검증한다.
- 자동 seed/profile/env 변경 없이 최초 어드민 부여 절차가 문서와 테스트 관점에서 충분한지 확인한다.
- 필요한 경우 platform 쪽 JWT role claim 호환성을 보강한다.

## Done When
- [x] 현재 `dev` 기준 `user_roles` migration과 `UserRole` entity를 확인한다.
- [x] 신규 이메일/비밀번호 가입과 OAuth 가입 시 기본 `ROLE_USER` 저장 경로를 확인한다.
- [x] 로그인, OAuth 성공, refresh 재발급 시 DB role이 JWT `roles` claim에 들어가는지 확인한다.
- [x] `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`의 수동 `ROLE_ADMIN` 부여 절차가 현재 schema와 맞는지 확인한다.
- [x] engagement의 관리자 판정이 `ADMIN`을 기대한다는 사실을 코드 기준으로 확인한다.
- [x] platform JWT가 engagement 모더레이션 API에 필요한 role 문자열을 제공하는지 판단한다.
- [x] 필요한 경우 platform JWT role claim 호환성 보강 및 테스트를 추가한다.
- [x] 단일/통합 테스트와 `clean build`를 통과시킨다.
- [x] `docs/project-management/history/HISTORY_platform.md`에 작업 이력을 남긴다.
- [ ] #86에 검증 결과와 결정 내용을 코멘트로 남긴다.

## Scope

### In Scope
- platform repo 내부 role 저장/발급/JWT claim 흐름 확인
- `ROLE_ADMIN` 수동 부여 런북 정합 확인
- platform JWT `roles` claim과 engagement `ADMIN` 기대값의 계약 갭 검토
- 필요한 경우 platform 코드에서 JWT role claim 호환성 보강
- 관련 단위/통합 테스트 보강
- #86 이슈 답변/정리
- PLAT-091 current 문서 archive 보관

### Out of Scope
- engagement/shared/gitops/frontend/gateway repo 수정
- env, profile, 운영 설정 변경
- 자동 seed admin, bootstrap admin, dummy admin 계정 추가
- 운영 DB 직접 변경
- `TASK_platform.md` 항목 추가
- #62 live E2E 전체 수행

## Initial Findings
- `V20260609140528__create_user_roles.sql`이 `user_roles(user_id, role)` 테이블을 생성한다.
- role check constraint는 `ROLE_USER`, `ROLE_ADMIN`만 허용한다.
- 기존 삭제되지 않은 사용자에게 `ROLE_USER`를 백필한다.
- `UserService.createForEmailPassword()`와 `UserService.createForOAuth()`가 신규 사용자에게 기본 `ROLE_USER`를 저장한다.
- `UserService.findRoles()`는 DB role을 조회하고, role row가 없으면 `ROLE_USER`로 fallback한다.
- `EmailPasswordAuthService`, `OAuth2SuccessHandler`, `AuthController` refresh 경로는 `userApi.findRoles(userId)` 결과로 access token을 발급한다.
- `JwtTokenProvider.createAccessToken()`은 `roles` claim에 전달받은 role 목록을 그대로 넣는다.
- platform admin endpoint는 Spring Security의 `hasRole('ADMIN')` 또는 `/api/v1/admin/** hasRole("ADMIN")` 패턴을 사용하므로 내부 권한은 `ROLE_ADMIN`과 맞다.
- `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`는 가입된 사용자에게 SQL로 `ROLE_ADMIN`을 추가하는 절차를 이미 제공한다.
- engagement `CurrentUser.requireAdmin()`은 JWT `roles` claim 안의 `ADMIN` 또는 단일 `role` claim의 `ADMIN`을 기대한다.
- 따라서 현재 남은 핵심 리스크는 "platform JWT가 `ROLE_ADMIN`만 담을 때 engagement가 이를 관리자 권한으로 인정하지 못할 수 있음"이다.

## Decision Criteria

### Option A: 현재 구현 확인 후 이슈 정리
선택 조건:
- 이슈의 주요 우려가 현재 `dev`에서 이미 해소되어 있다.
- engagement가 `ROLE_ADMIN`도 허용하도록 이미 갱신되어 있거나, E2E 계약상 `ROLE_ADMIN` 사용으로 합의되어 있다.

처리:
- 코드 변경 없이 검증 결과와 런북 근거를 남긴다.
- #86에 stale 항목과 현재 정본을 설명하고 close한다.

### Option B: platform JWT role claim 호환성 보강
선택 조건:
- engagement가 계속 `ADMIN`을 기대한다.
- 다른 repo 수정 없이 platform에서 해결해야 한다.
- platform 내부 DB/Spring Security 권한은 `ROLE_ADMIN`으로 유지해야 한다.

처리:
- DB role 정본은 `ROLE_USER`, `ROLE_ADMIN`으로 유지한다.
- Spring Security 내부 인증은 기존 `ROLE_*` 권한이 계속 동작해야 한다.
- JWT 외부 계약에서 engagement가 읽을 수 있는 `ADMIN` 표현을 함께 제공할지 결정한다.
- 관련 `JwtTokenProviderTest`와 인증 경로 테스트를 보강한다.

### Option C: engagement 수정 필요로 이슈 이관
선택 조건:
- 팀 합의상 JWT `roles` claim은 Spring Security 권한명인 `ROLE_*`만 담기로 결정한다.
- engagement가 수신 token을 정규화하는 책임을 갖기로 한다.

처리:
- platform 코드는 변경하지 않는다.
- #86에는 platform 현재 상태와 engagement 수정 필요 내용을 남긴다.
- 단, 현재 사용자 지시는 다른 repo 수정 금지이므로 이번 브랜치에서는 engagement를 건드리지 않는다.

## Result
- 최초/운영 어드민 부여 방식은 기존 결정대로 DB 수동 grant를 유지한다.
- 자동 seed admin, bootstrap admin, 승격 API, env/profile 변경은 추가하지 않았다.
- `ROLE_ADMIN` DB 정본과 platform 내부 Spring Security 권한은 유지했다.
- access token `roles` claim에는 기존 `ROLE_ADMIN` 값과 함께 외부 서비스 호환용 `ADMIN` alias를 추가한다.
  - 예: `ROLE_ADMIN` -> `ROLE_ADMIN`, `ADMIN`
- `JwtTokenProvider.getAuthentication()`은 bare role alias를 다시 Spring authority로 정규화한다.
  - 예: `ADMIN` -> `ROLE_ADMIN`
  - 예: `USER` -> `ROLE_USER`
- 따라서 platform 내부 admin endpoint는 기존 `hasRole('ADMIN')` 정책을 유지하고, engagement는 `roles` claim의 `ADMIN`을 읽을 수 있다.

## Test Plan

1. role 저장/조회 단위 테스트
   ```powershell
   .\gradlew.bat test --tests "*UserServiceTest"
   ```

2. JWT role claim/authority 테스트
   ```powershell
   .\gradlew.bat test --tests "*JwtTokenProviderTest"
   ```

3. 로그인/OAuth/refresh role 발급 경로 테스트
   ```powershell
   .\gradlew.bat test --tests "*EmailPasswordAuthServiceTest" --tests "*OAuth2SuccessHandlerTest" --tests "*AuthControllerTest"
   ```

4. admin 보안 통합 테스트
   ```powershell
   .\gradlew.bat test --tests "*AdminSecurityIntegrationTest" --tests "*AuditLogControllerTest"
   ```

5. 전체 검증
   ```powershell
   .\gradlew.bat clean build
   ```

## Working Notes
- 현재 브랜치: `fix/PLAT-086-admin-role-contract`
- 기준 브랜치: `dev`
- PLAT-091 문서는 `docs/ai/archive/20260610-plat-091-completed/`에 보관했다.
- 이번 작업에서는 다른 repo를 수정하지 않는다.
- 최초 어드민 자동 생성은 하지 않는다.
- profile/env 변경은 하지 않는다.
- `TASK_platform.md`는 초기 개발 목록이므로 수정하지 않는다.
- 검증 통과:
  - `.\gradlew.bat test --tests "*JwtTokenProviderTest"`
  - `.\gradlew.bat test --tests "*UserServiceTest" --tests "*EmailPasswordAuthServiceTest" --tests "*OAuth2SuccessHandlerTest" --tests "*AuthControllerTest" --tests "*AdminSecurityIntegrationTest" --tests "*AuditLogControllerTest"`
  - `.\gradlew.bat clean build`
