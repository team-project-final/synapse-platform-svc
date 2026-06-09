# HANDOFF - PLAT-064

## FROM

Codex

## TO

Worker

## 작업 브랜치

`feature/PLAT-064-user-roles` (base: `origin/dev`)

## 요청 내용

프론트 연동 테스트와 실제 어드민 API 접근을 위해 platform-svc에 DB 기반 사용자 role 저장 및 JWT 반영 경로를 추가한다.
최초 어드민은 자동 seed나 profile 설정이 아니라 승인된 DB 작업으로 부여한다.

## 구현 지침

- `user_roles` 테이블을 추가한다.
- 가입 시 기본 `ROLE_USER`를 저장한다.
- access token 발급 경로는 DB role을 조회한다.
- refresh 경로는 기존 Redis token 검증 후 `UserApi.isLoginAllowed(userId)`를 확인하고, 통과한 사용자에게만 새 access/refresh token을 발급한다.
- 기존 admin security rule은 그대로 둔다.
- env, profile, secret, GitOps overlay는 수정하지 않는다.
- `TASK_platform.md`는 수정하지 않는다.

## 운영 절차

- 운영 최초 어드민은 사용자가 정상 가입한 뒤 `docs/runbooks/ADMIN_ROLE_MANUAL_GRANT.md` 절차로 `ROLE_ADMIN`을 부여한다.
- role 부여 후 사용자는 다시 로그인해야 새 access token에 `ROLE_ADMIN`이 포함된다.
- 삭제된 사용자에게는 role을 부여하지 않는다.

## 검증 명령

```powershell
.\gradlew.bat test --tests "*UserServiceTest" --tests "*EmailPasswordAuthServiceTest" --tests "*AuthControllerTest" --tests "*OAuth2SuccessHandlerTest"
.\gradlew.bat clean build
```
