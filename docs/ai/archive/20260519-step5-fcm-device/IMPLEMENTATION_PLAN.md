# FCM Device Registration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `com.synapse.platform.notification` 모듈에 FCM 디바이스 토큰 등록/해제 API와 저장소를 구현한다.

**Architecture:** Notification 모듈은 billing 모듈과 같은 `controller/service/entity/repository/dto/config/exception` 구조를 따른다. 토큰 등록은 native PostgreSQL UPSERT로 원자 처리하고, tenant는 `UserApi.findById(userId).defaultTenantId()`로 resolve한다. `/api/v1/notifications/**`는 별도 `SecurityFilterChain`으로 JWT 인증만 처리해 OAuth2 흐름과 분리한다.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Security, Spring Data JPA, Flyway, PostgreSQL Testcontainers, JUnit 5, Mockito, JaCoCo.

---

## File Structure

- Create: `src/main/resources/db/migration/V27__create_device_tokens.sql`
- Create: `src/main/java/com/synapse/platform/notification/entity/Platform.java`
- Create: `src/main/java/com/synapse/platform/notification/entity/PlatformConverter.java`
- Create: `src/main/java/com/synapse/platform/notification/entity/DeviceToken.java`
- Create: `src/main/java/com/synapse/platform/notification/repository/DeviceTokenRepository.java`
- Create: `src/main/java/com/synapse/platform/notification/exception/DeviceRegistrationLimitExceededException.java`
- Create: `src/main/java/com/synapse/platform/notification/service/DeviceTokenService.java`
- Create: `src/main/java/com/synapse/platform/notification/controller/DeviceTokenController.java`
- Create: `src/main/java/com/synapse/platform/notification/dto/request/DeviceTokenRequest.java`
- Create: `src/main/java/com/synapse/platform/notification/dto/response/DeviceTokenResponse.java`
- Create: `src/main/java/com/synapse/platform/notification/config/NotificationSecurityConfig.java`
- Modify: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- Modify: `src/main/java/com/synapse/platform/global/exception/GlobalExceptionHandler.java`
- Delete: `src/main/java/com/synapse/platform/notification/NotificationPlaceholder.java`
- Test: `src/test/java/com/synapse/platform/notification/DeviceTokenServiceTest.java`
- Test: `src/test/java/com/synapse/platform/notification/DeviceTokenIntegrationTest.java`

## Implementation Notes

- `HANDOFF.md`가 최우선 구현 기준이다.
- `Platform`에는 `@JsonCreator`와 `@JsonValue`만 사용한다. `@JsonDeserialize`는 사용하지 않는다.
- `DeviceTokenController.currentUserId()`는 `BillingController`보다 방어적으로 구현한다. `authentication == null` 또는 malformed name이면 401을 반환한다.
- `DeviceTokenResponse`는 WORKFLOW 5.6 대비용으로 생성하지만 Step 5 POST/DELETE 응답 body에는 사용하지 않는다.
- `DeviceTokenRepository.upsert()`는 `is_active`를 INSERT에서 명시하지 않는다. DDL 기본값 `TRUE`를 사용한다. 기존 token 재등록 시 `tenant_id`, `user_id`, `updated_at`만 갱신한다.
- 통합 테스트는 `device_tokens.tenant_id` FK 때문에 `TenantRepository`로 tenant를 먼저 저장하고, `User.updateDefaultTenantId(tenantId)` 후 user를 저장한다.

---

## Task 1: Migration And Entity Model

**Files:**
- Create: `src/main/resources/db/migration/V27__create_device_tokens.sql`
- Create: `src/main/java/com/synapse/platform/notification/entity/Platform.java`
- Create: `src/main/java/com/synapse/platform/notification/entity/PlatformConverter.java`
- Create: `src/main/java/com/synapse/platform/notification/entity/DeviceToken.java`
- Delete: `src/main/java/com/synapse/platform/notification/NotificationPlaceholder.java`

- [x] **Step 1: Add Flyway migration**

Create `V27__create_device_tokens.sql` exactly:

