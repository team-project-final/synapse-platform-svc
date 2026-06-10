# TASK - PLAT-069: Tenant 초대 API

> 출처: 루트 `docs/BACKEND_GAP_platform.md` A-5 및 PLAT-068 후속 분리 항목. 프론트 `settings/tenant_settings_screen.dart`는 멤버 초대를 기대하지만, PLAT-068에서는 tenant 정보/멤버 목록/역할 변경/삭제까지만 구현하고 초대 API는 별도 작업으로 분리했다.

## Task Metadata

| 필드 | 내용 |
|---|---|
| Task ID | `PLAT-069` |
| Title | Tenant 초대 API |
| Owner | platform (김해준) |
| Status | `DONE` |
| Priority | `P1` |
| Step Goal | OWNER/ADMIN 사용자가 본인 기본 tenant에 이메일 기반 멤버 초대를 생성하고, 프론트가 초대 생성 결과를 확인할 수 있게 한다. |
| Done When | 아래 `Done When` 체크리스트 기준 |
| Scope | 아래 `Scope` 기준 |
| Dependencies | `BACKEND_GAP_platform.md` A-5, PLAT-068, `tenants`, `tenant_members`, `users`, `UserApi` |
| Due Date | 2026-06-12 |

## Step Goal

로그인 사용자가 본인 기본 tenant의 관리자 권한으로 이메일 초대를 생성한다.

이번 작업의 1차 목표는 "프론트에서 초대 버튼을 실제 API에 연결할 수 있는 상태"를 만드는 것이다. 메일 발송은 notification/SES 경계가 있으므로 직접 결합하지 않고, 초대 레코드와 응답 계약을 먼저 고정한다.

## Done When

- [x] 기존 PLAT-068 current 문서를 archive한다.
- [x] PLAT-069 작업 브랜치를 `dev`에서 생성한다.
- [x] PLAT-069 작업문서를 작성한다.
- [x] `tenant_invitations` 마이그레이션을 추가한다.
- [x] `TenantInvitation` entity와 repository를 추가한다.
- [x] `POST /api/v1/tenants/me/invitations`가 OWNER/ADMIN 권한으로 초대를 생성한다.
- [x] 초대 대상 email은 정규화해서 저장한다.
- [x] 초대 role은 `admin`, `member`, `viewer`만 허용한다.
- [x] 이미 같은 tenant의 멤버인 사용자는 초대할 수 없다.
- [x] 같은 tenant/email에 활성 초대가 있으면 409로 거절한다.
- [x] 같은 tenant/email 동시 초대 생성 시 DB unique 충돌을 409로 변환한다.
- [x] 초대 토큰은 원문을 저장하지 않고 hash만 저장한다.
- [x] 응답에는 프론트가 표시할 수 있는 초대 id/email/role/status/expiresAt을 반환한다.
- [x] 메일 발송은 이번 작업에서 직접 SES service를 주입하지 않는다.
- [x] `TASK_platform.md`, env/profile, gitops/shared 프로젝트는 수정하지 않는다.
- [x] controller/service/repository/security 테스트가 통과한다.
- [x] `PlatformModuleStructureTest`와 `clean build`가 통과한다.

## Scope

### In Scope

- `POST /api/v1/tenants/me/invitations`
- tenant 초대 저장 테이블 추가
- tenant 초대 entity/repository 추가
- 초대 생성 request/response DTO 추가
- PLAT-068 `TenantSelfServiceController`에 초대 endpoint 추가
- PLAT-068 `TenantSelfServiceService`의 tenant context/manager 권한 정책 재사용
- 이메일 정규화
- role 검증
- 이미 멤버인 email 차단
- 활성 초대 중복 처리
- token 생성 및 hash 저장
- controller/service/repository/security 테스트 추가

### Conditional Scope

- `GET /api/v1/tenants/me/invitations`
- `DELETE /api/v1/tenants/me/invitations/{invitationId}`
- 초대 재전송 API

조건:

- 프론트 초대 목록 표시가 바로 필요하면 목록 조회까지 포함한다.
- 초대 취소/재전송은 저장 모델이 안정화된 뒤 후속 작업으로 분리해도 된다.

### Out of Scope

- 초대 수락 API
- 가입/로그인 시 초대 자동 수락 흐름
- SES 직접 발송 구현
- notification 내부 service 직접 주입
- admin tenant API 변경
- billing plan 변경
- tenant switch API
- 프론트 화면 수정
- `TASK_platform.md` 수정
- env/profile/gitops/shared 수정

## API Contract

### 초대 생성

`POST /api/v1/tenants/me/invitations`

요청 후보:

```json
{
  "email": "new-user@example.com",
  "role": "member"
}
```

응답 후보:

