# TASK - PLAT-064: DB 기반 사용자 Role 및 어드민 부여 경로

> 출처: W5 프론트 연동 준비 중 발견된 어드민 권한 부여 공백

## 상태

- Phase: 구현 진행
- 담당 Agent: Codex
- 시작일: 2026-06-09
- 작업 브랜치: `feature/PLAT-064-user-roles`
- PR base: `dev`

---

## 목표

platform-svc가 JWT 발급 시 고정 `ROLE_USER`만 넣던 구조를 DB 기반 역할 조회 구조로 전환한다.
어드민 계정은 서비스 코드에서 자동 생성하지 않고, 승인된 가입 계정에 대해 수동 DB 작업으로 `ROLE_ADMIN`을 부여한다.

## 범위

In Scope:

- `user_roles` 테이블 생성 및 삭제되지 않은 기존 사용자 `ROLE_USER` 백필
- 신규 이메일/비밀번호 가입 사용자 기본 `ROLE_USER` 저장
- 신규 OAuth 가입 사용자 기본 `ROLE_USER` 저장
- 로그인, OAuth 성공, refresh 재발급 시 DB role 기반 JWT 발급
- 정지/삭제 사용자 refresh 차단 유지
- 로컬/운영 어드민 role 수동 부여 runbook 작성

Out of Scope:

- env/profile 수정
- 서비스 시작 시 최초 어드민 자동 생성
- 운영 dummy admin 계정 생성
- `TASK_platform.md` 항목 추가 또는 수정
- 다른 서비스 DB/권한 체계 변경

## 결정 사항

- role 정본은 platform DB의 `user_roles`로 둔다.
- 기본 사용자는 `ROLE_USER`, 어드민은 추가 role인 `ROLE_ADMIN`으로 표현한다.
- 기존 `/api/v1/admin/**`의 `hasRole("ADMIN")` 정책은 유지한다.
- 최초 운영 어드민은 정상 가입 후 승인된 DB 작업으로만 부여한다.
- 로컬 프론트 연동 테스트도 임의 seed/profile이 아니라 runbook SQL로 처리한다.

## Done When

- [x] `user_roles` Flyway migration 추가
- [x] `UserRole` entity 및 repository 추가
- [x] `UserApi.findRoles(UUID)` 추가
- [x] 신규 사용자 생성 시 기본 role 저장
- [x] 로그인/OAuth/refresh access token 발급 시 DB role 사용
- [x] refresh 시 정지/삭제 사용자 차단 유지
- [x] 어드민 role 수동 부여 문서 추가
- [x] 관련 단위 테스트 보강
- [x] targeted test 통과
- [x] `clean build` 통과

## 검증 예정

```powershell
.\gradlew.bat test --tests "*UserServiceTest" --tests "*EmailPasswordAuthServiceTest" --tests "*AuthControllerTest" --tests "*OAuth2SuccessHandlerTest"
.\gradlew.bat clean build
```

검증 결과(2026-06-09):

- `.\gradlew.bat test --tests "*UserServiceTest" --tests "*EmailPasswordAuthServiceTest" --tests "*AuthControllerTest" --tests "*OAuth2SuccessHandlerTest"`: PASS
- `.\gradlew.bat clean build`: PASS

> Windows Embedded Kafka 종료 중 임시 디렉터리 삭제 실패 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.
