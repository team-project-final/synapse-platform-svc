# Refresh Token Multi Device Sessions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow up to five active refresh tokens per user and evict the oldest session by FIFO when the limit is exceeded.

**Architecture:** Keep refresh token raw values out of storage by continuing to persist only SHA-256 hashes. Replace the current user-wide Redis key (`refresh:{userId}`) with token-specific keys (`refresh:{userId}:{tokenHash}`), so each device session can be validated, rotated, and deleted independently. Rotation will replace only the presented refresh token while preserving the original device fingerprint and IP address.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Redis via `StringRedisTemplate`, Flyway, JUnit 5, AssertJ, Mockito/MockMvc, Testcontainers.

---

## File Map

- Create: `src/main/resources/db/migration/V28__allow_multiple_refresh_tokens.sql`
  - Drops the user-level unique index introduced by `V23__add_refresh_tokens_user_unique.sql`.
- Modify: `src/main/java/com/synapse/platform/auth/repository/RefreshTokenRepository.java`
  - Adds ordered lookup and token-specific lookup/delete methods.
- Modify: `src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java`
  - Changes Redis key shape, max-session enforcement, cache fallback, delete-all, and rotation behavior.
- Modify: `src/main/java/com/synapse/platform/auth/controller/AuthController.java`
  - Passes the old refresh token to `rotate`.
- Modify: `src/test/java/com/synapse/platform/auth/service/RefreshTokenServiceTest.java`
  - Updates Redis key assertions, replaces single-session invalidation test with max-five/FIFO coverage, and verifies rotation metadata preservation.
- Modify: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`
  - Verifies the controller calls the new `rotate(userId, oldRefreshToken, newRefreshToken)` signature.

---

### Task 1: Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V28__allow_multiple_refresh_tokens.sql`

- [ ] **Step 1: Add migration**

```sql
DROP INDEX IF EXISTS uq_refresh_tokens_user_id;
```

- [ ] **Step 2: Verify Flyway migration order**

Run:

```powershell
Get-ChildItem -LiteralPath src\main\resources\db\migration -Filter V*.sql | Sort-Object Name | Select-Object -ExpandProperty Name
```

Expected: `V28__allow_multiple_refresh_tokens.sql` appears after `V27__create_device_tokens.sql`; no duplicate `V28`.

---

### Task 2: Repository Contract

**Files:**
- Modify: `src/main/java/com/synapse/platform/auth/repository/RefreshTokenRepository.java`

- [ ] **Step 1: Add imports**

```java
import java.util.List;
import java.util.Optional;
```

- [ ] **Step 2: Add repository methods**

Add these methods without removing existing methods used by tests or service code:

```java
List<RefreshToken> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

Optional<RefreshToken> findByUserIdAndTokenHash(UUID userId, String tokenHash);

@Modifying
@Query("DELETE FROM RefreshToken r WHERE r.userId = :userId AND r.tokenHash = :tokenHash")
void deleteByUserIdAndTokenHash(UUID userId, String tokenHash);
```

- [ ] **Step 3: Run compilation check**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: compile fails only if later service signature changes have not been applied yet; after Task 3 it must pass.

---

### Task 3: RefreshTokenService Multi-Session Logic

**Files:**
- Modify: `src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java`

- [ ] **Step 1: Add constants and imports**

```java
import java.util.List;
import java.util.Set;
```

```java
private static final int MAX_ACTIVE_SESSIONS = 5;
```

- [ ] **Step 2: Replace `save` behavior**

Change `save(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress)` so it no longer calls `repository.deleteAllByUserId(userId)`.

Expected implementation shape:

```java
@Transactional
public void save(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress) {
    evictOldestSessionsIfNecessary(userId);
    store(userId, refreshToken, deviceFingerprint, ipAddress);
}
```

- [ ] **Step 3: Change Redis storage to token-specific key**

Expected `store` Redis write:

```java
String tokenHash = RefreshToken.hash(refreshToken);
repository.save(entity);
afterCommit(() -> redisTemplate.opsForValue()
        .set(key(userId, tokenHash), tokenHash, REFRESH_TOKEN_TTL));
```

- [ ] **Step 4: Add FIFO eviction helper**

Expected behavior: before saving the new token, if the user already has five or more tokens, delete only enough oldest tokens so the new save leaves five active sessions.

```java
private void evictOldestSessionsIfNecessary(UUID userId) {
    List<RefreshToken> tokens = repository.findAllByUserIdOrderByCreatedAtAsc(userId);
    int tokensToDelete = Math.max(0, tokens.size() - MAX_ACTIVE_SESSIONS + 1);
    tokens.stream()
            .limit(tokensToDelete)
            .forEach(token -> deleteToken(userId, token.getTokenHash()));
}
```