```sql
CREATE TABLE device_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    user_id     UUID        NOT NULL,
    token       TEXT        NOT NULL,
    platform    VARCHAR(10) NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_tenant_user ON device_tokens (tenant_id, user_id);
```

- [x] **Step 2: Add Platform enum**

Create `Platform.java` with `IOS("ios")`, `ANDROID("android")`, `WEB("web")`, `getValue()`, and case-insensitive `from(String value)`.

Expected behavior:
- `"ios"` and `"IOS"` map to `Platform.IOS`
- `"android"` maps to `Platform.ANDROID`
- `"MOBILE"` throws `IllegalArgumentException`

- [x] **Step 3: Add PlatformConverter**

Create `PlatformConverter.java` implementing `AttributeConverter<Platform, String>`.

Expected behavior:
- `Platform.WEB` stores as `"web"`
- `null` stores/loads as `null`
- DB value `"android"` loads as `Platform.ANDROID`

- [x] **Step 4: Add DeviceToken entity**

Create `DeviceToken.java` with:
- `@Entity`
- `@Table(name = "device_tokens")`
- fields: `UUID id`, `UUID tenantId`, `UUID userId`, `String token`, `Platform platform`, `boolean isActive`, `Instant createdAt`, `Instant updatedAt`
- protected no-args constructor
- getters for every field used by tests/service
- `@Convert(converter = PlatformConverter.class)` on `platform`

- [x] **Step 5: Remove placeholder**

Delete `src/main/java/com/synapse/platform/notification/NotificationPlaceholder.java`.

- [x] **Step 6: Compile main code**

Run:

```powershell
.\gradlew.bat compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 2: Repository And Service Logic

**Files:**
- Create: `src/main/java/com/synapse/platform/notification/repository/DeviceTokenRepository.java`
- Create: `src/main/java/com/synapse/platform/notification/exception/DeviceRegistrationLimitExceededException.java`
- Create: `src/main/java/com/synapse/platform/notification/service/DeviceTokenService.java`
- Test: `src/test/java/com/synapse/platform/notification/DeviceTokenServiceTest.java`

- [x] **Step 1: Write service unit tests first**

Create `DeviceTokenServiceTest.java` with Mockito tests:

- `register_newTokenWhenUserHasFiveDevices_shouldThrowLimitExceeded`
- `register_existingToken_shouldSkipLimitCheckAndUpsert`
- `register_newToken_shouldResolveTenantAndUpsert`
- `register_missingUser_shouldThrowEntityNotFoundException`
- `unregister_ownDevice_shouldDelete`
- `unregister_otherUsersDevice_shouldThrowAccessDeniedException`
- `unregister_missingDevice_shouldThrowEntityNotFoundException`

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.notification.DeviceTokenServiceTest" --no-daemon
```

Expected before implementation: compile failure or failing tests because service/repository classes do not exist yet.

- [x] **Step 2: Add DeviceTokenRepository**

Implement:

```java
Optional<DeviceToken> findByToken(String token);
long countByUserId(UUID userId);
List<DeviceToken> findByUserId(UUID userId);
void upsert(UUID id, UUID tenantId, UUID userId, String token, String platform);
```

The `upsert` method must use `@Modifying(clearAutomatically = true, flushAutomatically = true)` and native SQL:

```sql
INSERT INTO device_tokens (id, tenant_id, user_id, token, platform, created_at, updated_at)
VALUES (:id, :tenantId, :userId, :token, :platform, NOW(), NOW())
ON CONFLICT (token) DO UPDATE
  SET tenant_id  = EXCLUDED.tenant_id,
      user_id    = EXCLUDED.user_id,
      updated_at = NOW()
```

- [x] **Step 3: Add limit exception**

Create `DeviceRegistrationLimitExceededException` extending `BusinessException`:

```java
super("PLAT-NOTIFICATION-001", 409, "Device registration limit exceeded (max 5)");
```

- [x] **Step 4: Add DeviceTokenService**

Implement:

