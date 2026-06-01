# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

### 코드베이스 현황
- **Flyway 최신 버전**: V31 (`create_notifications.sql`) → 신규 마이그레이션은 **V32**
- **User 엔티티**: `status` 필드 없음 → V32 마이그레이션으로 `status VARCHAR(20)`, `suspended_at TIMESTAMPTZ` 추가 필요
- **User 엔티티 기존 필드**: `deleted_at`, `anonymized_at` 이미 존재 → soft delete/마스킹 기반 확보됨
- **Tenant 엔티티**: `status` 필드 이미 존재 (기본값 "active"), `deleted_at` 존재 → 추가 마이그레이션 불필요
- **Admin 패키지**: `AdminPlaceholder.java`만 존재, 실질 구현 없음
- **UserRepository**: 기본 메서드만 존재 (`findByEmail`, `existsByEmail` 등) → Specification 기반 검색 추가 필요
- **SecurityConfig**: `/admin/**` 경로 미설정 → `.anyRequest().authenticated()`로만 처리 중
- **ROLE_ADMIN**: `AuthRoles.java`에 상수 존재
- **모듈 배치**: 사용자 관리 → `user` 모듈, 테넌트 관리 → `billing` 모듈

### 확정된 설계 결정
- **API 경로**: `/api/v1/admin/users/**`, `/api/v1/admin/tenants/**` (TASK Done When의 `/admin/` 표기는 `/api/v1/admin/`의 약식 표기였음 — 구현 기준은 `/api/v1/admin/**`)
- **UserStatus 저장 방식**: `AttributeConverter<UserStatus, String>` 사용. `@Enumerated(EnumType.STRING)` 사용 금지 — DB CHECK 제약이 소문자(`active|suspended|deleted`)이므로 converter로 소문자 변환 필수
- **UserStatus enum**: ACTIVE | SUSPENDED | DELETED (`toDbValue()` → 소문자, `fromDbValue()` → 대문자 파싱)
- **사용자 삭제**: soft delete (status → DELETED) + email/display_name 마스킹
- **세션 무효화**: user 모듈은 `UserSessionsRevocationRequested` 이벤트를 발행하고, auth 모듈의 `UserSessionRevocationListener`가 `RefreshTokenService.delete(UUID)`를 호출한다. 직접 `user -> auth` 의존은 Modulith 순환을 만들기 때문에 금지한다.
- **권한 검증**: `@PreAuthorize("hasRole('ADMIN')")` — @AdminOnly AOP 불필요
- **검색**: Specification 패턴 (email LIKE, display_name LIKE, status 필터)
- **Tenant.status**: 이미 `String status = "active"` 존재. 도메인 메서드(`suspend()`, `activate()`) 추가 필요
- **로그인 차단 위치**: OAuth → `OAuthUserResolver`, 이메일 → `EmailPasswordAuthService`
- **OAuth 삭제 계정 차단**: existing OAuth identity 경로에서는 `userApi.findById()`보다 먼저 `userApi.isLoginAllowed(userId)`를 확인한다. soft deleted user는 `@SQLRestriction("deleted_at IS NULL")` 때문에 `findById()`가 empty가 되므로, 순서가 반대면 `DisabledException`이 아니라 `IllegalStateException`으로 흐를 수 있다.
- **Admin 본인 정지/삭제 방지**: 전역 `IllegalArgumentException -> 400` 매핑은 사용하지 않는다. 내부 설정/crypto/Avro 오류가 400으로 숨겨질 수 있으므로 `AdminSelfActionException extends BusinessException`으로 범위를 좁힌다.
- **Tenant 상태 변경**: 목록 조회와 동일하게 deleted tenant 제외 조건을 적용한다. 상태 변경은 `TenantRepository.findByIdAndDeletedAtIsNull(UUID)`를 사용한다.

## 현재 미결 사항

- (없음 — Worker 리뷰로 모든 이슈 확정됨)

## 리뷰 후 보완 기록

- 2026-05-28 Worker 리뷰에서 OAuth deleted user 차단 순서, 전역 `IllegalArgumentException` handler 범위, deleted tenant 상태 변경 가능성이 지적됨.
- 수정 방향은 모두 코드에 반영한다:
  - OAuth existing identity → `isLoginAllowed(userId)` 선검증
  - Admin self action → `AdminSelfActionException` 전용 400
  - Tenant status change → `findByIdAndDeletedAtIsNull` 사용
- 2026-05-28 추가 리뷰에서 `GET /api/v1/admin/users?status=invalid`가 500으로 흐를 수 있는 입력 검증 공백이 확인됨. `InvalidUserStatusFilterException extends BusinessException`으로 400 처리한다.

## 활성 제약

- JWT 서명: RS256 고정
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- Entity @Setter 금지 → 도메인 메서드만 허용
- `@ApplicationModule` 없는 새 패키지 금지

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md Step 8
- docs/rules/01-security.md (인증/권한 규칙)
- docs/rules/07-platform-spring.md (Entity, DTO, Validation 규칙)
- docs/rules/11-data-sovereignty.md (개인정보 마스킹, soft delete)
- src/main/java/com/synapse/platform/user/entity/User.java (기존 엔티티 참조)
- src/main/java/com/synapse/platform/auth/entity/Tenant.java (기존 엔티티 참조)
- src/main/java/com/synapse/platform/auth/config/SecurityConfig.java (SecurityConfig 수정 대상)
