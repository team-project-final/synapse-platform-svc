# HANDOFF — Step 5: FCM 디바이스 등록

> FROM: Director (Claude)
> TO: Worker (Codex)
> DATE: 2026-05-19
> BRANCH: feature/PLAT-005-fcm-device

---

## 목표

`com.synapse.platform.notification` 패키지에 FCM 디바이스 토큰 등록/해제 API를 구현한다.
토큰 저장만 담당하며, 실제 FCM 푸시 발송은 Step 7에서 구현한다.

---

## 참고 파일

- `docs/ai/current/CONTEXT.md`
- `docs/spike/notification/SAMPLING_STEP5_FCM_DEVICE.md`
- `src/main/java/com/synapse/platform/billing/service/BillingService.java` — tenantId resolve 패턴
- `src/main/java/com/synapse/platform/billing/` — 패키지 구조 참조
- `src/main/java/com/synapse/platform/global/exception/BusinessException.java`
- `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`

---

## 구현 순서 (순서 엄수)

### 1. Flyway V27 마이그레이션

파일 경로: `src/main/resources/db/migration/V27__create_device_tokens.sql`

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

> `tenant_id` — ERD 표준 컬럼 (모든 도메인 테이블 필수)
> `is_active` — 토큰 활성 여부 (WORKFLOW 5.4/5.6 명시)
> 인덱스 — ERD 컨벤션: `tenant_id` prefix 필수

---

### 2. Platform enum + Converter

패키지: `com.synapse.platform.notification.entity`

**Platform.java**

```java
public enum Platform {
    IOS("ios"), ANDROID("android"), WEB("web");

    private final String value;

    Platform(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static Platform from(String value) {
        for (Platform p : values()) {
            if (p.value.equalsIgnoreCase(value)) return p;
        }
        throw new IllegalArgumentException("Unknown platform: " + value);
    }
}
```

**PlatformConverter.java**

```java
@Converter(autoApply = true)
public class PlatformConverter implements AttributeConverter<Platform, String> {
    @Override
    public String convertToDatabaseColumn(Platform attribute) {
        return attribute == null ? null : attribute.getValue();
    }
    @Override
    public Platform convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Platform.from(dbData);
    }
}
```

---

### 3. DeviceToken 엔티티 + Repository

패키지: `com.synapse.platform.notification.entity`, `com.synapse.platform.notification.repository`

**DeviceToken.java**
- `@Entity @Table(name = "device_tokens")`
- 필드: `UUID id`, `UUID tenantId`, `UUID userId`, `String token`, `Platform platform`, `boolean isActive`, `Instant createdAt`, `Instant updatedAt`
- `@Column(name = "tenant_id")`, `@Column(name = "user_id")`, `@Column(unique = true)`
- `@Convert(converter = PlatformConverter.class)` — platform 필드

**DeviceTokenRepository.java**

```java
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    long countByUserId(UUID userId);  // 5개 제한 — user 단위 (cross-tenant)

    List<DeviceToken> findByUserId(UUID userId);  // Step 7 준비용

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO device_tokens (id, tenant_id, user_id, token, platform, created_at, updated_at)
        VALUES (:id, :tenantId, :userId, :token, :platform, NOW(), NOW())
        ON CONFLICT (token) DO UPDATE
          SET tenant_id  = EXCLUDED.tenant_id,
              user_id    = EXCLUDED.user_id,
              updated_at = NOW()
        """, nativeQuery = true)
    void upsert(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId,
        @Param("token") String token,
        @Param("platform") String platform
    );
}
```

> `@Modifying(clearAutomatically = true, flushAutomatically = true)` 필수 — native UPSERT 후 JPA 1차 캐시 stale 방지

---

### 4. DeviceRegistrationLimitExceededException

패키지: `com.synapse.platform.notification.exception`

```java
public class DeviceRegistrationLimitExceededException extends BusinessException {
    public DeviceRegistrationLimitExceededException() {
        super("PLAT-NOTIFICATION-001", 409, "Device registration limit exceeded (max 5)");
    }
}
```

---

### 5. DeviceTokenService

패키지: `com.synapse.platform.notification.service`

의존성: `DeviceTokenRepository`, `UserApi`

**tenantId resolve — BillingService와 동일 패턴**

```java
private UUID resolveTenantId(UUID userId) {
    return userApi.findById(userId)
            .map(UserInfo::defaultTenantId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
}
```

> `UserApi`는 `com.synapse.platform.user.api.UserApi` (Spring Modulith NamedInterface)
> `UserInfo.defaultTenantId()` — `BillingService.resolveTenantId()` 참조

**register(UUID userId, String token, Platform platform)**

```
1. UUID tenantId = resolveTenantId(userId)
2. findByToken(token) → isNewToken 판별
3. isNewToken이면: countByUserId(userId) >= 5 → DeviceRegistrationLimitExceededException
4. upsert(UUID.randomUUID(), tenantId, userId, token, platform.getValue()) 실행
```

