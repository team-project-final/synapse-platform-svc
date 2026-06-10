# CONTEXT - PLAT-069: Tenant 초대 API

## 배경

루트 `docs/BACKEND_GAP_platform.md` A-5는 platform-svc에 tenant self-service API가 부족하다고 정리했다.

PLAT-068에서 다음 API는 구현 완료했다.

- `GET /api/v1/tenants/me`
- `PUT /api/v1/tenants/me`
- `GET /api/v1/tenants/me/members`
- `PUT /api/v1/tenants/me/members/{userId}`
- `DELETE /api/v1/tenants/me/members/{userId}`

다만 멤버 초대는 스키마, 토큰, 만료, 메일 발송 경계가 있어 PLAT-069로 분리했다.

이번 작업은 그 후속이다.

## 현재 코드 상태

### Tenant Self-Service

`TenantSelfServiceController`는 `/api/v1/tenants` base path를 사용한다.

현재 메서드:

- `getMe(Authentication)`
- `updateMe(Authentication, UpdateTenantRequest)`
- `listMembers(Authentication, page, size)`
- `updateMemberRole(Authentication, userId, UpdateTenantMemberRoleRequest)`
- `removeMember(Authentication, userId)`

`currentUserId(Authentication)`는 JWT authentication name을 UUID로 해석한다.

### TenantSelfServiceService

현재 책임:

- 로그인 사용자의 `defaultTenantId` 조회
- tenant 존재 여부 확인
- tenant membership 확인
- owner/admin 권한 확인
- tenant 이름/settings 변경
- 멤버 목록 조회
- 역할 변경
- 멤버 삭제

중요 정책:

- tenant id를 외부에서 받지 않는다.
- 항상 로그인 사용자의 기본 tenant 기준이다.
- owner/admin만 tenant 관리 변경 작업이 가능하다.
- role은 `owner`, `admin`, `member`, `viewer`를 사용한다.
- `owner` 승격/강등은 일반 API에서 제외한다.

PLAT-069는 이 context와 manager 권한 검증을 재사용하는 것이 맞다.

### Tenant/Member 데이터

기존 테이블:

- `tenants`
- `tenant_members`
- `users`

`tenant_members`는 실제 멤버십 테이블이다.
초대 생성 시점에는 아직 멤버가 아니므로 `tenant_members`에 insert하지 않는다.

### UserApi

초대 대상 email이 이미 가입된 사용자인지 확인하려면 기존 `UserApi.findByEmail(String email)`을 사용할 수 있다.

이미 가입된 사용자라면 해당 user id로 같은 tenant membership 존재 여부를 확인한다.
가입되지 않은 email도 초대는 가능해야 한다.

## 설계 방향

### API 기준

초대 생성 endpoint:

- `POST /api/v1/tenants/me/invitations`

tenant id는 받지 않는다.
PLAT-068과 동일하게 로그인 사용자의 기본 tenant를 기준으로 처리한다.

### 권한 기준

요청자는 tenant `owner` 또는 `admin`이어야 한다.

허용 초대 role:

- `admin`
- `member`
- `viewer`

불허:

- `owner`
- 빈 값
- 기타 임의 문자열

### Email 정책

저장 전 정규화:

- `trim`
- lower-case

검증:

- blank 불가
- email 형식은 Bean Validation 또는 service validation으로 처리
- 이미 같은 tenant의 active member이면 초대 불가

### 중복 초대 정책

같은 tenant/email에 만료되지 않은 `pending` 초대가 있으면 중복 생성하지 않는다.

구현 정책:

- 같은 tenant/email에 만료되지 않은 pending 초대가 있으면 409 conflict
- 같은 tenant/email의 pending 초대가 이미 만료됐으면 기존 초대를 `expired`로 바꾸고 새 초대를 생성
- DB에는 pending 중복 방지 partial unique index를 둔다.
- 동시 요청으로 사전 조회를 모두 통과해도 `saveAndFlush`의 unique constraint 충돌을 409로 변환한다.

### Token 정책