- `register(UUID userId, String token, Platform platform)`
  - resolve tenant via `UserApi.findById(userId).map(UserInfo::defaultTenantId)`
  - throw `EntityNotFoundException("User not found")` when missing
  - call `findByToken(token)` before count
  - if token is new and `countByUserId(userId) >= 5`, throw `DeviceRegistrationLimitExceededException`
  - call `upsert(UUID.randomUUID(), tenantId, userId, token, platform.getValue())`

- `unregister(UUID userId, UUID deviceId)`
  - `findById(deviceId)` or `EntityNotFoundException("Device not found")`
  - compare `deviceToken.getUserId()` with `userId`
  - throw `AccessDeniedException("Not owner")` on mismatch
  - delete with `deleteById(deviceId)`

- [x] **Step 5: Run service tests**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.notification.DeviceTokenServiceTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 3: API, Security, And Exception Mapping

**Files:**
- Create: `src/main/java/com/synapse/platform/notification/dto/request/DeviceTokenRequest.java`
- Create: `src/main/java/com/synapse/platform/notification/dto/response/DeviceTokenResponse.java`
- Create: `src/main/java/com/synapse/platform/notification/controller/DeviceTokenController.java`
- Create: `src/main/java/com/synapse/platform/notification/config/NotificationSecurityConfig.java`
- Modify: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- Modify: `src/main/java/com/synapse/platform/global/exception/GlobalExceptionHandler.java`

- [x] **Step 1: Add DTO records**

`DeviceTokenRequest`:

```java
public record DeviceTokenRequest(
        @NotBlank String token,
        @NotNull Platform platform
) {
}
```

`DeviceTokenResponse`:

```java
public record DeviceTokenResponse(
        UUID id,
        String platform,
        boolean isActive,
        Instant createdAt
) {
}
```

- [x] **Step 2: Add DeviceTokenController**

Implement:
- `POST /api/v1/notifications/devices` -> `201 Created`, no body
- `DELETE /api/v1/notifications/devices/{id}` -> `204 No Content`, no body
- `currentUserId(Authentication authentication)` returns 401 when authentication is null or malformed.

Use `ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")` for auth parsing failure.

- [x] **Step 3: Add notification SecurityFilterChain**

Create `NotificationSecurityConfig` with:
- `@Configuration`
- `@Order(1)`
- `securityMatcher("/api/v1/notifications/**")`
- stateless session
- CSRF disabled
- `jwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- every request authenticated
- 401 authentication entry point

- [x] **Step 4: Add order to existing SecurityConfig**

Add `@Order(2)` to `SecurityConfig`.

- [x] **Step 5: Add EntityNotFoundException handler**

Add handler below `handleBusinessException`:

```java
@ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
        jakarta.persistence.EntityNotFoundException exception,
        HttpServletRequest request) {
    HttpStatus status = HttpStatus.NOT_FOUND;
    return ResponseEntity.status(status)
            .body(errorResponse("PLAT-404", status, exception.getMessage(), request));
}
```

- [x] **Step 6: Compile test code**

Run:

```powershell
.\gradlew.bat compileTestJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 4: Integration Tests

**Files:**
- Test: `src/test/java/com/synapse/platform/notification/DeviceTokenIntegrationTest.java`

- [x] **Step 1: Create Testcontainers integration test**

Use:
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
- `@Testcontainers`
- `GenericContainer<?> postgres = DockerImageName.parse("pgvector/pgvector:pg16")`
- `@DynamicPropertySource` for datasource
- `@BeforeAll` explicit Flyway migrate
- repositories: `DeviceTokenRepository`, `TenantRepository`, `UserRepository`
- `TestRestTemplate` or `MockMvc` with Spring Security test support; prefer `MockMvc` if existing auth tests are easier to reuse.

- [x] **Step 2: Add JWT-authenticated request helper**

Use Spring Security test support when using MockMvc:

```java
.with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
```

If the local security setup does not expose JWT test support for the custom filter, build a signed RS256 token using `JwtTokenProvider` and configured test keys.

- [x] **Step 3: Add tenant/user fixture helper**

Fixture sequence:

1. Save `Tenant.ofPersonal(displayName, slug)` using `TenantRepository`.
2. Save `User.ofOAuth(email, username, displayName, avatarUrl)` after `user.updateDefaultTenantId(tenant.getId())`.
3. Return `user.getId()` and `tenant.getId()`.

