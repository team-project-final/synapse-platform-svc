# HANDOFF - PLAT-068: Tenant 셀프관리 API

## 한줄 요약

루트 `docs/BACKEND_GAP_platform.md` A-5를 처리했다. 프론트 tenant settings 화면이 사용할 내 tenant 조회/저장, 멤버 목록, 멤버 역할 변경/삭제 API를 추가했다.

## 현재 상태

- 구현 완료
- 검증 완료
- 1차 범위: tenant 정보/멤버 목록/역할 변경/삭제
- 초대 API는 스키마와 메일 발송 경계가 커서 PLAT-069로 분리
- 리뷰 보강 완료: 삭제 user 제외, 멤버 목록 기본 정렬 고정, 큰 page offset 방어, `UserApi` summary 계약 default 제거

## 작업 위치

- Repo: `synapse-platform-svc`
- Branch: `feature/PLAT-068-tenant-self-service`
- Base: `dev`
- 작업문서: `docs/ai/current`
- 이전 current archive: `docs/ai/archive/20260610-plat-067-completed`

## 구현 결과

1. Entity 보강 완료
   - `Tenant.updateName(...)`
   - `Tenant.updateSettings(...)`
   - `Tenant.getTenantType()`, `getRegion()`, `getSettings()`, `getUpdatedAt()`
   - `TenantMember.changeRole(...)`
   - `TenantMember.getJoinedAt()`

2. Repository query 추가 완료
   - `TenantMemberRepository.findByTenantId(...)`
   - `TenantMemberRepository.findByTenantIdAndUserId(...)`
   - `TenantMemberRepository.countByTenantIdAndRole(...)`

3. User API 확장 완료
   - 멤버 목록 표시용 summary record 추가
   - `UserSummary(UUID id, String email, String displayName)`
   - `List<UserSummary> findSummariesByIds(Collection<UUID> userIds)`
   - default 구현 없이 명시 구현 계약으로 유지

4. DTO 추가 완료
   - `MyTenantResponse`
   - `UpdateTenantRequest`
   - `TenantMemberResponse`
   - `TenantMemberPageResponse`
   - `UpdateTenantMemberRoleRequest`

5. Service 추가 완료
   - `TenantSelfServiceService`
   - current user id -> `UserApi.findById(userId).defaultTenantId()`
   - membership 검증
   - owner/admin 권한 검증
   - 자기 자신 삭제/변경 방지
   - 마지막 owner 보호
   - summary가 없는 삭제 user tenant member 제외
   - active member 기준 pagination metadata 계산
   - 큰 page offset 요청 시 overflow 없이 빈 목록 반환

6. Controller 추가 완료
   - `GET /api/v1/tenants/me`
   - `PUT /api/v1/tenants/me`
   - `GET /api/v1/tenants/me/members`
   - `PUT /api/v1/tenants/me/members/{userId}`
   - `DELETE /api/v1/tenants/me/members/{userId}`
   - 멤버 목록 기본 정렬: `joinedAt ASC, userId ASC`

7. Conditional: Invitation 후속 분리
   - `tenant_invitations`
   - `POST /api/v1/tenants/me/invitations`
   - 메일 발송 경계

## API 응답 후보

내 tenant:

```json
{
  "id": "uuid",
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

멤버 목록:

```json
{
  "items": [
    {
      "userId": "uuid",
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

## 검증 명령

```powershell
.\gradlew.bat test --tests "*TenantSelfServiceControllerTest"
.\gradlew.bat test --tests "*TenantSelfServiceServiceTest"
.\gradlew.bat test --tests "*TenantMemberRepositoryTest"
.\gradlew.bat test --tests "*TenantSelfServiceSecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

검증 결과:

- targeted test 통과
- 리뷰 보강 targeted test 통과
- `.\gradlew.bat spotbugsMain` 통과
- `.\gradlew.bat clean build` 통과

## 금지/주의

- `TASK_platform.md` 수정 금지
- env/profile 수정 금지
- gitops/shared 수정 금지
- admin tenant API 회귀 금지
- billing plan/status 변경 금지
- 초대 메일 발송을 위해 notification 내부 service를 직접 주입하지 말 것
- PR 본문 작성 시 UTF-8 body file 사용