- [ ] **Step 5: Add token-specific delete helper**

```java
private void deleteToken(UUID userId, String tokenHash) {
    repository.deleteByUserIdAndTokenHash(userId, tokenHash);
    afterCommit(() -> redisTemplate.delete(key(userId, tokenHash)));
}
```

- [ ] **Step 6: Update `delete(UUID userId)`**

Expected behavior: delete all DB rows for the user and delete all Redis keys matching `refresh:{userId}:*`.

```java
@Transactional
public void delete(UUID userId) {
    repository.deleteAllByUserId(userId);
    afterCommit(() -> {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    });
}
```

- [ ] **Step 7: Replace rotate signature and behavior**

Expected signature:

```java
@Transactional
public void rotate(UUID userId, String oldRefreshToken, String newRefreshToken)
```

Expected logic:

```java
String oldTokenHash = RefreshToken.hash(oldRefreshToken);
RefreshToken existingToken = repository.findByUserIdAndTokenHash(userId, oldTokenHash)
        .orElseThrow(() -> new IllegalArgumentException("Refresh token session not found"));
String deviceFingerprint = existingToken.getDeviceFingerprint();
String ipAddress = existingToken.getIpAddress();
deleteToken(userId, oldTokenHash);
store(userId, newRefreshToken, deviceFingerprint, ipAddress);
```

- [ ] **Step 8: Update `isValid`**

Expected behavior: compute token hash first, check `refresh:{userId}:{tokenHash}`, and fall back to DB if cache misses.

```java
public boolean isValid(UUID userId, String token) {
    String tokenHash = RefreshToken.hash(token);
    String cachedHash = redisTemplate.opsForValue().get(key(userId, tokenHash));
    if (cachedHash != null) {
        return cachedHash.equals(tokenHash);
    }
    return repository.existsByUserIdAndTokenHashAndExpiresAtAfter(userId, tokenHash, Instant.now());
}
```

- [ ] **Step 9: Replace key helper**

```java
private String key(UUID userId, String tokenHash) {
    return KEY_PREFIX + userId + ":" + tokenHash;
}
```

- [ ] **Step 10: Compile**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: fails until `AuthController` is updated for the new `rotate` signature; passes after Task 4.

---

### Task 4: AuthController Rotation Call

**Files:**
- Modify: `src/main/java/com/synapse/platform/auth/controller/AuthController.java`

- [ ] **Step 1: Pass old refresh token into rotate**

Replace:

```java
refreshTokenService.rotate(userId, newRefreshToken);
```

With:

```java
refreshTokenService.rotate(userId, refreshToken, newRefreshToken);
```

- [ ] **Step 2: Compile**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: PASS.

---

### Task 5: Service Tests

