# CONTEXT - PLAT-071: Admin 대시보드 분석 API

## 현재 상태

- 로컬 `dev`는 PR #83 `feat(auth): Auth 복구 플로우 보강` 머지 커밋까지 fast-forward 완료.
- 현재 브랜치: `feature/PLAT-071-admin-analytics`.
- 루트 `docs/BACKEND_GAP_platform.md` 기준 platform 담당 gap 중 A-1, A-2, A-3, A-4, A-5는 dev에 반영됨.
- 남은 platform 핵심 gap은 A-6 Admin 대시보드 보강.
- 이번 작업은 A-6 전체 중 첫 단위인 Admin analytics summary API만 처리한다.

## 요구사항 근거

### 루트 문서

- `C:\workspace\team_project_2\docs\BACKEND_GAP_platform.md`
  - A-6: Admin 대시보드 보강
  - 요구 후보:
    - `GET /api/v1/admin/analytics/*`
    - `GET`/`PUT /api/v1/admin/settings`
    - `GET`/`POST /api/v1/admin/data-requests`

### frontend 근거

- `synapse-frontend/lib/services/platform/features/admin/presentation/screens/admin_screens/admin_dashboard_screen.dart`
  - 사용자/테넌트 총계는 현재 목록 API의 `totalElements`로 조회.
  - DAU/MAU는 mock.
  - 시스템 사용량은 mock.
  - 긴급 처리 항목은 mock.
  - 최근 활동은 mock.
- `synapse-frontend/lib/services/platform/features/admin/presentation/screens/admin_screens/admin_system_settings_screen.dart`
  - 피처 플래그와 rate limit 저장은 mock.
  - 이번 PLAT-071에서는 제외하고 PLAT-072 후보로 둔다.
- `synapse-frontend/lib/services/platform/features/admin/presentation/screens/admin_screens/admin_data_request_screen.dart`
  - GDPR/data request API는 mock.
  - 이번 PLAT-071에서는 제외하고 PLAT-073 후보로 둔다.

## 기존 Backend API

- `AdminUserController`
  - `GET /api/v1/admin/users`
  - `PUT /api/v1/admin/users/{id}/status`
  - `DELETE /api/v1/admin/users/{id}`
- `AdminTenantController`
  - `GET /api/v1/admin/tenants`
  - `PUT /api/v1/admin/tenants/{id}/status`
- `AuditLogController`
  - `GET /api/v1/admin/audit-logs`
- `SecurityConfig`
  - `/api/v1/admin/**`는 `ROLE_ADMIN` 필요.

## 사용 가능한 데이터

### users

- Entity: `com.synapse.platform.user.entity.User`
- 주요 필드:
  - `status`
  - `createdAt`
  - `lastLoginAt`
  - `deletedAt`
- 참고:
  - `@SQLRestriction("deleted_at IS NULL")` 적용.
  - 일반 JPA count는 삭제 사용자를 제외하므로 total/deleted는 native query로 soft-delete 행까지 포함한다.
  - newToday/DAU 후보 지표는 `generatedAt` 날짜 00:00 이후, MAU 후보 지표는 최근 30일 `lastLoginAt` 기준으로 산출한다.

### tenants

- Entity: `com.synapse.platform.auth.entity.Tenant`
- 주요 필드:
  - `plan`
  - `status`
  - `createdAt`
  - `deletedAt`
- Repository:
  - `TenantRepository`
  - `findAllByDeletedAtIsNull(Pageable)`
  - `findByIdAndDeletedAtIsNull(UUID)`

### audit_logs

- Entity: `com.synapse.platform.audit.entity.AuditLog`
- 주요 필드:
  - `action`
  - `userId`
  - `resourceType`
  - `resourceId`
  - `createdAt`
- Repository:
  - `AuditLogRepository`
  - 현재 action/userId 필터와 retention delete만 있음.
  - 최근 활동용 `findAll(Pageable)` 또는 명시 query 사용 가능.

### notifications

- Entity: `com.synapse.platform.notification.entity.Notification`
- 주요 필드:
  - `channel`
  - `status`
  - `notificationType`
  - `sentAt`
  - `createdAt`
- Repository:
  - `NotificationRepository`
  - 현재 사용자별 inbox/count 중심.
  - admin usage summary용 전체 sent count query 추가 가능.

### subscriptions / payment_history

- Entity:
  - `Subscription`
  - `PaymentHistory`
- Repository:
  - `SubscriptionRepository`
  - `PaymentHistoryRepository`
- active subscription count나 plan distribution은 산출 가능.

## 설계 방향

- 새 API:
  - `GET /api/v1/admin/analytics/summary`
- 새 패키지 후보:
  - `com.synapse.platform.admin.controller`
  - `com.synapse.platform.admin.service`
  - `com.synapse.platform.admin.dto`
- 읽기 전용 API로 시작한다.
- cross-service 값은 임의 숫자를 반환하지 않는다.
  - 예: AI token, storage, learning usage 등
  - `status: NOT_CONNECTED`, `source: learning-ai` 같은 상태로 반환.
- pending items는 이번 작업에서 실제 처리 시스템이 없으면 `NOT_IMPLEMENTED` 상태로 반환하거나 빈 목록을 반환한다.
- 프론트가 mock을 걷어낼 수 있도록 응답 필드 이름을 안정적으로 설계한다.

## 테스트 방향

- Controller test
  - admin 인증 시 200
  - 일반 사용자/미인증 접근 시 403/401
  - 응답 JSON shape 검증
- Service test
  - user/tenant/notification/audit/subscription repository 결과를 summary DTO로 조합
  - `NOT_CONNECTED` usage item이 fake numeric value 없이 내려오는지 검증
- Repository test
  - count query가 status/window 조건을 제대로 반영하는지 검증
  - soft-delete 포함 native count와 plan/payment/notification/audit query를 실제 DB 매핑으로 검증
- 구조 테스트
  - `PlatformModuleStructureTest`
- 전체 검증
  - `clean build`

## 주의사항

- `TASK_platform.md`는 최초 개발 목록 문서이므로 수정하지 않는다.
- env/profile/gitops/shared 프로젝트는 이번 작업에서 수정하지 않는다.
- 프론트 코드는 이번 작업 범위가 아니다.
- A-6 중 설정/피처 플래그와 데이터 요청은 후속 작업으로 분리한다.
- DAU/MAU 표현은 "정식 analytics"가 아니라 `users.last_login_at` 기준 platform-local 지표임을 문서와 응답 source에 남긴다.
- `*.today` 지표는 최근 24시간이 아니라 `generatedAt` 날짜의 00:00 이후 기준이다.
