# HANDOFF

> Agent 간 작업 전달 문서입니다.

## FROM

Codex

## TO

Worker

## 작업 브랜치

`feature/PLAT-063-frontend-backend-gap` (base: `origin/dev`)

## 요청 내용

frontend platform 화면 연동을 위해 platform-svc에 없는 백엔드 API를 구현한다. 루트 `docs/BACKEND_GAP_platform.md` 기준 A-1부터 진행하고, 7번/B 타 서비스 소관 항목은 제외한다. `TASK_platform.md`는 원본 개발 목록으로 유지한다.

## 첫 구현 단위

A-1 User 셀프서비스 프로필 API.

### API

| Method | Path | 목적 |
|---|---|---|
| GET | `/api/v1/users/me` | 내 프로필 조회 |
| PUT | `/api/v1/users/me` | 표시 이름/언어 저장 |
| PUT | `/api/v1/users/me/password` | 비밀번호 변경 |
| DELETE | `/api/v1/users/me/oauth/{provider}` | OAuth 연결 해제 |
| DELETE | `/api/v1/users/me` | 본인 계정 삭제 |

### 후속 제외

- `POST /api/v1/users/me/avatar`: storage 정책 필요
- timezone: 현재 DB 스키마 없음. 저장되지 않는 값이므로 이번 API 계약에서 제거

## 구현 지침

- controller는 기존 `Authentication` 파라미터 패턴을 따른다.
- validation은 `jakarta.validation`으로 처리한다.
- `language`는 기존 `user_settings.locale`에 저장하며 `ko-KR`, `en-US`, `ja-JP`만 허용한다.
- 비밀번호 정책은 기존 `EmailPasswordAuthRequest`와 동일하게 8자 이상, 영문/숫자/특수문자 포함으로 맞춘다.
- 서비스 레벨에서 마지막 로그인 수단 제거를 막는다.
- OAuth 연결 해제는 `oauth_identities` 조회에 pessimistic write lock을 건다.
- 본인 계정 삭제 후 `UserSessionsRevocationRequested`를 발행한다.
- JWT 인증 필터는 `UserApi.isLoginAllowed()`로 삭제/정지 사용자의 access-token 요청을 차단한다.
- 신규 예외는 `BusinessException`을 상속한다.
- `TASK_platform.md`는 수정하지 않는다.

## 검증

```powershell
.\gradlew.bat test --tests "*JwtAuthenticationFilterTest" --tests "*OAuthConnection*Test" --tests "*OAuthIdentityRepositoryLockingTest" --tests "*UserControllerTest" --tests "*UserServiceTest"
.\gradlew.bat clean build
```