**Files:**
- Modify: `src/test/java/com/synapse/platform/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: Update Redis key helper**

Replace the existing helper:

```java
private static String refreshKey(UUID userId) {
    return "refresh:" + userId;
}
```

With:

```java
private static String refreshKey(UUID userId, String rawToken) {
    return "refresh:" + userId + ":" + RefreshToken.hash(rawToken);
}
```

- [ ] **Step 2: Update `save_storesHashInDbAndRedis` assertion**

Replace:

```java
assertThat(redisTemplate.opsForValue().get(refreshKey(userId))).isEqualTo(tokenHash);
```

With:

```java
assertThat(redisTemplate.opsForValue().get(refreshKey(userId, rawToken))).isEqualTo(tokenHash);
```

- [ ] **Step 3: Replace old single-session test**

Replace `save_secondTokenInvalidatesOldToken()` with:

```java
@Test
void save_sixthTokenEvictsOldestTokenAndKeepsFiveActiveSessions() {
    UUID userId = createUser().getId();

    refreshTokenService.save(userId, "token-1", null, null);
    refreshTokenService.save(userId, "token-2", null, null);
    refreshTokenService.save(userId, "token-3", null, null);
    refreshTokenService.save(userId, "token-4", null, null);
    refreshTokenService.save(userId, "token-5", null, null);
    refreshTokenService.save(userId, "token-6", null, null);

    assertThat(repository.countByUserId(userId)).isEqualTo(5);
    assertThat(refreshTokenService.isValid(userId, "token-1")).isFalse();
    assertThat(refreshTokenService.isValid(userId, "token-2")).isTrue();
    assertThat(refreshTokenService.isValid(userId, "token-3")).isTrue();
    assertThat(refreshTokenService.isValid(userId, "token-4")).isTrue();
    assertThat(refreshTokenService.isValid(userId, "token-5")).isTrue();
    assertThat(refreshTokenService.isValid(userId, "token-6")).isTrue();
    assertThat(redisTemplate.opsForValue().get(refreshKey(userId, "token-1"))).isNull();
}
```

- [ ] **Step 4: Update cache miss test Redis deletion**

Replace:

```java
redisTemplate.delete(refreshKey(userId));
```

With:

```java
redisTemplate.delete(refreshKey(userId, rawToken));
```

- [ ] **Step 5: Replace rotate test**

Replace `rotate_replacesOldToken()` with:

```java
@Test
void rotate_replacesOnlyPresentedTokenAndPreservesMetadata() {
    UUID userId = createUser().getId();

    refreshTokenService.save(userId, "old-token", "device-fp", "127.0.0.1");
    refreshTokenService.save(userId, "other-token", "other-device", "10.0.0.1");
    refreshTokenService.rotate(userId, "old-token", "new-token");

    assertThat(repository.countByUserId(userId)).isEqualTo(2);
    assertThat(refreshTokenService.isValid(userId, "old-token")).isFalse();
    assertThat(refreshTokenService.isValid(userId, "new-token")).isTrue();
    assertThat(refreshTokenService.isValid(userId, "other-token")).isTrue();
    assertThat(repository.findByUserIdAndTokenHash(userId, RefreshToken.hash("new-token")))
            .get()
            .satisfies(token -> {
                assertThat(token.getDeviceFingerprint()).isEqualTo("device-fp");
                assertThat(token.getIpAddress()).isEqualTo("127.0.0.1");
            });
}
```

- [ ] **Step 6: Update delete test Redis assertion**

Replace:

```java
assertThat(redisTemplate.opsForValue().get(refreshKey(userId))).isNull();
```

With:

```java
assertThat(redisTemplate.opsForValue().get(refreshKey(userId, rawToken))).isNull();
```

- [ ] **Step 7: Run service test**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.service.RefreshTokenServiceTest"
```

Expected: PASS. If Testcontainers cannot start Docker, record the environment failure and run compile plus controller unit tests.

---

### Task 6: Controller Test

**Files:**
- Modify: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`

- [ ] **Step 1: Update verification**

Replace:

```java
verify(refreshTokenService).rotate(userId, "new-refresh-token");
```

With:

```java
verify(refreshTokenService).rotate(userId, "old-refresh-token", "new-refresh-token");
```

- [ ] **Step 2: Run controller test**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.AuthControllerTest"
```

Expected: PASS.

---

### Task 7: Required Verification

**Files:**
- No new file changes unless verification exposes a defect.

