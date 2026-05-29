# HANDOFF — Step 8: 관리자 테넌트/사용자 관리

## FROM

Director (Claude)

## TO

Worker (Codex)

## 목적

관리자가 사용자와 테넌트를 관리(목록/검색/정지/삭제)할 수 있는 API를 구현한다.

---

## 코드베이스 현황 (작업 전 반드시 확인)

| 항목 | 상태 |
|------|------|
| Flyway 최신 | V31 (`create_notifications.sql`) |
| `User.status` 필드 | 없음 → V32로 추가 |
| `Tenant.status` 필드 | 있음 (추가 불필요) |
| `AuthRoles.ROLE_ADMIN` | 존재 |
| SecurityConfig `/admin/**` | 미설정 → 추가 필요 |
| Admin 패키지 구현 | `AdminPlaceholder.java`만 존재 |

---

## 구현 순서 (순서 준수)

### 1. V32 Flyway 마이그레이션

파일: `src/main/resources/db/migration/V32__add_user_status.sql`

```sql
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'active'
      CHECK (status IN ('active', 'suspended', 'deleted')),
  ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
```

### 2. UserStatus enum + AttributeConverter

파일: `src/main/java/com/synapse/platform/user/entity/UserStatus.java`

```java
package com.synapse.platform.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum UserStatus {
    ACTIVE, SUSPENDED, DELETED;

    public String toDbValue() {
        return name().toLowerCase();
    }

    public static UserStatus fromDbValue(String value) {
        return valueOf(value.toUpperCase());
    }

    @Converter(autoApply = true)
    public static class StatusConverter implements AttributeConverter<UserStatus, String> {
        @Override
        public String convertToDatabaseColumn(UserStatus status) {
            return status == null ? null : status.toDbValue();
        }

        @Override
        public UserStatus convertToEntityAttribute(String dbValue) {
            return dbValue == null ? null : UserStatus.fromDbValue(dbValue);
        }
    }
}
```

`@Enumerated(EnumType.STRING)` 사용 금지 — DB CHECK 제약이 소문자(`active|suspended|deleted`)이므로 반드시 `AttributeConverter`로 변환해야 합니다.

### 3. User 엔티티 수정

파일: `src/main/java/com/synapse/platform/user/entity/User.java`

추가할 필드 (`@Enumerated` 없이 — `StatusConverter`가 autoApply로 자동 적용됨):
```java
@Column(nullable = false)
private UserStatus status = UserStatus.ACTIVE;

@Column(name = "suspended_at")
private Instant suspendedAt;
```

추가할 도메인 메서드:
```java
public void suspend() {
    this.status = UserStatus.SUSPENDED;
    this.suspendedAt = Instant.now();
}

public void activate() {
    this.status = UserStatus.ACTIVE;
    this.suspendedAt = null;
}

public void softDelete() {
    this.status = UserStatus.DELETED;
    this.deletedAt = Instant.now();
    this.email = "deleted_" + this.id + "@deleted.invalid";
    this.displayName = "Deleted User";
}
```

### 4. SecurityConfig 수정

파일: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`

`.anyRequest().authenticated()` 앞에 추가:
```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
```

### 5. UserRepository 확장

파일: `src/main/java/com/synapse/platform/user/repository/UserRepository.java`

`JpaSpecificationExecutor<User>` 추가 상속.

```java
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> { ... }
```

### 6. AdminUserService (user 모듈)

파일: `src/main/java/com/synapse/platform/user/service/AdminUserService.java`

구현할 메서드:
- `Page<AdminUserResponse> listUsers(AdminUserSearchRequest req)` — Specification으로 동적 검색
- `void suspendUser(UUID userId, UUID adminId)` — status → SUSPENDED, Redis 토큰 삭제
- `void activateUser(UUID userId, UUID adminId)` — status → ACTIVE
- `void deleteUser(UUID userId, UUID adminId)` — status → DELETED, soft delete, 마스킹, Redis 토큰 삭제

제약:
- 관리자 본인(adminId == userId) 정지/삭제 시 `IllegalArgumentException` throw
- 정지/삭제 시 `RefreshTokenService.deleteAllByUserId(userId)` 호출
- `@Transactional` 적용

### 7. AdminUserController (user 모듈)

파일: `src/main/java/com/synapse/platform/user/controller/AdminUserController.java`

```
GET    /api/v1/admin/users                  listUsers(@AuthenticationPrincipal + Pageable)
PUT    /api/v1/admin/users/{id}/status      changeUserStatus (body: { "status": "suspended"|"active" })
DELETE /api/v1/admin/users/{id}             deleteUser
```

- `@PreAuthorize("hasRole('ADMIN')")` 클래스 레벨 적용
- `@AuthenticationPrincipal`로 adminId 추출

### 8. TenantRepository 확인 후 AdminTenantService (billing 모듈)

파일: `src/main/java/com/synapse/platform/billing/service/AdminTenantService.java`

구현할 메서드:
- `Page<AdminTenantResponse> listTenants(Pageable pageable)`
- `void changeTenantStatus(UUID tenantId, String status)` — status: "active"|"suspended"

### 9. AdminTenantController (billing 모듈)

파일: `src/main/java/com/synapse/platform/billing/controller/AdminTenantController.java`

```
GET  /api/v1/admin/tenants              listTenants
PUT  /api/v1/admin/tenants/{id}/status  changeTenantStatus (body: { "status": "suspended" })
```

- `@PreAuthorize("hasRole('ADMIN')")` 클래스 레벨 적용
- `Tenant`에 도메인 메서드 추가 필요: `suspend()`, `activate()` (status 필드가 String이므로 직접 할당)

### 10. DTO 목록

| DTO | 위치 | 필드 |
|-----|------|------|
| `AdminUserResponse` | user/dto | id, email, displayName, status, createdAt, suspendedAt |
| `AdminUserSearchRequest` | user/dto | q(검색어), status, page, size |
| `UserStatusChangeRequest` | user/dto | status (active\|suspended) |
| `AdminTenantResponse` | billing/dto | id, name, slug, plan, status, createdAt |
| `TenantStatusChangeRequest` | billing/dto | status (active\|suspended) |

### 11. 로그인 시 status 검증

기존 OAuth 로그인 흐름(`OAuthUserResolver` 또는 `CustomOAuth2UserService`)에서 User 로드 후:
```java
if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DELETED) {
    throw new DisabledException("Account is " + user.getStatus().toDbValue());
}
```

이메일 로그인(`UserDetailsService`)에도 동일하게 적용.

### 12. 통합 테스트

파일:
- `src/test/java/com/synapse/platform/user/controller/AdminUserControllerIT.java`
- `src/test/java/com/synapse/platform/billing/controller/AdminTenantControllerIT.java`

필수 시나리오:
- 관리자 토큰으로 사용자 목록 조회 → 200
- 비관리자 토큰으로 접근 → 403
- 미인증으로 접근 → 401
- 사용자 정지 → 정지된 사용자 로그인 시 401
- 관리자 본인 정지 시도 → 400
- 사용자 삭제 → email 마스킹 확인

---

## 검증 체크리스트

```bash
./gradlew test                         # 전체 테스트 통과
./gradlew check                        # checkstyle + spotbugs 통과
./gradlew test --tests "*AdminUser*"   # 관리자 사용자 테스트
./gradlew test --tests "*AdminTenant*" # 관리자 테넌트 테스트
```

---

## 완료 후 Director에게 반환할 내용

- 구현된 파일 목록
- `./gradlew check` 결과
- 미결 사항 또는 설계 변경 발생 시 사유
