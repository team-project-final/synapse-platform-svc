# PLAT-073 Context

## Current State
- Target task: PLAT-073
- Planned branch: `feature/PLAT-073-admin-data-requests`
- Base: `origin/main`
- 작업 대상 repo: `synapse-platform-svc`
- 현재 상태: 구현 및 리뷰 후속 보강 완료

## Background
프론트에서 platform backend 대기 항목으로 아래를 공유했다.

1. 프로필 아바타 업로드
2. admin 시스템 설정/피처 플래그
3. GDPR 데이터 요청

확인 결과:
- 아바타 업로드는 미구현이며 storage/S3 정책 결정이 필요하다.
- admin 시스템 설정/피처 플래그 API는 이미 `GET/PUT /api/v1/admin/settings`로 구현되어 있다.
- GDPR/data request API는 미구현이며, admin analytics summary에서 `NOT_IMPLEMENTED`로 표시 중이다.

따라서 이번 작업은 GDPR/data request API를 먼저 처리한다.

## Existing Backend Evidence

### Admin Settings
이미 구현됨:
- `GET /api/v1/admin/settings`
- `PUT /api/v1/admin/settings`

관련 파일:
- `src/main/java/com/synapse/platform/admin/controller/AdminSettingsController.java`
- `src/main/java/com/synapse/platform/admin/service/AdminSettingsService.java`
- `src/main/java/com/synapse/platform/admin/dto/AdminSettingsResponse.java`
- `src/main/java/com/synapse/platform/admin/dto/AdminSettingsUpdateRequest.java`

### GDPR/Data Request
미구현:
- `GET /api/v1/admin/data-requests`
- `POST /api/v1/admin/data-requests`
- 상태 처리 API

현재 analytics pending item:
- key: `data-requests`
- label: `GDPR 요청`
- count: `null`
- status: `NOT_IMPLEMENTED`

관련 파일:
- `src/main/java/com/synapse/platform/admin/service/AdminAnalyticsService.java`
- `src/test/java/com/synapse/platform/admin/service/AdminAnalyticsServiceTest.java`
- `src/test/java/com/synapse/platform/admin/controller/AdminAnalyticsControllerTest.java`
- `src/test/java/com/synapse/platform/auth/config/AdminSecurityIntegrationTest.java`

## Frontend Evidence
화면:
`C:\workspace\team_project_2\synapse-frontend\lib\services\platform\features\admin\presentation\screens\admin_screens\admin_data_request_screen.dart`

현재 상태:
- `_MockDataRequest` 사용
- TODO: platform-svc GDPR/데이터 요청 API 연동

필요 UI 데이터:
- `receivedAt`
- `user`
- `type`
- `status`
- `daysRemaining`
- 상세 데이터 요약
- 실행 로그
- 승인/실행/거부 액션

## Related Prior Docs
- `docs/ai/archive/20260610-plat-071-completed/`
  - Admin GDPR/data request API는 PLAT-073으로 분리
  - `data-requests` pending item은 `NOT_IMPLEMENTED`
- `docs/ai/archive/20260610-plat-072-completed/`
  - 시스템 설정/피처 플래그는 처리
  - GDPR data request API는 다음 후보
- `docs/project-management/scope/SCOPE_platform.md`
  - GDPR/CCPA 데이터 내보내기 요청 및 상태 조회가 scope에 있음
- `docs/project-management/task/TASK_platform.md`
  - 사용자 데이터 내보내기, 개인정보 마스킹 항목 존재

`TASK_platform.md`는 초기 개발 목록 문서이므로 이번 작업에서 수정하지 않는다.

## Proposed Implementation Shape

### Package
기존 admin package 안에 GDPR/data request 관리 기능을 추가한다.

예상 파일:
- `admin/controller/AdminDataRequestController.java`
- `admin/service/AdminDataRequestService.java`
- `admin/entity/GdprDataRequest.java`
- `admin/entity/GdprDataRequestStatus.java`
- `admin/entity/GdprDataRequestType.java`
- `admin/repository/GdprDataRequestRepository.java`
- `admin/dto/AdminDataRequestPageResponse.java`
- `admin/dto/AdminDataRequestResponse.java`
- `admin/dto/AdminDataRequestCreateRequest.java`
- `admin/dto/AdminDataRequestActionRequest.java`

### Migration
새 Flyway migration:
- `gdpr_data_requests` table
- status/type check constraint
- user_id index
- status/received_at index

마이그레이션 파일명은 현재 repo의 다음 순번을 확인한 뒤 작성한다.

### Service Rules
- `PENDING` -> `PROCESSING`: approve
- `PROCESSING` -> `COMPLETED`: execute
- `PENDING|PROCESSING` -> `REJECTED`: reject
- 완료/거부 상태는 추가 액션 불가
- 대상 user가 없거나 deleted 상태라면 요청 생성 실패 또는 상세에 상태 표시
- `dueAt = receivedAt + 30 days`

## API Contract Draft

