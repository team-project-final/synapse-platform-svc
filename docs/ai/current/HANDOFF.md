# PLAT-072 Handoff

## Status
PLAT-072 구현 및 검증 완료. PR 준비 가능 상태다.

## Branch
- Base: `dev`
- Working branch: `feature/PLAT-072-admin-settings`

## Archived
PLAT-071 current 문서는 아래 경로에 보관했다.

- `docs/ai/archive/20260610-plat-071-completed/TASK.md`
- `docs/ai/archive/20260610-plat-071-completed/CONTEXT.md`
- `docs/ai/archive/20260610-plat-071-completed/HANDOFF.md`

## Current Task
Admin System Settings 화면 연결용 backend API를 구현했다.

대상 endpoint:
- `GET /api/v1/admin/settings`
- `PUT /api/v1/admin/settings`

## Implementation Direction
- 기존 `com.synapse.platform.admin` 패키지에 settings controller/service/dto/entity/repository를 추가했다.
- `/api/v1/admin/**` 기존 보안 정책과 `@PreAuthorize("hasRole('ADMIN')")` 패턴을 따랐다.
- Plan quota는 `auth::tenant-api`의 `TenantApi.listPlanQuotas()`로 조회한다.
- Plan quota 수정은 하지 않는다.
- Feature flag와 rate limit 저장용 `admin_settings` 테이블을 추가했다.
- Feature flag 저장 key는 영문 stable key를 사용한다.
- `.env`, profile, 포트 설정은 수정하지 않았다.

## Implemented
- `GET /api/v1/admin/settings`
- `PUT /api/v1/admin/settings`
- `admin_settings` migration
- `TenantApi.listPlanQuotas()` 공개 계약
- service/controller/security 테스트 보강
- Modulith 구조 테스트 대응
- SpotBugs DTO 방어 복사 및 DI false-positive exclude 보강

## Remaining Steps
1. 작업 내용 리뷰.
2. Git 규칙 확인 후 PR을 `dev`로 생성.

## Test Commands
```powershell
.\gradlew.bat test --tests "*AdminSettings*"
.\gradlew.bat test --tests "*AdminSecurityIntegrationTest"
.\gradlew.bat test --tests "*AdminSettings*" --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

## Out of Scope Reminder
- GDPR data request API는 PLAT-073 후보로 분리한다.
- 실제 rate limit enforcement는 이번 작업이 아니다.
- feature flag를 실제 기능 분기점에 연결하는 작업은 이번 작업이 아니다.
- frontend mock 제거는 frontend 담당 작업이다.