- [ ] **Step 1: Run requested auth test suite**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.*"
```

Expected: PASS.

- [ ] **Step 2: Run Modulith structure check if auth tests pass**

Run:

```powershell
.\gradlew.bat test --tests "*ModuleStructureTest"
```

Expected: PASS.

- [ ] **Step 3: Done When checklist**

Verify:

- `V28__allow_multiple_refresh_tokens.sql` drops `uq_refresh_tokens_user_id`.
- `RefreshTokenRepository` has ordered lookup and token-specific lookup/delete methods.
- Redis keys use `refresh:{userId}:{tokenHash}`.
- `save()` keeps at most five active sessions and evicts FIFO from DB and Redis.
- `rotate()` replaces only the old presented token and preserves metadata.
- `AuthController` calls the new rotate signature with old and new refresh tokens.
- `RefreshTokenServiceTest` and `AuthControllerTest` cover the changed behavior.
- `.\gradlew.bat test --tests "com.synapse.platform.auth.*"` result is recorded.

---

## Risks And Notes

- `StringRedisTemplate.keys(...)` is acceptable here because the HANDOFF explicitly requires deleting `refresh:{userId}:*` on full user logout/delete. Keep its use scoped to `delete(UUID userId)`.
- `RefreshToken.hash(rawToken)` remains the only value stored in DB/Redis; raw refresh tokens are used only as method inputs.
- The existing `token_hash` unique constraint remains valid because a raw token should not be reused across sessions.
- The plan intentionally does not alter JWT signing behavior, token TTL, OAuth flow, or logout endpoint design because they are out of scope.

---

# Refresh Token Review Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the post-review correctness gaps in refresh token multi-session handling: concurrent session creation must still cap active sessions at five, and replay/race failures during rotation must return 401 instead of 500.

**Architecture:** Serialize all per-user refresh-token mutations (`save`, `rotate`, `delete`) with a PostgreSQL transaction-scoped advisory lock acquired through `JdbcTemplate`. Keep the existing token-hash storage model and Redis key shape. Convert stale/missing refresh-token sessions during rotation into `UnauthorizedTokenException`, preserving the API contract that invalid refresh attempts are authentication failures.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Spring JDBC `JdbcTemplate`, PostgreSQL advisory locks, Redis via `StringRedisTemplate`, JUnit 5, AssertJ, Mockito/MockMvc, Testcontainers.

---

## Review Findings To Address

- [P1] `src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java:40`
  - Problem: `save()` reads the current sessions, deletes old sessions, then inserts the new session without a user-scoped lock. Concurrent refresh-token creation can let two transactions both observe fewer than five sessions and insert, leaving more than five active sessions.
  - Fix direction: acquire a transaction-scoped user-specific lock before FIFO eviction and insert.

- [P2] `src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java:82`
  - Problem: `rotate()` throws `IllegalArgumentException` when the old token session disappears between controller validation and service rotation. `GlobalExceptionHandler` maps that to 500, but this is an auth failure/replay race and should be 401.
  - Fix direction: throw `UnauthorizedTokenException` from `rotate()` when the old session is missing, and cover it at service and controller level.

---

## Follow-Up File Map

- Create: `src/main/java/com/synapse/platform/auth/service/RefreshTokenSessionLock.java`
  - Owns the PostgreSQL advisory lock call for per-user refresh-token mutation serialization.
- Modify: `src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java`
  - Injects `RefreshTokenSessionLock`, acquires the lock in `save`, `rotate`, and `delete`, and throws `UnauthorizedTokenException` for missing old refresh-token sessions.
- Modify: `src/test/java/com/synapse/platform/auth/service/RefreshTokenServiceTest.java`
  - Adds concurrent-save cap coverage and missing-old-token rotation coverage.
- Modify: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`
  - Adds a controller regression test proving a rotation race returns 401.

---

### Follow-Up Task 1: Add Transaction-Scoped Session Lock

**Files:**
- Create: `src/main/java/com/synapse/platform/auth/service/RefreshTokenSessionLock.java`

- [ ] **Step 1: Create lock component**

```java
package com.synapse.platform.auth.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenSessionLock {

    private static final String LOCK_NAMESPACE = "refresh_tokens";

    private final JdbcTemplate jdbcTemplate;

    RefreshTokenSessionLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void acquire(UUID userId) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                Void.class,
                LOCK_NAMESPACE,
                userId.toString());
    }
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: PASS if `JdbcTemplate` is available through current Spring dependencies.

---

### Follow-Up Task 2: Apply Lock And Auth Failure Semantics

**Files:**
- Modify: `src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java`

- [ ] **Step 1: Add import**

```java
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
```

- [ ] **Step 2: Inject lock component**

Replace the constructor and fields with this shape:

```java
private final RefreshTokenRepository repository;
private final StringRedisTemplate redisTemplate;
private final RefreshTokenSessionLock sessionLock;