초대 수락은 이번 scope 밖이다.
그래도 수락 흐름을 염두에 두고 token hash는 저장한다.

원칙:

- token 원문은 DB에 저장하지 않는다.
- 응답에도 token 원문을 노출하지 않는다.
- 초대 메일 발송 연결이 생길 때만 원문 token을 링크 생성에 사용한다.

이번 작업에서는 초대 생성 API 응답을 프론트 확인용으로 제한한다.

### 메일 발송 경계

현재 platform에는 `notification.service.SesEmailService`와 `NotificationService`가 있다.

하지만 auth의 tenant 초대 service가 notification 내부 service를 직접 주입하면 모듈 경계가 흐려진다.

이번 작업에서는 다음 중 하나만 문서/설계로 남긴다.

- 초대 생성만 구현하고 발송은 후속 작업
- 추후 outbox/Kafka notification event로 연결
- notification 모듈에 공개 application boundary를 따로 만들고 그 계약을 통해 발송

직접 SES service 주입은 하지 않는다.

## 구현 위치

우선 후보:

- `com.synapse.platform.auth.entity.TenantInvitation`
- `com.synapse.platform.auth.repository.TenantInvitationRepository`
- `com.synapse.platform.auth.dto.request.CreateTenantInvitationRequest`
- `com.synapse.platform.auth.dto.response.TenantInvitationResponse`
- `com.synapse.platform.auth.service.TenantSelfServiceService`
- `com.synapse.platform.auth.controller.TenantSelfServiceController`

마이그레이션:

- `src/main/resources/db/migration/V20260610110000__create_tenant_invitations.sql`

## 구현 결과

- `tenant_invitations` 테이블 추가
- `TenantInvitation` entity 추가
- `TenantInvitationRepository` 추가
- `CreateTenantInvitationRequest` 추가
- `TenantInvitationResponse` 추가
- `TenantSelfServiceException` 초대 오류 코드 추가
- `POST /api/v1/tenants/me/invitations` 추가
- 초대 email 정규화 추가
- role 검증은 기존 `admin`, `member`, `viewer` 정책 재사용
- 같은 tenant의 기존 member email 초대 차단
- 활성 pending 초대 중복 409 처리
- 동시 pending 초대 insert 충돌 409 처리
- 만료된 pending 초대는 `expired` 처리 후 새 초대 생성
- token hash만 DB 저장, 응답에는 token 원문 미노출
- SES/notification 직접 결합 없음

## 테스트 포인트

- 인증 없이 초대 생성 시 401
- member/viewer가 초대 생성 시 403
- owner/admin이 초대 생성 가능
- role이 `admin`, `member`, `viewer`이면 성공
- role이 `owner`이면 실패
- email이 blank/invalid이면 실패
- email은 lower-case로 저장
- 이미 같은 tenant member인 email이면 실패
- 다른 tenant member인 email은 초대 가능
- 같은 tenant/email pending 초대 중복은 정책대로 실패 또는 기존 응답
- 만료된 초대가 있으면 새 초대 생성 가능
- token 원문은 DB에 저장하지 않음
- `PlatformModuleStructureTest` 통과

## 검증 결과

- `TenantSelfServiceServiceTest` 통과
- 리뷰 보강 후 `TenantSelfServiceServiceTest` 통과
- `TenantSelfServiceControllerTest` 통과
- `TenantSelfServiceSecurityIntegrationTest` 통과
- `TenantInvitationRepositoryTest` 통과
- `PlatformModuleStructureTest` 통과
- `clean build` 통과

## 주의 사항

- `TASK_platform.md`는 최초 개발 목록이므로 수정하지 않는다.
- env/profile은 수정하지 않는다.
- gitops/shared는 팀장님 관리 영역이므로 수정하지 않는다.
- admin tenant API의 기존 응답/동작은 변경하지 않는다.
- billing plan/status는 tenant invitation API에서 변경하지 않는다.
- 초대 수락 API는 이번 작업에 섞지 않는다.
- notification 내부 구현체 직접 주입은 피한다.
