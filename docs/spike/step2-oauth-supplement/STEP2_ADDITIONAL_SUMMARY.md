# Step 2 추가 샘플링 정리

> 샘플링 완료 기준: 2026-05-13
> 기반: OAuth 샘플링(`SAMPLING_OAUTH.md`, `OAUTH_SUMMARY.md`) 결과물 위에 추가 검증
> 검증 항목: (1) Jackson 쿠키 직렬화 (2) Tenant 자동 생성 트랜잭션 (3) Flyway + PostgreSQL 마이그레이션

---

## 0. 결론 요약

| 항목 | 결과 | 비고 |
|------|------|------|
| Jackson 쿠키 직렬화 | ✅ 동작 확인 | DTO record + Base64 URL 인코딩, 332 bytes |
| 3-케이스 트랜잭션 (A/B/C) | ✅ 전체 통과 | 단일 `@Transactional`, 롤백 검증 포함 |
| GitHub email null 처리 | ✅ 확정 | 플레이스홀더 전략 (`{login}@github.placeholder`) |
| Flyway V1~V3, V16~V18 | ✅ 마이그레이션 성공 | pgvector/pgvector:pg16 Docker 이미지 |
| `ddl-auto=validate` | ✅ 통과 | Entity ↔ PostgreSQL 스키마 일치 확인 |
| `./gradlew test` (H2) | ✅ 전체 통과 | — |
| PostgreSQL 통합 테스트 | ✅ 전체 통과 | `@Tag("integration")` 별도 실행 |

---

## 1. 설계 결정 목록 (D-001 ~ D-005)

### D-001: OAuth2AuthorizationRequest 직렬화 방식

**결정**: DTO record + Jackson JSON + Base64 URL 인코딩

**근거**: `OAuth2AuthorizationRequest`는 Jackson 기본 설정으로 직렬화 불가 (불변 객체, 생성자 없음).
필요한 필드만 추출한 `OAuth2AuthorizationRequestDto` record를 도입하여 Jackson → Base64 URL 인코딩으로 처리.
Custom Serializer보다 유지보수가 쉽고, 필드 범위가 명시적이다.

```java
// 직렬화: request → DTO → JSON → Base64
String json = objectMapper.writeValueAsString(OAuth2AuthorizationRequestDto.from(request));
return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

// 역직렬화: Base64 → JSON → DTO → request
String json = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
return objectMapper.readValue(json, OAuth2AuthorizationRequestDto.class).toRequest();
```

**쿠키 크기**: 테스트 authorization request 기준 인코딩 후 332 bytes. 4 KB 제한 대비 여유 충분.

---

### D-002: UUID v7 생성 방식

**결정**: 애플리케이션 측 생성 — `com.github.f4b6a3:uuid-creator:5.3.3`

**근거**: PostgreSQL 16은 UUID v7 내장 함수 미지원 (PostgreSQL 17부터 지원).
애플리케이션 레이어에서 생성하면 DB 의존 없이 정렬 가능한 UUID를 확보할 수 있다.

```java
// 모든 Entity @PrePersist에서 동일하게 사용
@PrePersist
void prePersist() {
    if (id == null) {
        id = UuidCreator.getTimeOrderedEpoch();
    }
}
```

**의존성 추가**:
```kotlin
// build.gradle.kts
implementation("com.github.f4b6a3:uuid-creator:5.3.3")
```

---

### D-003: GitHub email null 처리

**결정**: 플레이스홀더 전략 — `{github_login}@github.placeholder`

| 컬럼 | 값 | 이유 |
|------|-----|------|
| `users.email` | `{login}@github.placeholder` | NOT NULL 제약, 고유성 보장 |
| `users.email_verified_at` | `NULL` | 미인증 상태 명시 |
| `oauth_identities.email` | `NULL` (provider 원본 그대로) | 실제 값 보존 |

**근거**: GitHub `/user/emails` API 추가 호출(옵션 A)은 API rate limit 및 scope 추가가 필요해 복잡도가 높다.
플레이스홀더 방식은 사용자가 나중에 실제 email을 등록하는 흐름과 자연스럽게 이어진다.

---

### D-004: oauth_identities 테이블 분리

**결정**: `users`에서 `provider`, `provider_id` 필드 제거 → `oauth_identities` 테이블로 분리

