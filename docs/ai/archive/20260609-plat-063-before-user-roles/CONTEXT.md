# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.

## 현재 확정된 것

- 루트 `docs/BACKEND_GAP_platform.md`는 frontend platform 화면이 호출하지만 platform-svc에 없는 API를 정리한 문서다.
- 7번/B 항목은 타 서비스 소관이므로 이번 platform 백엔드 구현 범위에서 제외한다.
- `TASK_platform.md`는 최초 개발 목록 문서이므로 새 항목을 추가하지 않는다.
- 작업 브랜치는 `origin/dev` 기준 `feature/PLAT-063-frontend-backend-gap`다.
- frontend profile/settings 화면은 아직 data layer가 없고 화면 TODO/mock 상태다.
- `UserController`는 `/api/v1/users` placeholder이며 실제 매핑이 없다.
- JWT 인증 후 `Authentication.getName()`은 userId UUID 문자열이다.
- `users` 테이블에는 `display_name`, `avatar_url`, `password_hash`, `deleted_at`이 있다.
- `user_settings` 테이블에는 `locale`, `theme`, `notification_prefs` 등이 있으나 timezone 컬럼은 없다.
- `oauth_identities`는 user FK cascade를 가지고 provider/provider_id unique index가 있다.
- 기존 계정 삭제/정지 시 session revocation은 `UserSessionsRevocationRequested` 이벤트로 처리한다.
- refresh-token은 세션 저장소에서 제거되지만 access-token은 stateless JWT라 인증 필터에서 사용자 상태를 재확인한다.

## 현재 구현 방향

- A-1을 첫 구현 단위로 잡는다.
- 프로필 API는 `displayName`, `email`, `avatarUrl`, `language(locale)` 중심으로 계약을 잡는다. `language` 허용 값은 `ko-KR`, `en-US`, `ja-JP`다.
- timezone은 저장 컬럼이 없으므로 request/response 계약에서 제외한다.
- avatar 파일 업로드는 storage 정책이 필요하므로 첫 구현에서 제외한다.
- password 변경은 password login 계정에 한정하고 현재 비밀번호 검증을 요구한다.
- OAuth 연결 해제는 `oauth_identities` row lock 후 계정의 마지막 로그인 수단을 제거하지 못하게 막는다.
- 계정 삭제는 기존 `User.softDelete()`와 session revocation event를 재사용한다.

## 참고 파일

- `src/main/java/com/synapse/platform/user/controller/UserController.java`
- `src/main/java/com/synapse/platform/user/service/UserService.java`
- `src/main/java/com/synapse/platform/user/entity/User.java`
- `src/main/java/com/synapse/platform/user/entity/UserSettings.java`
- `src/main/java/com/synapse/platform/auth/entity/OAuthIdentity.java`
- `src/main/java/com/synapse/platform/auth/repository/OAuthIdentityRepository.java`
- `src/main/java/com/synapse/platform/user/api/UserSessionsRevocationRequested.java`
- `src/test/java/com/synapse/platform/user/controller/AdminUserControllerTest.java`
- `src/test/java/com/synapse/platform/user/service/UserServiceTest.java`
