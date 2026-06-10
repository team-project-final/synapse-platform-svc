# PLAT-072 Admin 시스템 설정/피처 플래그 API

## Task ID
PLAT-072

## Title
Admin 시스템 설정/피처 플래그 API 구현

## Owner
platform

## Status
DONE

## Priority
P2

## Step Goal
관리자가 Admin 시스템 설정 화면에서 플랜 할당량을 조회하고 피처 플래그와 API 요청 제한 설정을 저장한다.

## Done When
- [x] `GET /api/v1/admin/settings`가 현재 시스템 설정을 반환한다.
- [x] `PUT /api/v1/admin/settings`가 피처 플래그와 API 요청 제한 설정을 저장한 뒤 최신 설정을 반환한다.
- [x] 응답에 플랜 할당량, 피처 플래그, API 요청 제한 값이 포함된다.
- [x] 플랜 할당량은 기존 `plan_quotas` 데이터를 조회 전용으로 사용한다.
- [x] 피처 플래그와 API 요청 제한 값은 platform 소유 저장소에 영속화한다.
- [x] `/api/v1/admin/settings`는 `ROLE_ADMIN`만 접근 가능하다.
- [x] 일반 사용자 또는 미인증 요청은 기존 Admin 보안 정책대로 차단된다.
- [x] 입력 검증 실패 시 400 응답과 명확한 에러 메시지를 반환한다.
- [x] 단위 테스트, 컨트롤러 테스트, 보안 통합 테스트를 추가하거나 보강한다.
- [x] `./gradlew.bat test --tests "*AdminSettings*"` 또는 동등한 단일 테스트가 통과한다.
- [x] `./gradlew.bat clean build`가 통과한다.
- [x] `docs/project-management/history/HISTORY_platform.md`에 작업 이력을 남긴다.

## Scope

### In Scope
- Admin 설정 조회/저장 API 추가
- `com.synapse.platform.admin` 하위 controller/service/dto 구성
- 피처 플래그 및 rate limit 설정 저장용 DB migration/entity/repository 추가
- 기존 `TenantApi` 기반 플랜 quota 조회 모델 구성
- `ROLE_ADMIN` 인가 검증
- 테스트 코드 작성
- 현재 작업문서 갱신 및 PLAT-071 문서 archive 보관

### Out of Scope
- GDPR 데이터 요청 API (`/api/v1/admin/data-requests`, PLAT-073 후보)
- 실제 런타임 feature flag enforcement
- 실제 rate limiting filter/gateway enforcement
- `plan_quotas` 수정 API 또는 quota 값 변경
- frontend 코드 수정
- gateway/gitops/shared 프로젝트 수정
- `.env`, profile, 실행 포트 설정 변경
- `TASK_platform.md` 신규 항목 추가

## Dependencies
- Root 요구사항: `C:\workspace\team_project_2\docs\BACKEND_GAP_platform.md` A-6
- 선행 작업: PLAT-071 Admin analytics summary API
- 기존 Admin 보안: `/api/v1/admin/**` `ROLE_ADMIN`
- 기존 quota 데이터: `plan_quotas`, `PlanQuotaInfo`, `TenantApi`
- 프론트 화면: `admin_system_settings_screen.dart`

## Due Date
2026-06-12

## Requirement Context
Root `BACKEND_GAP_platform.md`의 A-6 Admin 대시보드 보강 항목 중 PLAT-071에서 분석 요약 API는 처리했다. 남은 platform 소관 항목은 시스템 설정/피처 플래그와 GDPR 데이터 요청이다.

이번 작업은 먼저 프론트의 Admin System Settings 화면을 연결하기 위한 `GET`/`PUT /api/v1/admin/settings`를 구현한다.

## Frontend Contract
프론트 화면은 현재 mock 상태이며 다음 설정 묶음을 기대한다.

- Plan Quota
- Feature Flags
- Rate Limit

초기 백엔드 응답은 아래 형태를 기준으로 한다. 구현 중 기존 DTO 스타일에 맞춰 필드명은 조정할 수 있으나, 프론트가 바로 붙을 수 있게 의미는 유지한다.

```json
{
  "planQuotas": [
    {
      "planCode": "free",
      "displayName": "Free",
      "maxNotes": 1000,
      "maxCards": 5000,
      "maxStorageBytes": 1073741824,
      "maxAiTokensMonthly": 100000,
      "maxAiCardGenerationsMonthly": 100,
      "maxUsersPerTenant": 5
    }
  ],
  "featureFlags": [
    {
      "key": "aiCardAutoGeneration",
      "label": "AI 카드 자동 생성",
      "enabled": true
    }
  ],
  "rateLimit": {
    "apiRequestsPerMinute": 100
  },
  "updatedAt": "2026-06-10T00:00:00Z"
}
```

`PUT` 요청은 운영 중 영향이 큰 plan quota를 제외하고, 피처 플래그와 rate limit만 저장한다.

```json
{
  "featureFlags": [
    {
      "key": "aiCardAutoGeneration",
      "enabled": true
    }
  ],
  "rateLimit": {
    "apiRequestsPerMinute": 100
  }
}
```

## Design Notes
- Plan quota는 billing/auth 쪽 기존 계약과 연결되어 있어 이번 작업에서는 조회 전용으로 둔다.
- Admin 모듈은 Modulith 경계를 지키기 위해 auth 내부 repository/entity 대신 `TenantApi.listPlanQuotas()`를 사용한다.
- 피처 플래그와 rate limit은 현재 platform 내부 저장 스키마가 없으므로 migration을 추가한다.
- 설정 key는 프론트 표시 문구가 아니라 안정적인 영문 key를 기준으로 저장한다.
- 기본값은 코드 상수 또는 seed migration으로 제공하되, 운영 설정 변경 후에는 DB 값이 우선이다.
- `apiRequestsPerMinute`는 `1..10000` 범위로 검증한다.
- rate limit 저장은 이번 범위에 포함하지만 실제 요청 제한 적용은 별도 작업으로 남긴다.

## Test Plan
- `AdminSettingsServiceTest`
  - 기본 설정 조회
  - 저장된 설정 우선 조회
  - 알 수 없는 feature flag key 거부
  - rate limit 범위 검증
- `AdminSettingsControllerTest`
  - `GET /api/v1/admin/settings`
  - `PUT /api/v1/admin/settings`
  - validation error 400
- `AdminSecurityIntegrationTest` 보강
  - 미인증 401/403
  - 일반 사용자 403
  - admin 200
- 빌드 검증
  - `./gradlew.bat test --tests "*AdminSettings*"`
  - `./gradlew.bat clean build`

## Working Notes
- 현재 브랜치: `feature/PLAT-072-admin-settings`
- 기준 브랜치: `dev`
- PLAT-071 current 문서는 `docs/ai/archive/20260610-plat-071-completed/`에 보관했다.
- `TASK_platform.md`는 최초 개발 목록 문서이므로 수정하지 않았다.
- 검증 통과:
  - `./gradlew.bat test --tests "*AdminSettings*"`
  - `./gradlew.bat test --tests "*AdminSecurityIntegrationTest"`
  - `./gradlew.bat test --tests "*AdminSettings*" --tests "*PlatformModuleStructureTest"`
  - `./gradlew.bat clean build`