**근거**: 사용자 1명이 Google + GitHub 계정을 모두 연결하는 시나리오를 지원하기 위해 1:N 구조가 필요하다.
`users` 테이블 단일 provider 컬럼으로는 다중 provider 연결 불가.

---

### D-005: Username / Slug 자동 생성 규칙

**결정**: email 앞부분 기반 → 소문자 + 영숫자만 → 중복 시 `_{6자리 난수}` suffix

```java
// SlugGenerator.generate(email)
String base = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9]", "");
if (base.isBlank()) base = "user";
// 중복 시: base + "_" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
// 최대 10회 시도, 모두 실패 시 IllegalStateException
```

---

## 2. 확정 스키마

### 테이블 생성 순서 (FK 의존성 기준)

```
plan_quotas → tenants → users (default_tenant_id FK)
                      → oauth_identities (user_id FK)
                      → user_settings (user_id FK)
tenant_members (tenant_id FK → tenants, user_id FK → users)
```

### users

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255),
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    email_verified_at TIMESTAMPTZ,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    password_changed_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    default_tenant_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    anonymized_at TIMESTAMPTZ,
    CONSTRAINT fk_users_default_tenant FOREIGN KEY (default_tenant_id) REFERENCES tenants(id)
);

CREATE UNIQUE INDEX uq_users_email    ON users(email)    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_username ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_locked         ON users(locked_until) WHERE locked_until IS NOT NULL;
```

**주의**: `users` → `tenants` FK가 있으므로 Flyway에서 `tenants`를 먼저 생성해야 한다 (V2 → V3 순서).

### oauth_identities

```sql
CREATE TABLE oauth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_oauth_identities_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_oauth_provider_user ON oauth_identities(provider, provider_user_id);
CREATE INDEX idx_oauth_user_id            ON oauth_identities(user_id);
```

### tenants

```sql
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    plan VARCHAR(50) NOT NULL DEFAULT 'free',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    tenant_type VARCHAR(20) NOT NULL DEFAULT 'personal',
    region VARCHAR(20) NOT NULL DEFAULT 'ap-northeast-2',
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_tenants_plan FOREIGN KEY (plan) REFERENCES plan_quotas(plan)
);

