# TASK - PLAT-068: Tenant 셀프관리 API

> 출처: 루트 `docs/BACKEND_GAP_platform.md` A-5. 프론트 `settings/tenant_settings_screen.dart`가 테넌트 정보, 멤버 목록, 초대, 역할 변경/삭제를 기대하지만 platform-svc는 현재 admin 전용 tenant API만 제공한다.

## Task Metadata

| 필드 | 내용 |
|---|---|
| Task ID | `PLAT-068` |
| Title | Tenant 셀프관리 API |
| Owner | platform (김해준) |
| Status | `DONE` |
| Priority | `P1` |
| Step Goal | 로그인 사용자가 본인 기본 tenant의 정보와 멤버를 platform API로 조회/관리한다. |
| Done When | 아래 `Done When` 체크리스트 기준 |
| Scope | 아래 `Scope` 기준 |
| Dependencies | `BACKEND_GAP_platform.md` A-5, `tenants`, `tenant_members`, `UserApi`, `TenantApi` |
| Due Date | 2026-06-12 |

## Step Goal

로그인 사용자가 본인 기본 tenant의 정보와 멤버를 platform API로 조회/관리한다.

## Done When

- [x] `GET /api/v1/tenants/me`가 로그인 사용자의 기본 tenant 정보를 반환한다.
- [x] `PUT /api/v1/tenants/me`가 tenant 이름과 설정 일부를 저장한다.
- [x] `GET /api/v1/tenants/me/members`가 현재 tenant 멤버 목록을 반환한다.
- [x] `PUT /api/v1/tenants/me/members/{userId}`가 OWNER/ADMIN 권한으로 멤버 역할을 변경한다.
- [x] `DELETE /api/v1/tenants/me/members/{userId}`가 OWNER/ADMIN 권한으로 멤버를 제거한다.
- [x] 자기 자신을 제거하거나 마지막 OWNER를 제거하는 요청은 400/409로 거절한다.
- [x] 초대 API는 스키마와 메일 발송 경계가 커서 PLAT-069 후속 작업으로 분리한다.
- [x] tenant 소유권/멤버십 검증 없이 타 tenant 데이터에 접근할 수 없다.
- [x] 삭제된 user는 멤버 목록 응답에서 제외한다.
- [x] 멤버 목록 기본 정렬을 `joinedAt ASC, userId ASC`로 고정한다.
- [x] 큰 page offset 요청은 overflow 없이 빈 목록으로 처리한다.
- [x] `UserApi.findSummariesByIds(...)`는 default 구현 없이 명시 계약으로 유지한다.
- [x] `TASK_platform.md`, env/profile, gitops/shared 프로젝트는 수정하지 않는다.
- [x] controller/service/repository/security 테스트가 통과한다.
- [x] `PlatformModuleStructureTest`와 `clean build`가 통과한다.

## Scope

### In Scope

- `GET /api/v1/tenants/me`
- `PUT /api/v1/tenants/me`
- `GET /api/v1/tenants/me/members`
- `PUT /api/v1/tenants/me/members/{userId}`
- `DELETE /api/v1/tenants/me/members/{userId}`
- tenant member role 정책 정리: `owner`, `admin`, `member`, `viewer`
- 기본 tenant 기준 접근 제어
- 멤버 목록 응답에 사용자 표시 정보 포함
- tenant/member controller, service, DTO, repository query 추가
- controller/service/repository/security 테스트 추가

### Conditional Scope

- `POST /api/v1/tenants/me/invitations`
- 초대 토큰 저장 테이블 추가
- 초대 메일 발송 경계 결정

조건:

- 초대 토큰 저장/만료/재전송 정책을 이번 작업에서 확정할 수 있으면 포함한다.
- 메일 발송까지 넣으면 notification/SES 경계가 커지므로, 범위가 커지면 PLAT-069로 분리한다.

### Out of Scope

- admin tenant API 변경
- billing plan 변경
- tenant switch API
- SSO/enterprise 조직 관리
- 타 서비스 group/community 관리
- 프론트 화면 수정
- `TASK_platform.md` 수정
- env/profile/gitops/shared 수정

## API Contract

### 내 tenant 조회

`GET /api/v1/tenants/me`

응답 후보:

```json
{
  "id": "75c0cf72-dc31-4d58-bf6b-77e0b45e9dd5",
  "name": "Synapse 팀",
  "slug": "synapse-team",
  "plan": "team",
  "status": "active",
  "tenantType": "personal",
  "region": "ap-northeast-2",
  "settings": {},
  "myRole": "owner",
  "createdAt": "2026-06-10T09:00:00+09:00",
  "updatedAt": "2026-06-10T09:00:00+09:00"
}
```

### 내 tenant 저장

`PUT /api/v1/tenants/me`

요청 후보:

```json
{
  "name": "Synapse 팀",
  "settings": {
    "timezone": "Asia/Seoul"
  }
}
```

정책:

