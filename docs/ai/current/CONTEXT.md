# PLAT-072 Context

## Current State
- `dev`는 PR #85 merge 이후 최신 상태다.
- PLAT-071 Admin analytics summary API는 merge 완료됐다.
- 현재 작업 브랜치: `feature/PLAT-072-admin-settings`
- PLAT-072 Admin settings API 구현과 검증이 완료됐다.

## Requirement Source
Root 문서 `C:\workspace\team_project_2\docs\BACKEND_GAP_platform.md` A-6에는 Admin 대시보드 보강 항목이 남아 있다.

| 기대 동작 | 제안 엔드포인트 | 상태 |
|---|---|---|
| DAU/MAU, 시스템 사용량, 최근 활동 | `GET /api/v1/admin/analytics/*` | PLAT-071 완료 |
| 시스템 설정/피처 플래그 | `GET`, `PUT /api/v1/admin/settings` | 이번 작업 |
| GDPR 데이터 요청 처리 | `GET`, `POST /api/v1/admin/data-requests` | 다음 후보 |

## Frontend Context
프론트의 Admin System Settings 화면은 아직 mock 기반이다.

파일:
- `C:\workspace\team_project_2\synapse-frontend\lib\services\platform\features\admin\presentation\screens\admin_screens\admin_system_settings_screen.dart`

화면 구조:
- Plan Quota
- Feature Flags
- Rate Limit

프론트 mock 피처 플래그:
- `AI 카드 자동 생성`
- `소셜 로그인 (Google)`
- `소셜 로그인 (GitHub)`
- `실시간 협업 편집`
- `베타: 음성 복습`

프론트 mock rate limit:
- `100`

## Backend Context
Admin 관련 기존 코드:
- `src/main/java/com/synapse/platform/admin/controller/AdminAnalyticsController.java`
- `src/main/java/com/synapse/platform/admin/service/AdminAnalyticsService.java`
- `src/main/java/com/synapse/platform/admin/dto/AdminAnalyticsSummaryResponse.java`
- `src/main/java/com/synapse/platform/user/controller/AdminUserController.java`
- `src/main/java/com/synapse/platform/billing/controller/AdminTenantController.java`
- `src/main/java/com/synapse/platform/audit/controller/AuditLogController.java`

Admin 보안:
- `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- `/api/v1/admin/**`는 `ROLE_ADMIN` 필요
- Admin 컨트롤러들은 `@PreAuthorize("hasRole('ADMIN')")` 패턴 사용

Plan quota 관련 기존 코드:
- `src/main/java/com/synapse/platform/auth/entity/PlanQuota.java`
- `src/main/java/com/synapse/platform/auth/repository/PlanQuotaRepository.java`
- `src/main/java/com/synapse/platform/auth/api/TenantApi.java`
- `src/main/java/com/synapse/platform/auth/api/PlanQuotaInfo.java`
- `src/main/resources/db/migration/V2__init_tenants_and_plans.sql`
- `src/main/resources/db/migration/V18__seed_plan_quotas.sql`

## Design Decision
이번 작업의 핵심 결정은 다음과 같다.

- Plan quota는 조회 전용이다.
- 피처 플래그와 rate limit만 `PUT /api/v1/admin/settings`로 저장한다.
- 피처 플래그 key는 영문 stable key로 저장한다.
- 화면 표시용 label은 응답에 포함할 수 있지만 저장 기준은 key다.
- Admin 모듈은 auth 내부 repository/entity가 아니라 `auth::tenant-api`의 `TenantApi.listPlanQuotas()`만 사용한다.
- 실제 기능 on/off 적용과 실제 rate limit enforcement는 별도 작업이다.
- 환경 변수, profile, 포트 설정은 건드리지 않는다.

## Proposed Backend Shape
패키지는 기존 Admin analytics 작업과 같은 `com.synapse.platform.admin` 하위를 우선 사용한다.

구현 파일:
- `admin/controller/AdminSettingsController.java`
- `admin/service/AdminSettingsService.java`
- `admin/dto/AdminSettingsResponse.java`
- `admin/dto/AdminSettingsUpdateRequest.java`
- `admin/entity/AdminSetting.java`
- `admin/repository/AdminSettingRepository.java`
- `db/migration/V20260610150000__create_admin_settings.sql`
- `auth/api/TenantApi.java`의 `listPlanQuotas()`
- `auth/service/TenantService.java`의 `listPlanQuotas()` 구현

DTO는 record 기반으로 구성했고, SpotBugs 경고 방지를 위해 list 필드는 방어 복사한다.

## API Draft
### GET `/api/v1/admin/settings`
Admin 설정 화면 초기 로딩용 API.

응답 포함 항목:
- `planQuotas`
- `featureFlags`
- `rateLimit`
- `updatedAt`

### PUT `/api/v1/admin/settings`
피처 플래그와 rate limit 저장 API.

요청 포함 항목:
- `featureFlags`
- `rateLimit.apiRequestsPerMinute`

응답:
- 저장 후 최신 `AdminSettingsResponse`

## Risk Notes
- `plan_quotas` 값을 수정 가능하게 열면 billing/auth 계약과 직접 충돌할 수 있어 이번 범위에서는 제외한다.
- Feature flag key를 프론트 표시 문구로 저장하면 한글 문구 변경 때 데이터 호환성이 깨질 수 있다.
- Rate limit 값을 저장만 하고 적용하지 않으면 운영자가 오해할 수 있다. 응답 필드 또는 문서에서 "설정 저장 API" 범위임을 명확히 해야 한다.
- `V18__seed_plan_quotas.sql`는 빈 유지 파일로 보이며, plan quota seed는 V2에서 처리된 상태다.

## Verification
통과한 검증:

```powershell
.\gradlew.bat test --tests "*AdminSettings*"
.\gradlew.bat test --tests "*AdminSecurityIntegrationTest"
.\gradlew.bat test --tests "*AdminSettings*" --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

전체 빌드에서 checkstyle, 전체 테스트, jacoco coverage verification, spotbugs가 통과했다.

## Do Not Touch
- `TASK_platform.md`
- `.env`
- Spring profile 설정
- 실행 포트 설정
- gitops/shared 프로젝트
- frontend 프로젝트
