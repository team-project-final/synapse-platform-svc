# PLAT-073 Handoff

## Status
구현, 리뷰 후속 보강, 검증 완료.

## Branch
- Base: `origin/main`
- Planned working branch: `feature/PLAT-073-admin-data-requests`

## Archived
이전 current 문서는 아래 경로에 보관했다.

- `docs/ai/archive/20260611-plat-087-completed/TASK.md`
- `docs/ai/archive/20260611-plat-087-completed/CONTEXT.md`
- `docs/ai/archive/20260611-plat-087-completed/HANDOFF.md`

## Current Task
GDPR/data request API를 구현해 프론트 `admin_data_request_screen`이 mock 데이터를 제거하고 platform backend와 연동할 수 있게 한다.

## Key Decision
이번 작업은 admin 화면 연동용 MVP다.

포함:
- admin data request 목록
- 상세
- 상태 처리 액션
- 관리자 테스트/운영 생성 API
- analytics summary의 `data-requests` `NOT_IMPLEMENTED` 제거

제외:
- 실제 S3/file export
- 이메일 발송
- 타 서비스 데이터 통합 export
- 실제 사용자 삭제/마스킹 실행 연동
- 프로필 아바타 업로드
- admin settings 추가 구현

리뷰 후속 결정:
- `DATA_ERASURE`는 실제 삭제/마스킹을 수행하지 않는 동안 `EXECUTE`를 허용하지 않는다.
- `daysRemaining`은 floor 계산 대신 UI 표시용 보정 계산을 사용한다.
- 실제 삭제 연동 작업에서는 `gdpr_data_requests`의 이메일/표시명 스냅샷도 함께 마스킹해야 한다.

## Relevant Files

Admin analytics:
- `src/main/java/com/synapse/platform/admin/service/AdminAnalyticsService.java`
- `src/test/java/com/synapse/platform/admin/service/AdminAnalyticsServiceTest.java`
- `src/test/java/com/synapse/platform/admin/controller/AdminAnalyticsControllerTest.java`
- `src/test/java/com/synapse/platform/auth/config/AdminSecurityIntegrationTest.java`

Admin settings, already implemented:
- `src/main/java/com/synapse/platform/admin/controller/AdminSettingsController.java`
- `src/main/java/com/synapse/platform/admin/service/AdminSettingsService.java`

User/profile context:
- `src/main/java/com/synapse/platform/user/entity/User.java`
- `src/main/java/com/synapse/platform/user/repository/UserRepository.java`

Frontend reference, read-only:
- `C:\workspace\team_project_2\synapse-frontend\lib\services\platform\features\admin\presentation\screens\admin_screens\admin_data_request_screen.dart`

Prior docs:
- `docs/ai/archive/20260610-plat-071-completed/`
- `docs/ai/archive/20260610-plat-072-completed/`
- `docs/project-management/scope/SCOPE_platform.md`

## Expected API

```text
GET  /api/v1/admin/data-requests
GET  /api/v1/admin/data-requests/{id}
POST /api/v1/admin/data-requests
POST /api/v1/admin/data-requests/{id}/actions
```

Statuses:
- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `REJECTED`

Types:
- `DATA_ACCESS`
- `DATA_EXPORT`
- `DATA_ERASURE`

Actions:
- `APPROVE`
- `EXECUTE`
- `REJECT`

## Implementation Checklist
- [x] 현재 Flyway migration 마지막 번호 확인
- [x] `gdpr_data_requests` migration 추가
- [x] entity/enum/repository 추가
- [x] request/response DTO 추가
- [x] `AdminDataRequestService` 추가
- [x] `AdminDataRequestController` 추가
- [x] `AdminAnalyticsService`가 pending data request count를 사용하도록 보강
- [x] `AdminSecurityIntegrationTest`에 admin-only 접근 제어 추가
- [x] controller/service/analytics 테스트 추가
- [x] `HISTORY_platform.md` 갱신
- [x] `DATA_ERASURE` + `EXECUTE` conflict 처리
- [x] `daysRemaining` 생성 직후 30일 표시 보정
- [x] 삭제 요청 개인정보 스냅샷 마스킹 정책 문서화 및 `DATA_ERASURE` 실행 차단 테스트 반영

## Review Follow-up Work
- `GdprDataRequest.execute()` 또는 service action 처리에서 `DATA_ERASURE` 실행을 차단한다.
- conflict는 기존 `ResponseStatusException(HttpStatus.CONFLICT, ...)` 경로를 사용한다.
- `AdminDataRequestResponse.daysRemaining()`은 남은 기간이 있으면 올림 계산으로 반환한다.
- `DATA_ERASURE` 완료 오인을 막는 service/controller 테스트를 추가한다.
- `daysRemaining` 계산 테스트를 추가한다.

## Test Commands
```powershell
.\gradlew.bat test --tests "*AdminDataRequestServiceTest"
.\gradlew.bat test --tests "*AdminDataRequestControllerTest"
.\gradlew.bat test --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest"
.\gradlew.bat clean build
```

## Do Not Touch
- frontend repo code
- shared/gitops/learning/engagement/gateway repo code
- `.env`
- Spring profile
- 실제 export storage/S3 설정
- 실제 사용자 삭제/마스킹 플로우
- `TASK_platform.md`

## Notes
- 현재 git 상태에는 PLAT-087 문서 정리 변경도 남아 있다.
- PR 전에는 PLAT-087 문서 정리와 PLAT-073 구현 변경을 어떻게 묶을지 확인이 필요하다.
- Windows Kafka 임시파일 삭제 로그가 `clean build` 중 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.
- 리뷰 후속 보강 테스트 통과:
  - `.\gradlew.bat test --tests "*AdminDataRequestServiceTest" --tests "*AdminDataRequestControllerTest"`
  - `.\gradlew.bat test --tests "*AdminDataRequestServiceTest" --tests "*AdminDataRequestControllerTest" --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest" --tests "*PlatformModuleStructureTest"`