- [x] **Step 4: Add required integration scenarios**

Implement all methods from `HANDOFF.md`:

- `register_newDevice_shouldReturn201`
- `register_sameTokenForDifferentUser_shouldKeepOneRowAndMoveOwner`
- `register_existingToken_shouldSkipLimitCheckAndUpsert`
- `register_sixthNewDevice_shouldReturn409`
- `register_invalidPlatform_shouldReturn400`
- `register_withoutJwt_shouldReturn401`
- `unregister_ownDevice_shouldReturn204`
- `unregister_otherUsersDevice_shouldReturn403`
- `unregister_missingDevice_shouldReturn404`

- [x] **Step 5: Run notification integration test**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.notification.DeviceTokenIntegrationTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

Execution note: 첫 통합 테스트 실행은 `UnreachableFilterChainException`으로 실패했다. 기존 default `SecurityFilterChain`이 모든 요청을 먼저 잡아 notification 전용 체인이 unreachable 상태가 됐다. `@Order`는 설정 클래스가 아니라 각 `SecurityFilterChain` `@Bean` 메서드에 직접 부여한다.

Execution note: 두 번째 통합 테스트 실행은 `jwt()` test post-processor가 프로젝트에 없는 `spring-security-oauth2-resource-server` 클래스를 요구해 실패했다. 새 의존성을 추가하지 않고 `user(userId.toString())` post-processor로 인증 컨텍스트를 주입한다.

Execution note: 세 번째 통합 테스트 실행은 invalid platform 역직렬화 실패가 전역 catch-all에 잡혀 500이 됐다. `HttpMessageNotReadableException`을 400으로 매핑해 잘못된 JSON 요청을 명시적으로 처리한다.

---

## Task 5: Verification And Review

**Files:**
- Modify as needed based on verification failures.

- [x] **Step 1: Search forbidden patterns**

Run:

```powershell
rg -n "firebase-admin|@Enumerated|notification_preferences|NotificationPlaceholder|com\\.synapse\\.platform\\.shared|io\\.synapse" build.gradle.kts src
```

Expected:
- no `firebase-admin`
- no `notification_preferences`
- no `NotificationPlaceholder`
- no old package names
- `@Enumerated` may exist elsewhere only if unrelated to notification; notification must not use it.

- [x] **Step 2: Run Modulith structure test**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.PlatformModuleStructureTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

Execution note: 첫 Modulith 검증은 notification 모듈이 auth 내부 타입 `JwtAuthenticationFilter`를 직접 참조해 실패했다. 모듈 경계를 지키기 위해 `@Qualifier("jwtAuthenticationFilter") Filter`로 주입받아 auth 구현 타입 import를 제거한다.

- [x] **Step 3: Run full tests**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Run full build if tests pass**

Run:

```powershell
.\gradlew.bat clean build --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Review changed files**

Run:

```powershell
git diff --stat
git diff -- src/main/java/com/synapse/platform/notification src/test/java/com/synapse/platform/notification src/main/resources/db/migration/V27__create_device_tokens.sql src/main/java/com/synapse/platform/auth/config/SecurityConfig.java src/main/java/com/synapse/platform/global/exception/GlobalExceptionHandler.java
```

Review for:
- no FCM send implementation
- no firebase dependency
- tenant FK handled in tests
- controller returns no body for POST/DELETE
- repository upsert uses native query and `@Modifying`

---

## Self-Review

**Spec coverage:** All HANDOFF steps are mapped: V27 migration, Platform/converter, entity/repository, exception, service, controller/DTO, security filter chain, global exception handler, integration tests, placeholder removal, and final full test/build verification.

**Placeholder scan:** No task uses TBD/TODO/fill-later language. Test names and command lines are explicit.

**Type consistency:** `tenantId`, `userId`, `token`, `platform`, `isActive`, `createdAt`, and `updatedAt` match the DDL and entity plan. Repository UPSERT updates `tenant_id`, `user_id`, and `updated_at`, matching the latest TASK constraints.
