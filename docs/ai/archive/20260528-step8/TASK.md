# TASK — Step 8: 관리자 테넌트/사용자 관리

> 출처: TASK_platform.md Step 8

## 상태

- Phase: 설계
- 담당 Agent: Director (Claude)
- 시작일: 2026-05-28
- 목표 완료일: 2026-05-29

---

## Step Goal

관리자가 테넌트와 사용자를 관리(목록/검색/정지/삭제)할 수 있다.

## Done When

- [x] `GET /api/v1/admin/users` → 사용자 목록 조회 (페이징 + 이름/이메일 검색)
- [x] `PUT /api/v1/admin/users/{id}/status` → 사용자 상태 변경 (suspend/activate)
- [x] `DELETE /api/v1/admin/users/{id}` → 사용자 삭제 (soft delete + 개인정보 마스킹)
- [x] `GET /api/v1/admin/tenants` → 테넌트 목록 조회 (페이징)
- [x] `PUT /api/v1/admin/tenants/{id}/status` → 테넌트 상태 변경 (body: `{ "status": "suspended" }`)
- [x] 관리자 권한 검증 동작 (`ROLE_ADMIN`)
- [x] 통합 테스트 통과 (관리자/비관리자 시나리오)

## Scope

- In Scope:
  - User 엔티티 `status` 필드 추가 + V32 Flyway 마이그레이션
  - SecurityConfig `/api/v1/admin/**` → `hasRole('ADMIN')` 추가
  - 관리자 사용자 목록/검색 API (user 모듈)
  - 사용자 정지/삭제 API (user 모듈)
  - 테넌트 목록 조회 API (billing 모듈)
  - 테넌트 상태 변경 API (billing 모듈)
  - 통합 테스트
- Out of Scope:
  - 테넌트 생성 (자동 생성 정책)
  - 사용자 데이터 내보내기 (GDPR)
  - 관리자 활동 로그 (audit_logs에서 처리 — Step 6 완료)
  - @AdminOnly AOP (SecurityConfig + @PreAuthorize로 충분)

## Input

- `User` 엔티티 (`com.synapse.platform.user.entity.User`) — status 필드 없음 → 추가 필요
- `Tenant` 엔티티 (`com.synapse.platform.auth.entity.Tenant`) — status 필드 있음
- `AuthRoles.ROLE_ADMIN` 상수 존재
- Flyway 최신: V31 → 신규는 V32

## Instructions

1. `V32__add_user_status.sql` 작성 (users 테이블에 status, suspended_at 컬럼 추가)
2. `User` 엔티티에 `status`, `suspendedAt` 필드 추가 + `UserStatus` enum (ACTIVE|SUSPENDED|DELETED)
3. `SecurityConfig`에 `/api/v1/admin/**` → `hasRole('ADMIN')` 규칙 추가
4. `UserRepository`에 Specification 기반 동적 검색 + 페이징 메서드 추가
5. `AdminUserService` 구현 (user 모듈): 목록조회, 검색, 정지, 삭제
6. `AdminUserController` 구현 (user 모듈): GET/PUT/DELETE `/api/v1/admin/users/**`
7. `TenantRepository`에 페이징 조회 확인 (기존 메서드 재사용 가능 여부 파악)
8. `AdminTenantService` 구현 (billing 모듈): 목록조회, 상태변경
9. `AdminTenantController` 구현 (billing 모듈): GET/PUT `/api/v1/admin/tenants/**`
10. 통합 테스트 작성 (관리자/비관리자 시나리오, 401/403 포함)

## Output Format

관리자 기능 코드 + 테스트 코드
- 사용자 관리 → `user` 모듈
- 테넌트 관리 → `billing` 모듈
- 감사 조회 → `audit` 모듈 (Step 6 완료, 손대지 않음)

## Constraints

- 관리자 권한 (`ROLE_ADMIN`) 필수 — `@PreAuthorize("hasRole('ADMIN')")`
- 사용자 삭제 시 개인정보 마스킹 (email, display_name → anonymized)
- 정지된 사용자 로그인 차단 (OAuth/email 로그인 시 status 검증)
- 관리자 본인 정지/삭제 방지
- 정지/삭제 시 Redis Refresh Token 즉시 삭제 (세션 무효화)
- 테스트 커버리지: 신규 코드 80% 이상
- 모듈 간 순환 의존 금지

## Review Follow-up — 2026-05-28

- OAuth existing identity 경로에서 soft deleted user가 `findById()` empty로 `IllegalStateException` 처리되지 않도록 `isLoginAllowed(userId)`를 먼저 확인한다.
- Admin 본인 정지/삭제 400 응답은 전역 `IllegalArgumentException` handler가 아니라 `AdminSelfActionException extends BusinessException`으로 처리한다.
- Tenant 상태 변경은 deleted tenant를 제외하기 위해 `findByIdAndDeletedAtIsNull(UUID)` 기준으로 조회한다.
- 위 항목은 각각 `OAuthUserResolverTest`, `AdminUserServiceTest`/`GlobalExceptionHandlerTest`, `TenantAdminServiceTest`에 회귀 테스트를 둔다.
- 추가 리뷰에서 사용자 목록 `status` query param 검증 공백을 확인했다. invalid status는 `InvalidUserStatusFilterException`으로 400 응답하며 `AdminUserServiceTest`, `AdminUserControllerTest`에 회귀 테스트를 둔다.

## Duration

1.5일 (2026-05-28 ~ 2026-05-29)

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