**unregister(UUID userId, UUID deviceId)**

```
1. findById(deviceId) → 없으면 EntityNotFoundException("Device not found")
2. deviceToken.getUserId().equals(userId) → false면 AccessDeniedException("Not owner")
3. deleteById(deviceId)
```

> `EntityNotFoundException`: `jakarta.persistence.EntityNotFoundException`
> `AccessDeniedException`: `org.springframework.security.access.AccessDeniedException`

---

### 6. DeviceTokenController

패키지: `com.synapse.platform.notification.controller`

**DTO**

패키지: `com.synapse.platform.notification.dto.request`, `com.synapse.platform.notification.dto.response`

```java
// DeviceTokenRequest.java
public record DeviceTokenRequest(
    @NotBlank String token,
    @NotNull Platform platform
) {}

// DeviceTokenResponse.java — WORKFLOW 5.6: (id, platform, is_active, createdAt)
public record DeviceTokenResponse(
    UUID id,
    String platform,
    boolean isActive,
    Instant createdAt
) {}
```

**Controller**

```java
@RestController
@RequestMapping("/api/v1/notifications/devices")
public class DeviceTokenController {

    @PostMapping
    public ResponseEntity<Void> register(
            Authentication authentication,
            @Valid @RequestBody DeviceTokenRequest request) {
        UUID userId = currentUserId(authentication);
        deviceTokenService.register(userId, request.token(), request.platform());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregister(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID userId = currentUserId(authentication);
        deviceTokenService.unregister(userId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        // BillingController와 동일 패턴
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
```

> POST 201: body 없음 (등록 성공 확인만)
> DELETE 204: body 없음

---

### 7. NotificationSecurityConfig + SecurityConfig @Order 추가

**수정**: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- 클래스에 `@Order(2)` 추가

**신규**: `src/main/java/com/synapse/platform/notification/config/NotificationSecurityConfig.java`

```java
@Configuration
@Order(1)
public class NotificationSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public NotificationSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain notificationFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/v1/notifications/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(
                    (req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
            .build();
    }
}
```

---

### 8. GlobalExceptionHandler — EntityNotFoundException → 404 추가

**수정**: `src/main/java/com/synapse/platform/global/exception/GlobalExceptionHandler.java`

`handleBusinessException` 아래에 추가:

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

---

### 9. 통합 테스트

파일 경로: `src/test/java/com/synapse/platform/notification/DeviceTokenIntegrationTest.java`

Testcontainers 설정 — 기존 `RefreshTokenServiceTest`와 동일 방식 적용.

**필수 테스트 시나리오**

| 테스트 메서드명 | 검증 내용 |
|----------------|----------|
| `register_newDevice_shouldReturn201` | 신규 토큰 등록 → 201 |
| `register_sameTokenForDifferentUser_shouldKeepOneRowAndMoveOwner` | 동일 토큰 재등록 시 user_id + tenant_id 교체, DB row 1개 유지 |
| `register_existingToken_shouldSkipLimitCheckAndUpsert` | 기존 토큰 재등록은 5개 제한 미적용 |
| `register_sixthNewDevice_shouldReturn409` | 6번째 신규 토큰 → 409 |
| `register_invalidPlatform_shouldReturn400` | `"MOBILE"` 입력 → 400 |
| `register_withoutJwt_shouldReturn401` | JWT 없음 → 401 |
| `unregister_ownDevice_shouldReturn204` | 본인 디바이스 삭제 → 204 |
| `unregister_otherUsersDevice_shouldReturn403` | 타인 디바이스 삭제 → 403 |
| `unregister_missingDevice_shouldReturn404` | 존재하지 않는 ID → 404 |

---

### 10. NotificationPlaceholder.java 삭제

```
src/main/java/com/synapse/platform/notification/NotificationPlaceholder.java 삭제
```

---

## 절대 금지

| 금지 항목 | 이유 |
|-----------|------|
| `firebase-admin` 의존성 추가 | Step 7에서 별도 추가 |
| `@Enumerated(EnumType.STRING)` 단독 사용 | 소문자 DB 저장 불가 |
| Application-level upsert (`findByToken + save`) | TOCTOU race condition |
| `SecurityConfig` `@Order` 없이 FilterChain 추가 | 충돌 발생 |
| `notification_preferences` 테이블/API 구현 | Step 7로 이관, ERD 충돌 존재 |

---

## 완료 기준 (Done When)

- [x] `POST /api/v1/notifications/devices` — 201 반환
- [x] `DELETE /api/v1/notifications/devices/{id}` — 204 / 403 / 404
- [x] `V27__create_device_tokens.sql` Flyway 마이그레이션 적용
- [x] `./gradlew test` 전체 통과
- [x] JaCoCo 신규 코드 라인 커버리지 80% 이상

## 첨부 파일

- `docs/ai/agent/worker.md`
- `docs/ai/current/CONTEXT.md`

## 기한

2026-05-19