CREATE UNIQUE INDEX uq_tenants_slug   ON tenants(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status       ON tenants(status) WHERE status != 'active';
CREATE INDEX idx_tenants_plan         ON tenants(plan);
```

### tenant_members

```sql
CREATE TABLE tenant_members (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    invited_by UUID,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT fk_tenant_members_tenant     FOREIGN KEY (tenant_id)  REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_members_user       FOREIGN KEY (user_id)    REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_tenant_members_invited_by FOREIGN KEY (invited_by) REFERENCES users(id)
);
```

**주의**: `tenant_members.user_id → users` FK는 users 테이블 생성 후 V3에서 `ALTER TABLE`로 추가한다.

### user_settings

```sql
CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY,
    locale VARCHAR(10) NOT NULL DEFAULT 'ko-KR',
    theme VARCHAR(20) NOT NULL DEFAULT 'system',
    srs_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    editor_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    notification_prefs JSONB NOT NULL DEFAULT '{}'::jsonb,
    pii_redaction_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

---

## 3. 3-케이스 처리 플로우

`CustomOAuth2UserService.loadUser()` 내부 `resolveUser()` 로직:

```
loadUser() 진입
  ↓
① oauthIdentityRepo.findByProviderAndProviderUserId() 조회
   → 존재: Case A — 기존 user 반환 (프로필 갱신 없음, 로그인만)
   → 없음: ↓
② email이 non-null인 경우: userRepo.findByEmail() 조회
   → 존재: Case B — oauthIdentities에만 신규 레코드 추가 (계정 연결)
   → 없음 (또는 email null): ↓
③ Case C — 신규 가입 (단일 @Transactional)
   tenants → users (default_tenant_id 설정) → oauth_identities
   → tenant_members → user_settings 순서로 저장
```

**Case C 저장 순서 주의**:
```java
// Tenant를 먼저 저장해야 User.default_tenant_id FK가 유효하다
Tenant tenant = tenantRepository.save(Tenant.ofPersonal(displayName, slug));
User user = User.ofOAuth(email, slug, displayName, avatarUrl);
user.updateDefaultTenantId(tenant.getId());
User savedUser = userRepository.save(user);
// 이후 나머지 저장
oauthIdentityRepository.save(OAuthIdentity.of(savedUser, ...));
tenantMemberRepository.save(TenantMember.ofOwner(tenant.getId(), savedUser.getId()));
userSettingsRepository.save(UserSettings.defaultFor(savedUser.getId()));
```

---

## 4. Flyway 구성

### 마이그레이션 파일 목록

| 파일 | 내용 |
|------|------|
| `V1__init_extensions.sql` | uuid-ossp, pg_trgm, pgcrypto, vector, btree_gin 확장 |
| `V2__init_tenants_and_plans.sql` | plan_quotas + 시드 데이터, tenants, tenant_members |
| `V3__init_users_and_auth.sql` | users, oauth_identities, user_settings + FK 마무리 |
| `V16__enable_rls_policies.sql` | tenants RLS 활성화 + tenant_isolation 정책 |
| `V17__create_triggers.sql` | trg_set_updated_at() + users/tenants 트리거 |
| `V18__seed_plan_quotas.sql` | 빈 파일 (V2에서 시드 완료) |

> V4~V15는 이후 Step에서 추가. Flyway는 순번 건너뛰기 허용.

### Docker 이미지

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16   # pgvector 확장 사전 설치 포함
    environment:
      POSTGRES_DB: synapsedb
      POSTGRES_USER: synapse
      POSTGRES_PASSWORD: synapse123
    ports:
      - "5432:5432"
```

### 환경별 설정 분리

```
src/main/resources/application.yml       공통 설정 (flyway.enabled=true, ddl-auto=validate)
src/main/resources/application-local.yml PostgreSQL DataSource (로컬 개발 전용)
src/test/resources/application.yml       H2 DataSource + flyway.enabled=false + ddl-auto=create-drop
```

### 필수 의존성 추가

```kotlin
// build.gradle.kts
implementation("com.github.f4b6a3:uuid-creator:5.3.3")
runtimeOnly("org.postgresql:postgresql")
runtimeOnly("org.flywaydb:flyway-database-postgresql")  // PostgreSQL 16 인식 필수
testRuntimeOnly("com.h2database:h2")
```

---

## 5. RLS 동작 확인

```sql
-- V16 적용 내용
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenants
  USING (id = current_setting('app.tenant_id', TRUE)::uuid);
```

**`current_setting(name, missing_ok)` 두 번째 인자**:
`TRUE`로 설정하면 미설정 시 NULL 반환 (에러 없음).
`FALSE`(기본값)이면 미설정 시 예외 발생 — 반드시 `TRUE`를 사용해야 한다.

**동작 확인 결과**:
- `app.tenant_id` 미설정 상태에서 `SELECT` 시 행 반환 없음 (에러 아님, 빈 결과)
- Spring DataSource는 connection pool을 공유하므로 요청마다 `SET LOCAL app.tenant_id = ?` 주입 패턴 필요 (Step 3 이후 구현)

---

## 6. Partial Index 동작

소규모 테이블에서는 PostgreSQL planner가 다른 인덱스를 선택하는 경우가 있다.
통합 테스트에서 Partial Index 사용을 검증할 때는 샘플 row를 먼저 삽입하고 `ANALYZE`를 수행해야 한다.

```java
// PostgreSQLMigrationTest 패턴
@Test
void partialIndexWorksForEmail() {
    // 샘플 row 삽입
    jdbcTemplate.update("INSERT INTO users (id, email, username, ...) VALUES (?, ?, ?, ...)");
    // planner 통계 갱신
    jdbcTemplate.execute("ANALYZE users");
    // EXPLAIN 검증
    String plan = jdbcTemplate.queryForObject("EXPLAIN SELECT * FROM users WHERE email = ?", String.class, email);
    assertThat(plan).contains("Index Scan");
}
```

---

## 7. Entity ↔ JSONB 매핑

PostgreSQL `JSONB` 타입과 Hibernate Entity 매핑 시 `@Column(columnDefinition = "jsonb")`만으로는 부족하다.
Hibernate 6+에서는 `@JdbcTypeCode(SqlTypes.JSON)` 또는 `@Type(JsonType.class)`를 함께 사용해야 한다.

H2 테스트 환경은 JSONB 미지원 → Entity 필드를 `String`으로 선언하고 `@Column(columnDefinition = "jsonb")`는 PostgreSQL 전용 DDL 힌트로만 동작한다.

```java
@Column(columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
private String metadata = "{}";
```

---

## 8. plan_quotas 시드 데이터 수정 사항

ERD 원본의 `enterprise` 플랜 `price_usd_monthly = NULL` → `price_usd_monthly NOT NULL` 제약 위반.
**`0.00`으로 수정** (별도 협의로 확정 필요):

```sql
('enterprise', 'Enterprise', 0.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 '{"graphView":true,"semanticSearch":true,"sharedDecks":true,"ssoEnabled":true,"auditLog":true}',
 true, NOW())
```

---

## 9. 테스트 결과

### H2 단위/통합 테스트 (`./gradlew test`)

| 테스트 클래스 | 테스트 수 | 결과 |
|-------------|---------|------|
| `HttpCookieOAuth2AuthorizationRequestRepositoryTest` | 4 | ✅ 전체 통과 |
| `CustomOAuth2UserServiceTest` (Case A/B/C + 롤백) | 4 | ✅ 전체 통과 |
| `SlugGeneratorTest` | 3 | ✅ 전체 통과 |
| `OAuthAttributesTest` | 4 | ✅ 전체 통과 |
| `OAuth2LoginIntegrationTest` | 3 | ✅ 전체 통과 |
| `OAuthSignupRollbackIntegrationTest` | 1 | ✅ 전체 통과 |
| 기존 테스트 (OAuth 1차 샘플링 분) | 19 | ✅ 회귀 없음 |

### PostgreSQL 통합 테스트 (`./gradlew test -Dgroups=integration`)

```
./gradlew "-Dspring.profiles.active=local" "-Dgroups=integration" test \
  --tests com.synapse.platform.auth.PostgreSQLMigrationTest
```

| 테스트 | 결과 |
|--------|------|
| `flywayMigrationApplied` | ✅ |
| `partialIndexWorksForEmail` | ✅ |
| `inetTypeInsertSelect` | ✅ |
| `rlsBlocksWithoutTenantId_shouldNotReturnRows` | ✅ |

---

## 10. 본 프로젝트 적용 체크리스트

### 의존성

- [ ] `uuid-creator:5.3.3` 추가
- [ ] `flyway-database-postgresql` 추가 (PostgreSQL 16 인식)
- [ ] `org.postgresql:postgresql` runtimeOnly 추가

### 코드

- [ ] `OAuth2AuthorizationRequestDto` record 복사
- [ ] `HttpCookieOAuth2AuthorizationRequestRepository` 교체 (Java 직렬화 → Jackson)
- [ ] `SecurityConfig.cookieAuthorizationRequestRepository()` Bean에 `ObjectMapper` 주입
- [ ] `User` 엔티티 `provider`/`providerId` 필드 제거 + ERD 필드 추가
- [ ] `OAuthIdentity`, `Tenant`, `TenantMember`, `TenantMemberId`, `UserSettings` 엔티티 추가
- [ ] `OAuthIdentityRepository`, `TenantRepository`, `TenantMemberRepository`, `UserSettingsRepository` 추가
- [ ] `SlugGenerator` 추가
- [ ] `CustomOAuth2UserService` 3-케이스 로직으로 교체
- [ ] 모든 Entity `@PrePersist`에 `UuidCreator.getTimeOrderedEpoch()` 적용

### Flyway

- [ ] 기존 `V1__create_users_table.sql` 삭제
- [ ] V1~V3, V16~V18 신규 파일 추가
- [ ] `application.yml` 공통/로컬/테스트 분리
- [ ] `enterprise` 플랜 `price_usd_monthly = 0.00` 확인

### 주의사항

- [ ] Case C에서 Tenant를 User보다 먼저 저장 (FK 순서 의존성)
- [ ] `current_setting('app.tenant_id', TRUE)` — 두 번째 인자 `TRUE` 필수
- [ ] JSONB 컬럼에 `@JdbcTypeCode(SqlTypes.JSON)` 병기
- [ ] Partial Index 테스트 시 `ANALYZE` 선행 필요
