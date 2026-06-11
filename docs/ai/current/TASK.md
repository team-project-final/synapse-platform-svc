# PLAT-073 Admin GDPR 데이터 요청 API

## Task ID
PLAT-073

## Title
Admin GDPR/data request 관리 API 구현

## Owner
platform

## Status
DONE

## Priority
P1

## Branch
`feature/PLAT-073-admin-data-requests`

## Base
`origin/main`

## Step Goal
프론트 `admin_data_request_screen`이 mock 데이터를 제거하고 platform-svc의 실제 API로 GDPR/data request 목록, 상세, 처리 액션을 연동할 수 있게 한다.

현재 admin analytics summary는 `data-requests` pending item을 `NOT_IMPLEMENTED`로 내려준다. 이번 작업에서는 platform 소관 범위 안에서 GDPR/data request 저장소와 admin API를 추가하고, analytics summary도 실제 pending/processing 건수를 반영하도록 보강한다.

## Done When
- [x] GDPR/data request 저장 테이블이 추가된다.
- [x] request type/status enum과 entity/repository/service가 추가된다.
- [x] `GET /api/v1/admin/data-requests`가 요청 목록을 page 형태로 반환한다.
- [x] `GET /api/v1/admin/data-requests/{id}`가 요청 상세와 처리 로그/요약을 반환한다.
- [x] `POST /api/v1/admin/data-requests/{id}/actions`가 approve/execute/reject 액션을 처리한다.
- [x] 관리자 테스트/운영용 `POST /api/v1/admin/data-requests` 생성 API를 제공한다.
- [x] `GET /api/v1/admin/analytics/summary`의 `data-requests` pending item이 `NOT_IMPLEMENTED` 대신 실제 상태를 반영한다.
- [x] `ROLE_ADMIN` 접근 제어가 적용된다.
- [x] 관련 단위/컨트롤러/보안 테스트와 `clean build`를 통과한다.
- [x] `docs/project-management/history/HISTORY_platform.md`에 작업 이력이 기록된다.
- [x] 리뷰 후속 보강: `DATA_ERASURE`의 실제 삭제 오인 가능성을 제거한다.
- [x] 리뷰 후속 보강: `daysRemaining` 계산이 생성 직후 30일로 표시되도록 보정한다.
- [x] 리뷰 후속 보강: 삭제 요청 관련 개인정보 스냅샷 마스킹 정책을 문서화하고, `DATA_ERASURE` 실행 차단 테스트로 삭제 완료 오인을 방지한다.

## Scope

### In Scope
- platform repo 내부 구현
- admin GDPR/data request API
- platform-local 데이터 요청 저장/조회/상태 전이
- admin dashboard pending item의 `data-requests` 상태 보강
- 프론트 `admin_data_request_screen`이 필요한 row/detail/action 계약 정의
- 테스트 보강

### Out of Scope
- 프론트 코드 수정
- 실제 S3/파일 export 생성
- 이메일 발송
- 타 서비스 데이터까지 포함한 완전한 GDPR export
- 운영 Schema Registry/Kafka 설정
- 프로필 아바타 업로드 API
- admin system settings API 추가 구현
- `TASK_platform.md` 개발 목록 수정

## Current Evidence

### Existing Backend State
- `GET /api/v1/admin/settings`, `PUT /api/v1/admin/settings`는 이미 구현되어 있다.
- GDPR/data request API는 미구현이다.
- `AdminAnalyticsService`는 `data-requests` pending item을 `NOT_IMPLEMENTED`로 반환한다.
- 기존 문서에서 GDPR/data request API는 PLAT-073 후보로 분리되어 있다.

관련 파일:
- `src/main/java/com/synapse/platform/admin/service/AdminAnalyticsService.java`
- `src/test/java/com/synapse/platform/admin/service/AdminAnalyticsServiceTest.java`
- `src/test/java/com/synapse/platform/admin/controller/AdminAnalyticsControllerTest.java`
- `src/test/java/com/synapse/platform/auth/config/AdminSecurityIntegrationTest.java`
- `docs/ai/archive/20260610-plat-071-completed/`
- `docs/ai/archive/20260610-plat-072-completed/`

