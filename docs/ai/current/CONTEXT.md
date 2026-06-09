# CONTEXT - PLAT-064

## 현재 문제

W5 프론트 연동 준비 중 어드민 화면을 테스트하려면 `ROLE_ADMIN`을 가진 계정이 필요하다는 점이 확인됐다.
현재 platform-svc는 JWT access token 발급 시 기본 role만 넣는 구조라, 서비스 내부에서 어드민 권한을 부여하거나 보존하는 경로가 없다.

## 기존 구조

- `/api/v1/admin/**`는 Spring Security에서 `hasRole("ADMIN")`으로 보호된다.
- JWT `roles` claim은 `JwtTokenProvider`가 `SimpleGrantedAuthority`로 변환한다.
- `ROLE_ADMIN`이 token에 있으면 기존 admin controller 접근 정책은 그대로 동작한다.
- 사용자 상태는 `users.status`와 `deleted_at` 기반으로 관리된다.
- 정지/삭제 사용자는 로그인 및 refresh 경로에서 차단해야 한다.

## 구현 방향

- `user_roles` 테이블을 추가해 사용자별 role을 저장한다.
- 삭제되지 않은 기존 사용자에게 `ROLE_USER`를 백필한다.
- 신규 이메일/비밀번호 가입과 OAuth 가입 시 `ROLE_USER`를 저장한다.
- access token 발급 시 `UserApi.findRoles(userId)`를 통해 DB role을 읽는다.
- role row가 없는 예외 상황에서는 기존 호환성을 위해 `ROLE_USER`로 fallback한다.
- role 조회 순서는 `created_at ASC`로 고정해 JWT roles claim이 흔들리지 않게 한다.

## 어드민 부여 정책

- env/profile은 수정하지 않는다.
- 서비스 코드에서 최초 어드민을 자동 생성하지 않는다.
- 운영 dummy admin 계정은 만들지 않는다.
- 운영 최초 어드민은 승인된 사용자가 먼저 가입한 뒤, 승인된 DB 작업으로 `ROLE_ADMIN`을 추가한다.
- 로컬 프론트 테스트도 동일하게 가입 후 SQL로 `ROLE_ADMIN`을 부여한다.

## 참고 파일

- `src/main/resources/db/migration/V20260609140528__create_user_roles.sql`
- `src/main/java/com/synapse/platform/user/entity/UserRole.java`
- `src/main/java/com/synapse/platform/user/repository/UserRoleRepository.java`
- `src/main/java/com/synapse/platform/user/api/UserApi.java`
- `src/main/java/com/synapse/platform/user/service/UserService.java`
- `src/main/java/com/synapse/platform/auth/service/EmailPasswordAuthService.java`
- `src/main/java/com/synapse/platform/auth/service/OAuth2SuccessHandler.java`
- `src/main/java/com/synapse/platform/auth/controller/AuthController.java`
- `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md`