### List
`GET /api/v1/admin/data-requests`

Query:
- `status`
- `q`
- `page`
- `size`

### Detail
`GET /api/v1/admin/data-requests/{id}`

### Action
`POST /api/v1/admin/data-requests/{id}/actions`

Body:
```json
{
  "action": "EXECUTE",
  "reason": "처리 완료"
}
```

### Create
`POST /api/v1/admin/data-requests`

Body:
```json
{
  "userId": "uuid",
  "type": "DATA_EXPORT",
  "reason": "관리자 테스트 요청"
}
```

## Analytics Integration
`AdminAnalyticsService`의 pending item 보강:

Before:
```json
{
  "key": "data-requests",
  "label": "GDPR 요청",
  "count": null,
  "severity": "INFO",
  "status": "NOT_IMPLEMENTED"
}
```

After:
```json
{
  "key": "data-requests",
  "label": "GDPR 요청",
  "count": 3,
  "severity": "INFO",
  "status": "ACTION_REQUIRED"
}
```

count는 `PENDING` + `PROCESSING` 기준으로 계산한다.

## Open Questions
- 관리자 생성 API를 MVP에 포함할지, 테스트 fixture로만 만들지 결정 필요.
- 사용자 self-service `POST /api/v1/users/me/data-requests`를 이번 범위에 포함할지 여부.
- 실제 export 파일 생성/S3 저장은 별도 작업으로 둘지 확정 필요.

현재 추천:
- admin screen 연동을 우선하므로 admin list/detail/action/create까지만 구현한다.
- user self-service와 실제 export file 생성은 후속 작업으로 분리한다.

## Review Follow-up Context

작업 리뷰에서 백엔드에서 처리 가능한 보강 항목이 확인됐다.

### `DATA_ERASURE` 실행 의미 정리
- 현재 구현은 request type과 무관하게 `EXECUTE`를 `COMPLETED`로 처리한다.
- `DATA_ERASURE`는 실제 사용자 삭제/마스킹을 수행하지 않으면 완료로 표시하면 안 된다.
- 이번 보강에서는 실제 삭제 호출을 새로 묶지 않고, `DATA_ERASURE` + `EXECUTE`를 `409 CONFLICT`로 차단한다.
- 실제 삭제/마스킹은 기존 admin user delete 또는 user self-service delete 흐름과 연결해야 하므로 별도 작업이다.

### `daysRemaining` 계산
- 현재 floor 계산은 생성 직후에도 29일로 내려갈 수 있다.
- 프론트 화면은 "30일 기한 남은 일수"로 보여주므로 표시값은 올림 또는 날짜 기준으로 계산해야 한다.
- 완료/거부 상태는 계속 `0`을 반환한다.

### 개인정보 스냅샷
- `gdpr_data_requests.user_email`, `user_display_name`은 admin 화면 검색/식별용 스냅샷이다.
- 실제 삭제 요청을 수행하는 작업에서는 사용자 테이블뿐 아니라 요청 테이블 스냅샷도 마스킹해야 한다.
- 이번 보강에서는 `DATA_ERASURE` 실행을 차단해 "삭제 완료" 오인을 막고, 실제 마스킹 연동은 후속 작업으로 둔다.

## Risks
- GDPR/CCPA 명칭 때문에 실제 법적 완전성을 암시하면 안 된다. 이번 작업은 platform-local request management MVP다.
- 타 서비스 데이터 export는 MSA 간 별도 계약이 필요하다.
- 개인정보가 admin API 응답에 과도하게 노출되지 않도록 응답 필드를 제한해야 한다.
- 상태 전이가 느슨하면 잘못된 완료/거부 처리가 생긴다.
- `DATA_ERASURE`를 완료 처리하면 실제 삭제가 된 것처럼 오해될 수 있으므로 반드시 실행 차단 또는 실제 삭제 연동 중 하나를 선택해야 한다.

## Verification Direction
필수:
- service 상태 전이 테스트
- controller request/response 테스트
- admin security 테스트
- analytics pending item 테스트
- `DATA_ERASURE` execute conflict 테스트
- `daysRemaining` 표시 계산 테스트
- `clean build`

권장:
- migration schema 테스트가 있으면 추가
- invalid status/type/action 검증
- deleted user 요청 처리 정책 테스트

## Verification Result
통과:
- `.\gradlew.bat test --tests "*AdminDataRequestServiceTest" --tests "*AdminDataRequestControllerTest"`
- `.\gradlew.bat test --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest"`
- `.\gradlew.bat test --tests "*AdminDataRequestServiceTest" --tests "*AdminDataRequestControllerTest" --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest" --tests "*PlatformModuleStructureTest"`
- `.\gradlew.bat clean build`

참고:
- `clean build` 중 Windows Kafka 임시파일 삭제 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.
- 리뷰 후속 보강으로 `DATA_ERASURE` execute conflict와 `daysRemaining` 표시 계산 테스트를 추가했다.