### Frontend Need
프론트 화면:
`C:\workspace\team_project_2\synapse-frontend\lib\services\platform\features\admin\presentation\screens\admin_screens\admin_data_request_screen.dart`

현재 화면은 mock data를 사용한다.

화면이 필요로 하는 값:
- 접수일
- 사용자
- 요청 유형
- 상태
- 30일 처리 기한 남은 일수
- 상세 데이터 요약
- 실행 로그
- 승인/실행/거부 액션

## Proposed API Contract

### GET `/api/v1/admin/data-requests`
목록 조회.

Query:
- `status`: optional, `pending|processing|completed|rejected`
- `q`: optional, user email/display name 검색
- `page`: default `0`
- `size`: default `20`

Response:
```json
{
  "content": [
    {
      "id": "uuid",
      "userId": "uuid",
      "userEmail": "user@example.com",
      "userDisplayName": "User",
      "type": "DATA_EXPORT",
      "typeLabel": "데이터 내보내기",
      "status": "PENDING",
      "statusLabel": "대기",
      "receivedAt": "2026-06-11T00:00:00Z",
      "dueAt": "2026-07-11T00:00:00Z",
      "daysRemaining": 30,
      "latestLog": "요청 접수"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### GET `/api/v1/admin/data-requests/{id}`
상세 조회.

Response includes:
- 목록 row fields
- `dataSummary`
- `executionLogs`

### POST `/api/v1/admin/data-requests/{id}/actions`
상태 전이.

Request:
```json
{
  "action": "APPROVE",
  "reason": "관리자 승인"
}
```

Actions:
- `APPROVE`: `PENDING` -> `PROCESSING`
- `EXECUTE`: `PROCESSING` -> `COMPLETED`
- `REJECT`: `PENDING|PROCESSING` -> `REJECTED`

Review follow-up:
- `DATA_ACCESS`, `DATA_EXPORT`는 MVP 범위에서 `EXECUTE` 가능하다.
- `DATA_ERASURE`는 실제 사용자 삭제/마스킹을 수행하지 않는 한 `EXECUTE`를 허용하지 않는다.
- `DATA_ERASURE`에 `EXECUTE` 요청이 들어오면 `409 CONFLICT`로 막고, 별도 실제 삭제 연동 작업으로 분리한다.

### POST `/api/v1/admin/data-requests`
관리자 테스트/운영 생성용. 프론트에서 당장 쓰지 않더라도 테스트 데이터 생성에 유용하다.

Request:
```json
{
  "userId": "uuid",
  "type": "DATA_EXPORT",
  "reason": "사용자 요청 접수"
}
```

## Data Model

Table candidate: `gdpr_data_requests`

Fields:
- `id uuid primary key`
- `user_id uuid not null`
- `request_type varchar(40) not null`
- `status varchar(40) not null`
- `reason varchar(500)`
- `admin_note varchar(1000)`
- `data_summary text`
- `execution_log text`
- `received_at timestamptz not null`
- `due_at timestamptz not null`
- `processed_at timestamptz`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Status:
- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `REJECTED`

Type:
- `DATA_ACCESS`
- `DATA_EXPORT`
- `DATA_ERASURE`

## Design Notes
- 30일 기한은 `receivedAt + 30 days`로 계산한다.
- `daysRemaining`은 서버 기준 현재 시각과 `dueAt` 차이를 UI 표시용으로 계산한다. 생성 직후 29일로 내려가지 않도록 올림 또는 날짜 기준 계산을 사용하고, 완료/거부 상태는 `0`으로 반환한다.
- 실제 cross-service export는 이번 범위가 아니다.
- `dataSummary`는 platform-local 사용자의 기본 정보와 처리 상태 중심으로 구성한다.
- 민감한 개인정보를 과도하게 응답하지 않는다.
- 상태 전이는 명시적으로 제한한다.
- `data-requests` pending item은 `PENDING`/`PROCESSING` 건수를 기준으로 보여준다.
- `gdpr_data_requests`의 `userEmail`, `userDisplayName`은 admin 화면 검색/식별용 스냅샷이다. `DATA_ERASURE`가 실제 삭제 처리와 연결될 때는 요청 테이블 스냅샷도 함께 마스킹해야 한다.

## Review Follow-up Plan

이번 보강은 백엔드에서 처리 가능한 리뷰 지적만 다룬다.

### 1. `DATA_ERASURE` 실행 차단
- 현재 문제: `DATA_ERASURE`도 `EXECUTE` 시 실제 삭제 없이 `COMPLETED`가 되어 프론트에 "데이터 삭제 완료"처럼 보일 수 있다.
- 처리 방향: `DATA_ERASURE` 요청은 `APPROVE`와 `REJECT`까지만 허용하고, `EXECUTE`는 `409 CONFLICT`로 차단한다.
- 실제 사용자 삭제/마스킹 호출은 후속 작업으로 분리한다.
- 테스트: `DATA_ERASURE` + `EXECUTE`가 conflict를 반환하는 service/controller 테스트를 추가한다.

### 2. `daysRemaining` 표시 보정
- 현재 문제: `ChronoUnit.DAYS.between(now, dueAt)` floor 계산 때문에 생성 직후 30일 대신 29일로 보일 수 있다.
- 처리 방향: 남은 시간이 있으면 올림 계산을 적용해 UI 표시값이 기한 체감과 맞도록 한다.
- 테스트: `receivedAt + 30 days`인 open request가 생성 직후 `30`을 반환하는 테스트를 추가한다.

### 3. 개인정보 스냅샷 마스킹 정책 문서화
- 현재 문제: GDPR 요청 테이블이 `userEmail`, `userDisplayName` 스냅샷을 보관한다.
- 처리 방향: 이번 PR에서는 실제 삭제 실행을 하지 않으므로 즉시 마스킹 로직은 만들지 않는다. 다만 `DATA_ERASURE` 실행을 차단하고, 실제 삭제 연동 시 요청 테이블 스냅샷도 마스킹해야 한다는 정책을 문서에 명확히 남긴다.
- 테스트: `DATA_ERASURE`가 완료 상태로 가지 못하는 회귀 테스트로 "삭제 완료 오인"을 방지한다.

## Test Plan

단위 테스트:
```powershell
.\gradlew.bat test --tests "*AdminDataRequestServiceTest"
```

컨트롤러 테스트:
```powershell
.\gradlew.bat test --tests "*AdminDataRequestControllerTest"
```

analytics/security 회귀:
```powershell
.\gradlew.bat test --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest"
```

전체 검증:
```powershell
.\gradlew.bat clean build
```

## Constraints
- 다른 repo는 수정하지 않는다.
- `.env`, profile, 운영 설정은 수정하지 않는다.
- 실제 파일 export/S3/email은 이번 작업에 넣지 않는다.
- `TASK_platform.md`는 초기 개발 목록 문서이므로 수정하지 않는다.
- PR/issue 본문은 UTF-8 파일 기반으로 작성한다.

## Notes
- PLAT-087 current 문서는 `docs/ai/archive/20260611-plat-087-completed/`에 보관했다.
- 구현 및 검증 완료.
- 리뷰 후속 보강까지 완료했다.
- `DATA_ERASURE`는 실제 삭제/마스킹 연동 전까지 `EXECUTE`를 `409 CONFLICT`로 차단한다.
- `daysRemaining`은 남은 시간이 있으면 올림 계산으로 반환한다.
- 실제 삭제 연동 시 `gdpr_data_requests`의 `userEmail`, `userDisplayName` 스냅샷도 함께 마스킹해야 한다는 정책을 문서화했다.
- 검증 통과:
  - `.\gradlew.bat test --tests "*AdminDataRequestServiceTest" --tests "*AdminDataRequestControllerTest"`
  - `.\gradlew.bat test --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest"`
  - `.\gradlew.bat test --tests "*AdminDataRequestServiceTest" --tests "*AdminDataRequestControllerTest" --tests "*AdminAnalytics*" --tests "*AdminSecurityIntegrationTest" --tests "*PlatformModuleStructureTest"`
  - `.\gradlew.bat clean build`
