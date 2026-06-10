# CONTEXT - PLAT-068: Tenant 셀프관리 API

## 배경

루트 `docs/BACKEND_GAP_platform.md` A-5는 platform-svc에 tenant self-service API가 없다고 정리한다.

프론트 `settings/tenant_settings_screen.dart`는 현재 다음 항목을 mock/TODO로 유지한다.

- 테넌트 정보 조회
- 테넌트 이름 저장
- 멤버 목록 조회
- 멤버 초대
- 멤버 역할 변경
- 멤버 삭제

현재 백엔드는 admin 전용 tenant API만 제공한다.

- `GET /api/v1/admin/tenants`
- `PUT /api/v1/admin/tenants/{id}/status`

즉, 일반 로그인 사용자가 본인 tenant를 관리하는 API가 없다.

## 구현 전 코드 상태

### Tenant

`Tenant` entity는 `auth` 모듈에 있다.

주요 필드:

- `id`
- `name`
- `slug`
- `plan`
- `status`
- `tenantType`
- `region`
- `settings`
- `createdAt`
- `updatedAt`
- `deletedAt`

기존 공개 메서드는 조회와 plan/status 변경 중심이었다.
PLAT-068에서 tenant 이름/settings 업데이트용 메서드와 필요한 getter를 보강했다.

### TenantMember

`TenantMember` entity는 `auth` 모듈에 있다.

주요 필드:

- `tenantId`
- `userId`
- `role`
- `joinedAt`

신규 tenant 생성 시 `TenantMember.ofOwner(tenantId, userId)`로 owner를 만든다.
PLAT-068에서 role 변경 메서드와 joinedAt getter를 보강했다.

### Repository

`TenantRepository`:

- `existsBySlug`
- `findAllByDeletedAtIsNull`
- `findByIdAndDeletedAtIsNull`

`TenantMemberRepository`에는 PLAT-068에서 다음 query를 추가했다.

- tenant 멤버 page 조회
- 특정 tenant/user membership 조회
- 특정 tenant owner count

### User 정보

멤버 목록 응답에는 `email`, `displayName`이 필요하다.
이 정보는 user 모듈 소유이므로 auth module에서 user repository를 직접 참조하지 않는다.

PLAT-068에서 `UserSummary`와 `findSummariesByIds(Collection<UUID>)` 계약을 추가했다.
auth 모듈은 user repository를 직접 참조하지 않고 `UserApi`를 통해 표시 정보를 조회한다.

## 설계 방향

### API 기준

API는 tenant id를 path로 받지 않는다.
항상 로그인 사용자의 기본 tenant 기준이다.

이유:

- 프론트 settings 화면은 "내 워크스페이스" 관리 화면이다.
- 타 tenant ID를 입력받으면 권한 체크 누락 위험이 커진다.
- user의 `defaultTenantId`가 이미 존재한다.

### 권한 기준

읽기:

- tenant 멤버이면 가능

수정:

- tenant `owner`, `admin`만 가능

삭제/역할 변경:

- tenant `owner`, `admin`만 가능
- 자기 자신 변경/삭제는 거절
- 마지막 owner 삭제/강등은 거절

역할:

- `owner`: tenant 최초 생성자. 이번 작업에서 승격/강등 제외
- `admin`: tenant 관리 가능
- `member`: 일반 멤버
- `viewer`: 읽기 전용

프론트 표시값:

- 관리자: `admin`
- 멤버: `member`
- 뷰어: `viewer`
- 소유자: `owner`

## 구현 위치

우선 후보:

- `com.synapse.platform.auth.controller.TenantSelfServiceController`
- `com.synapse.platform.auth.service.TenantSelfServiceService`

근거:

- tenant/tenant_members 테이블과 repository가 auth 모듈 소유다.
- billing 모듈의 `AdminTenantController`는 admin dashboard용 기존 API다.
- 일반 tenant self-service를 billing에 두면 billing이 auth 내부 tenant repository를 직접 다루게 되어 경계가 어색해진다.

## 초대 API 경계

멤버 초대는 단순 member insert가 아니다.

필요한 정책:

- 초대 토큰 생성/해시 저장
- 만료 시간
- 같은 email 재초대 처리
- 이미 멤버인 email 처리
- 초대 수락 API 또는 가입/로그인 시 초대 수락 흐름
- 초대 메일 발송 경계

PLAT-068에서는 tenant 정보/멤버 조회/역할 변경/삭제만 구현했다.
초대는 `tenant_invitations`, 수락 흐름, 메일 발송 경계가 커서 PLAT-069로 분리한다.

## 테스트 포인트

- 인증 없이 `/api/v1/tenants/me` 접근 시 401
- tenant member가 본인 tenant 정보를 조회할 수 있다.
- tenant member가 본인 tenant 멤버 목록을 조회할 수 있다.
- member/viewer는 tenant 이름 수정, 역할 변경, 삭제를 할 수 없다.
- owner/admin은 tenant 이름 수정, 역할 변경, 삭제를 할 수 있다.
- 자기 자신 삭제는 실패한다.
- 마지막 owner 삭제는 실패한다.
- 타 tenant member는 응답에 포함되지 않는다.
- 삭제된 tenant/user는 응답에서 제외한다.
- `PlatformModuleStructureTest`가 통과한다.

## 구현 결과

- `TenantSelfServiceController` 추가
- `TenantSelfServiceService` 추가
- `TenantSelfServiceException` 추가
- tenant self-service request/response DTO 추가
- `Tenant` 업데이트 메서드/getter 보강
- `TenantMember` role 변경 메서드/getter 보강
- `TenantMemberRepository` tenant scoped query 추가
- `UserApi`/`UserService` member summary 조회 계약 추가
- 삭제된 user처럼 summary가 없는 tenant member는 멤버 목록 응답에서 제외
- 멤버 목록 기본 정렬을 `joinedAt ASC, userId ASC`로 고정
- 큰 page offset 요청은 overflow 없이 빈 목록으로 처리
- `UserApi.findSummariesByIds(...)`는 default 없이 명시 구현하도록 유지

## 검증 결과

- `TenantSelfServiceControllerTest` 통과
- `TenantSelfServiceServiceTest` 통과
- `TenantMemberRepositoryTest` 통과
- `TenantSelfServiceSecurityIntegrationTest` 통과
- `UserServiceTest` 통과
- `CustomOAuth2UserServiceTest` 통과
- `OAuthUserResolverTest` 통과
- `PlatformModuleStructureTest` 통과
- `spotbugsMain` 통과
- `clean build` 통과

## 주의 사항

- `TASK_platform.md`는 최초 개발 목록이므로 수정하지 않는다.
- env/profile은 수정하지 않는다.
- gitops/shared는 팀장님 관리 영역이므로 수정하지 않는다.
- admin tenant API의 기존 응답/동작은 변경하지 않는다.
- billing plan/status는 tenant self-service에서 변경하지 않는다.