- `name`은 OWNER/ADMIN만 변경 가능
- `slug`, `plan`, `status`, `tenantType`, `region`은 이번 API에서 변경하지 않는다.
- `settings`는 object만 허용하고 기존 값과 merge한다.

### 멤버 목록

`GET /api/v1/tenants/me/members?page=0&size=20`

응답 후보:

```json
{
  "items": [
    {
      "userId": "03cfd9b7-3a8f-45b5-a291-4cbf478da3e6",
      "email": "admin@example.com",
      "displayName": "김시냅스",
      "role": "owner",
      "joinedAt": "2026-06-10T09:00:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 역할 변경

`PUT /api/v1/tenants/me/members/{userId}`

요청 후보:

```json
{
  "role": "member"
}
```

정책:

- 요청자는 `owner` 또는 `admin`이어야 한다.
- 허용 role: `admin`, `member`, `viewer`
- `owner` 승격/강등은 이번 작업에서 제외한다.
- 본인 역할 변경은 거절한다.

### 멤버 삭제

`DELETE /api/v1/tenants/me/members/{userId}`

정책:

- 요청자는 `owner` 또는 `admin`이어야 한다.
- 본인 삭제는 거절한다.
- 마지막 owner 삭제는 거절한다.

### 초대 요청

`POST /api/v1/tenants/me/invitations`

요청 후보:

```json
{
  "email": "user@example.com",
  "role": "member"
}
```

이번 작업 포함 여부:

- PLAT-068에서는 tenant 정보/멤버 관리까지만 완료한다.
- 초대 토큰 저장소, 수락 흐름, 메일 발송 경계는 PLAT-069로 분리한다.

## Data Model

기존 테이블:

- `tenants`
- `tenant_members`
- `users`

기존 제약:

- `tenant_members` PK: `(tenant_id, user_id)`
- `role`은 문자열이며 DB check constraint는 없다.
- 현재 신규 가입자는 `owner` role로 생성된다.

추가 가능 후보:

- `tenant_invitations`
  - `id UUID`
  - `tenant_id UUID`
  - `email VARCHAR(255)`
  - `role VARCHAR(20)`
  - `token_hash VARCHAR(255)`
  - `invited_by UUID`
  - `expires_at TIMESTAMPTZ`
  - `accepted_at TIMESTAMPTZ`
  - `created_at TIMESTAMPTZ`

## Design Notes

- API 위치는 `/api/v1/tenants/**`로 둔다.
- 구현 위치는 tenant 소유 데이터가 있는 `auth` 모듈을 우선 후보로 한다.
- billing의 `AdminTenantController`는 admin 전용으로 유지한다.
- 현재 사용자 tenant는 `UserApi.findById(userId).defaultTenantId()`로 찾는다.
- tenant 정보와 member query는 auth 모듈 내부 repository를 사용한다.
- 멤버 목록에 필요한 user email/displayName은 `UserApi` 공개 계약을 확장해 가져온다.
- 다른 tenant ID를 path/body로 받지 않고, 항상 로그인 사용자의 기본 tenant 기준으로 처리한다.
- 삭제된 user 또는 삭제된 tenant는 응답에서 제외한다.

## Implementation Checklist

- [x] 기존 PLAT-067 current 문서 archive 완료
- [x] PLAT-068 작업 브랜치 생성
- [x] PLAT-068 작업문서 작성
- [x] `Tenant` entity 업데이트 메서드/getter 보강
- [x] `TenantMember` role 변경 메서드/getter 보강
- [x] `TenantMemberRepository` tenant scoped query 추가
- [x] `UserApi` member summary 조회 계약 추가
- [x] tenant self-service DTO 추가
- [x] tenant self-service service 추가
- [x] tenant self-service controller 추가
- [x] 권한 정책 테스트 추가
- [x] PostgreSQL repository 테스트 추가
- [x] security integration 테스트 추가
- [x] targeted test 통과
- [x] `PlatformModuleStructureTest` 통과
- [x] `clean build` 통과

## Verification Plan

```powershell
.\gradlew.bat test --tests "*TenantSelfServiceControllerTest"
.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"
.\gradlew.bat test --tests "*TenantMemberRepositoryTest"
.\gradlew.bat test --tests "*TenantSelfServiceSecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

## Verification Result

- `.\gradlew.bat test --tests "*UserServiceTest" --tests "*TenantSelfServiceControllerTest" --tests "*TenantSelfServiceServiceTest" --tests "*TenantMemberRepositoryTest" --tests "*TenantSelfServiceSecurityIntegrationTest" --tests "*PlatformModuleStructureTest"` 통과
- `.\gradlew.bat test --tests "*TenantSelfServiceServiceTest" --tests "*TenantSelfServiceControllerTest" --tests "*TenantMemberRepositoryTest" --tests "*UserServiceTest" --tests "*CustomOAuth2UserServiceTest" --tests "*OAuthUserResolverTest"` 통과
- `.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"` 통과
- `.\gradlew.bat spotbugsMain` 통과
- `.\gradlew.bat clean build` 통과