public RefreshTokenService(
        RefreshTokenRepository repository,
        StringRedisTemplate redisTemplate,
        RefreshTokenSessionLock sessionLock) {
    this.repository = repository;
    this.redisTemplate = redisTemplate;
    this.sessionLock = sessionLock;
}
```

- [ ] **Step 3: Lock `save` before FIFO check**

```java
@Transactional
public void save(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress) {
    sessionLock.acquire(userId);
    evictOldestSessionsIfNecessary(userId);
    store(userId, refreshToken, deviceFingerprint, ipAddress);
}
```

- [ ] **Step 4: Lock `delete` before DB/Redis deletion registration**

```java
@Transactional
public void delete(UUID userId) {
    sessionLock.acquire(userId);
    repository.deleteAllByUserId(userId);
    afterCommit(() -> {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    });
}
```

- [ ] **Step 5: Lock `rotate` and convert missing old session to 401 domain exception**

```java
@Transactional
public void rotate(UUID userId, String oldRefreshToken, String newRefreshToken) {
    sessionLock.acquire(userId);
    String oldTokenHash = RefreshToken.hash(oldRefreshToken);
    RefreshToken existingToken = repository.findByUserIdAndTokenHash(userId, oldTokenHash)
            .orElseThrow(() -> new UnauthorizedTokenException("Refresh token does not match stored token"));
    String deviceFingerprint = existingToken.getDeviceFingerprint();
    String ipAddress = existingToken.getIpAddress();
    deleteToken(userId, oldTokenHash);
    store(userId, newRefreshToken, deviceFingerprint, ipAddress);
}
```

- [ ] **Step 6: Compile**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: PASS.

---

### Follow-Up Task 3: Add Service Regression Tests

**Files:**
- Modify: `src/test/java/com/synapse/platform/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: Add imports**

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
```

- [ ] **Step 2: Add missing-old-token rotate test**

```java
@Test
void rotate_missingOldToken_shouldThrowUnauthorizedTokenException() {
    UUID userId = createUser().getId();

    assertThatThrownBy(() -> refreshTokenService.rotate(userId, "missing-token", "new-token"))
            .isInstanceOf(UnauthorizedTokenException.class);
}
```

- [ ] **Step 3: Add concurrent save cap test**

```java
@Test
void save_concurrentSixthTokens_shouldKeepAtMostFiveActiveSessions() throws Exception {
    UUID userId = createUser().getId();
    CountDownLatch ready = new CountDownLatch(6);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(6);

    try {
        List<Future<?>> futures = IntStream.rangeClosed(1, 6)
                .mapToObj(index -> executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    refreshTokenService.save(userId, "concurrent-token-" + index, null, null);
                    return null;
                }))
                .toList();

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        assertThat(repository.countByUserId(userId)).isLessThanOrEqualTo(5);
    } finally {
        executor.shutdownNow();
    }
}
```

- [ ] **Step 4: Run service test and verify RED/GREEN**

Run before implementation if following strict TDD:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.service.RefreshTokenServiceTest"
```

Expected before implementation: missing-old-token test fails with `IllegalArgumentException`, and concurrent save may exceed five depending on timing.

Run after implementation:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.service.RefreshTokenServiceTest"
```

Expected after implementation: PASS.

---

### Follow-Up Task 4: Add Controller Regression Test

**Files:**
- Modify: `src/test/java/com/synapse/platform/auth/AuthControllerTest.java`

- [ ] **Step 1: Add static import**

```java
import static org.mockito.Mockito.doThrow;
```

- [ ] **Step 2: Add rotate race test**

```java
@Test
void refresh_tokenRotatedAfterValidation_shouldReturnUnauthorizedProblem() throws Exception {
    // Given
    UUID userId = UUID.randomUUID();
    given(jwtTokenProvider.validateRefreshToken("old-refresh-token")).willReturn(true);
    given(jwtTokenProvider.getUserId("old-refresh-token")).willReturn(userId);
    given(refreshTokenService.isValid(userId, "old-refresh-token")).willReturn(true);
    given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER"))).willReturn("new-access-token");
    given(jwtTokenProvider.createRefreshToken(userId)).willReturn("new-refresh-token");
    doThrow(new UnauthorizedTokenException("Refresh token does not match stored token"))
            .when(refreshTokenService)
            .rotate(userId, "old-refresh-token", "new-refresh-token");

    // When & Then
    mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", "old-refresh-token"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("PLAT-002"));
}
```

- [ ] **Step 3: Run controller test**

Run:

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.AuthControllerTest"
```

Expected: PASS after `rotate()` throws `UnauthorizedTokenException`.

---

### Follow-Up Task 5: Required Verification

**Files:**
- No code changes unless verification exposes a defect.

- [ ] **Step 1: Run targeted service tests**

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.service.RefreshTokenServiceTest"
```

Expected: PASS.

- [ ] **Step 2: Run targeted controller tests**

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.AuthControllerTest"
```

Expected: PASS.

- [ ] **Step 3: Run requested auth suite**

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.*"
```

Expected: PASS.

- [ ] **Step 4: Run Modulith verification**

```powershell
.\gradlew.bat test --tests "*ModuleStructureTest"
```

Expected: PASS.

- [ ] **Step 5: Run full test suite**

```powershell
.\gradlew.bat test
```

Expected: PASS.

---

## Follow-Up Done When

- [ ] Per-user refresh-token mutations are serialized inside the active database transaction.
- [ ] Concurrent refresh-token creation cannot leave more than five active sessions for one user.
- [ ] `rotate()` returns the auth-domain 401 error path when the old session disappears after validation.
- [ ] Existing raw-token storage prohibition remains intact: DB and Redis store only token hashes.
- [ ] Redis keys remain `refresh:{userId}:{tokenHash}`.
- [ ] Service and controller regression tests cover both review findings.
- [ ] `.\gradlew.bat test --tests "com.synapse.platform.auth.*"` passes.
- [ ] `.\gradlew.bat test --tests "*ModuleStructureTest"` passes.
