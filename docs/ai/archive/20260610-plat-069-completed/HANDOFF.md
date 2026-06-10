# HANDOFF - PLAT-069: Tenant 초대 API

## 한줄 요약

PLAT-068에서 분리한 tenant member 초대 API를 구현할 차례다. 우선 `POST /api/v1/tenants/me/invitations`와 `tenant_invitations` 저장 모델을 만든다.

## 현재 상태

- 작업 브랜치 생성 완료
- PLAT-068 current 문서 archive 완료
- PLAT-069 작업문서 작성 완료
- 초대 생성 API 구현 완료
- 동시 pending 초대 insert 충돌 409 변환 완료
- targeted test 통과
- `clean build` 통과

## 작업 위치

- Repo: `synapse-platform-svc`
- Branch: `feature/PLAT-069-tenant-invitations`
- Base: `dev`
- 작업문서: `docs/ai/current`
- 이전 current archive: `docs/ai/archive/20260610-plat-068-completed`

## 구현 목표

1. 초대 저장 모델 추가
   - `tenant_invitations`
   - pending/accepted/expired/cancelled 상태 관리
   - email, role, token hash, invitedBy, expiresAt 저장

2. 초대 생성 API 추가
   - `POST /api/v1/tenants/me/invitations`
   - 로그인 사용자의 기본 tenant 기준
   - owner/admin만 허용
   - role은 `admin`, `member`, `viewer`만 허용

3. 중복/멤버 검증 추가
   - 이미 같은 tenant 멤버인 email 차단
   - 같은 tenant/email에 활성 pending 초대가 있으면 409로 중복 차단
   - 같은 tenant/email 동시 생성 시 DB unique 충돌을 409로 변환
   - 만료된 초대가 있으면 새 초대 가능

4. 메일 발송 경계 유지
   - 이번 작업에서는 SES 직접 발송을 구현하지 않는다.
   - auth service에서 notification 내부 service를 직접 주입하지 않는다.
   - 후속으로 outbox/Kafka notification event 또는 공개 notification boundary를 검토한다.

## 예상 파일

- `src/main/resources/db/migration/V20260610110000__create_tenant_invitations.sql`
- `src/main/java/com/synapse/platform/auth/entity/TenantInvitation.java`
- `src/main/java/com/synapse/platform/auth/repository/TenantInvitationRepository.java`
- `src/main/java/com/synapse/platform/auth/dto/request/CreateTenantInvitationRequest.java`
- `src/main/java/com/synapse/platform/auth/dto/response/TenantInvitationResponse.java`
- `src/main/java/com/synapse/platform/auth/service/TenantSelfServiceException.java`
- `src/main/java/com/synapse/platform/auth/service/TenantSelfServiceService.java`
- `src/main/java/com/synapse/platform/auth/controller/TenantSelfServiceController.java`

테스트 후보:

- `src/test/java/com/synapse/platform/auth/controller/TenantSelfServiceControllerTest.java`
- `src/test/java/com/synapse/platform/auth/service/TenantSelfServiceServiceTest.java`
- `src/test/java/com/synapse/platform/auth/repository/TenantInvitationRepositoryTest.java`
- `src/test/java/com/synapse/platform/auth/controller/TenantSelfServiceSecurityIntegrationTest.java`

## API 후보

요청:

```http
POST /api/v1/tenants/me/invitations
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "email": "new-user@example.com",
  "role": "member"
}
```

응답:

```json
{
  "id": "uuid",
  "email": "new-user@example.com",
  "role": "member",
  "status": "pending",
  "expiresAt": "2026-06-17T09:00:00+09:00",
  "createdAt": "2026-06-10T09:00:00+09:00"
}
```

## 검증 명령

```powershell
.\gradlew.bat test --tests "*TenantSelfServiceControllerTest"
.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"
.\gradlew.bat test --tests "*TenantInvitationRepositoryTest"
.\gradlew.bat test --tests "*TenantSelfServiceSecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

현재 통과:

- `.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"`
- 리뷰 보강 후 `.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"`
- `.\gradlew.bat test --tests "*TenantSelfServiceControllerTest" --tests "*TenantSelfServiceSecurityIntegrationTest" --tests "*TenantInvitationRepositoryTest"`
- `.\gradlew.bat test --tests "*PlatformModuleStructureTest"`
- `.\gradlew.bat clean build`

## 금지/주의

- `TASK_platform.md` 수정 금지
- env/profile 수정 금지
- gitops/shared 수정 금지
- admin tenant API 회귀 금지
- billing plan/status 변경 금지
- 초대 수락 API를 이번 작업에 섞지 말 것
- notification 내부 service를 auth에서 직접 주입하지 말 것
- PR 본문 작성 시 UTF-8 body file 사용
