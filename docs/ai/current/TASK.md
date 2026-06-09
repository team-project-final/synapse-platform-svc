# TASK — PLAT-063: frontend platform backend gap

> 출처: 루트 `docs/BACKEND_GAP_platform.md`, frontend `lib/services/platform/**` TODO, platform 실제 컨트롤러 매핑

## 상태

- Phase: A-1 User self-service 구현 완료 / A-2~A-6 후속
- 담당 Agent: Codex
- 시작일: 2026-06-09
- 작업 브랜치: `feature/PLAT-063-frontend-backend-gap`
- PR base: `dev`

---

## Goal

frontend platform 화면이 mock/TODO로 남겨둔 호출 중 platform-svc 책임 영역의 백엔드 API를 단계적으로 구현한다.

## Scope

- In Scope:
  - A-1 User 셀프서비스 프로필
  - A-2 Auth 보강
  - A-3 Notification 인박스
  - A-4 Billing 보강
  - A-5 Tenant 셀프관리
  - A-6 Admin 대시보드 보강
- Out of Scope:
  - 7번/B 항목: engagement/knowledge/learning 소관 화면
  - `TASK_platform.md` 원본 개발 목록 수정
  - production 배포
  - secret/API key 커밋

## Current Slice

이번 첫 구현은 A-1 중 DB와 기존 서비스 자산으로 바로 처리 가능한 API를 우선한다.

- [x] `GET /api/v1/users/me`
- [x] `PUT /api/v1/users/me`
- [x] `PUT /api/v1/users/me/password`
- [x] `DELETE /api/v1/users/me/oauth/{provider}`
- [x] `DELETE /api/v1/users/me`

후속으로 분리:

- [ ] `POST /api/v1/users/me/avatar` — 저장소/S3 정책 필요
- [x] timezone 필드 제거 — 현재 `user_settings` 스키마에 컬럼 없음, 저장되지 않는 값은 API 계약에서 제외

## Done When

- [x] `UserController` placeholder가 실제 self-service API를 노출한다.
- [x] 현재 로그인 사용자 ID는 기존 JWT Authentication 패턴으로 해석한다.
- [x] 프로필 조회/수정은 `users`와 `user_settings` 기존 컬럼만 사용한다. `language`는 `ko-KR`, `en-US`, `ja-JP`만 허용한다.
- [x] 비밀번호 변경은 현재 비밀번호 검증과 신규 비밀번호 해시 저장을 수행한다.
- [x] OAuth 연결 해제는 마지막 로그인 수단 제거를 막는다.
- [x] OAuth 연결 해제는 `oauth_identities` row lock으로 동시 해제 경합을 방어한다.
- [x] 계정 삭제는 기존 soft delete와 session revocation event를 재사용한다.
- [x] JWT 인증 필터는 삭제/정지 등 로그인 불가 사용자의 access-token 요청을 차단한다.
- [x] controller/service 테스트를 추가한다.
- [x] `TASK_platform.md`는 변경하지 않는다.

## Validation

우선 실행:

```powershell
.\gradlew.bat test --tests "*JwtAuthenticationFilterTest" --tests "*OAuthConnection*Test" --tests "*OAuthIdentityRepositoryLockingTest" --tests "*UserControllerTest" --tests "*UserServiceTest"
```

최종 확인:

```powershell
.\gradlew.bat test --tests "*User*"
.\gradlew.bat clean build
```

실행 결과(2026-06-09):

- `.\gradlew.bat test --tests "*JwtAuthenticationFilterTest" --tests "*OAuthConnection*Test" --tests "*OAuthIdentityRepositoryLockingTest" --tests "*UserControllerTest" --tests "*UserServiceTest"`: PASS
- `.\gradlew.bat clean build`: PASS

> Windows Embedded Kafka 임시 디렉터리 삭제 실패 로그가 출력되지만 Gradle 결과는 성공이다.