```json
{
  "id": "f00ac14e-e1d3-43b6-b7de-6cc79801bfa9",
  "email": "new-user@example.com",
  "role": "member",
  "status": "pending",
  "expiresAt": "2026-06-17T09:00:00+09:00",
  "createdAt": "2026-06-10T09:00:00+09:00"
}
```

정책:

- 인증 필요
- 요청자는 tenant `owner` 또는 `admin`
- tenant는 로그인 사용자의 `defaultTenantId` 기준
- path/body로 tenant id를 받지 않는다.
- 허용 role: `admin`, `member`, `viewer`
- `owner` 초대는 허용하지 않는다.
- email은 trim + lower-case 정규화
- 이미 같은 tenant의 member인 email은 409
- 같은 tenant/email에 만료되지 않은 pending 초대가 있으면 409
- 같은 tenant/email의 pending 초대가 이미 만료됐으면 기존 초대를 `expired`로 바꾸고 새 초대를 생성
- 같은 tenant/email 동시 요청으로 DB pending unique index가 충돌하면 409
- token 원문은 응답/DB에 저장하지 않는다.

## Data Model

추가 후보:

- `tenant_invitations`
  - `id UUID PRIMARY KEY`
  - `tenant_id UUID NOT NULL`
  - `email VARCHAR(255) NOT NULL`
  - `role VARCHAR(20) NOT NULL`
  - `token_hash VARCHAR(64) NOT NULL`
  - `status VARCHAR(20) NOT NULL`
  - `invited_by UUID NOT NULL`
  - `expires_at TIMESTAMPTZ NOT NULL`
  - `accepted_at TIMESTAMPTZ`
  - `created_at TIMESTAMPTZ NOT NULL`
  - `updated_at TIMESTAMPTZ NOT NULL`

인덱스/제약 후보:

- `idx_tenant_invitations_tenant_status` on `(tenant_id, status)`
- `idx_tenant_invitations_email` on `(email)`
- pending 중복 방지용 partial unique index:
  - `(tenant_id, email)` where `status = 'pending'`

마이그레이션 이름 후보:

- `V20260610110000__create_tenant_invitations.sql`

## Design Notes

- API 위치는 기존 PLAT-068과 동일하게 `/api/v1/tenants/**`로 둔다.
- 구현 위치는 tenant/tenant_members 소유권이 있는 `auth` 모듈로 둔다.
- `TenantSelfServiceController`에 endpoint를 추가하고, service 정책은 기존 tenant context와 manager role 검증을 재사용한다.
- 초대 생성은 tenant membership insert가 아니다. 초대 수락 전까지 `tenant_members`에 추가하지 않는다.
- 메일 발송은 이번 작업에서 직접 결합하지 않는다. 추후 outbox/Kafka notification event 또는 별도 application boundary로 연결한다.
- 초대 토큰 원문은 생성 시점에만 사용 가능해야 하며, DB에는 hash만 저장한다.
- 프론트가 당장 필요한 것은 초대 생성 성공/실패와 pending 상태 표시다.

## Implementation Checklist

- [x] 기존 PLAT-068 current 문서 archive 완료
- [x] PLAT-069 작업 브랜치 생성
- [x] PLAT-069 작업문서 작성
- [x] migration 추가
- [x] `TenantInvitation` entity 추가
- [x] `TenantInvitationRepository` 추가
- [x] request/response DTO 추가
- [x] `TenantSelfServiceException` 초대 관련 오류 추가
- [x] `TenantSelfServiceService.createInvitation(...)` 추가
- [x] `TenantSelfServiceController` 초대 생성 endpoint 추가
- [x] 이미 멤버인 email 검증 추가
- [x] 활성 초대 중복 정책 구현
- [x] 동시 pending insert 충돌 409 변환 구현
- [x] token hash 처리 구현
- [x] controller test 추가
- [x] service test 추가
- [x] repository integration test 추가
- [x] security integration test 추가
- [x] `PlatformModuleStructureTest` 통과
- [x] `clean build` 통과

## Verification Plan

```powershell
.\gradlew.bat test --tests "*TenantSelfServiceControllerTest"
.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"
.\gradlew.bat test --tests "*TenantInvitationRepositoryTest"
.\gradlew.bat test --tests "*TenantSelfServiceSecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

## Verification Result

- `.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"` 통과
- 리뷰 보강 후 `.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"` 통과
- `.\gradlew.bat test --tests "*TenantSelfServiceControllerTest" --tests "*TenantSelfServiceSecurityIntegrationTest" --tests "*TenantInvitationRepositoryTest"` 통과
- `.\gradlew.bat test --tests "*PlatformModuleStructureTest"` 통과
- `.\gradlew.bat clean build` 통과
