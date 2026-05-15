# SAMPLING — Step 2 추가 검증 (OAuth 샘플링 이후)

> **목적**: OAuth 샘플링(`docs/spike/OAuth/`)에서 검증되지 않은 3가지 항목을 추가로 검증한다.
> 본 문서를 전달받은 Agent는 별도 샘플 프로젝트에서 아래 항목을 실제로 동작시키고 결과를 기록한다.

---

## 전제 — OAuth 샘플링에서 이미 확정된 것

아래 항목은 재검증 불필요. 샘플링 결과(`SAMPLING_OAUTH.md`, `OAUTH_SUMMARY.md`) 기준으로 확정됨.

- Spring Security OAuth2 Client 의존성 및 설정
- `CustomOAuth2UserService` 구현 패턴 (Google `sub` / GitHub `id`)
- `SecurityFilterChain` 구조 (STATELESS + CSRF disable + state 쿠키)
- `OAuth2SuccessHandler` A안 채택 (userId redirect, JWT는 Step 3)
- `ApplicationModules.verify()` 통과 패키지 구조
- 통합 테스트 패턴 (`@SpringBootTest` + `MockMvc` + `oauth2Login()`)

---

## 샘플링 환경

| 항목          | 내용                                                   |
| ------------- | ------------------------------------------------------ |
| 기반 프로젝트 | synapse-platform-svc 복사본 (OAuth 샘플링 결과물 기반) |
| 기술 스택     | Spring Boot 4.0.0 + Java 21 + Spring Modulith 1.3.0    |
| DB (운영)     | PostgreSQL 16 (Docker)                                 |
| DB (테스트)   | H2 in-memory                                           |
| 빌드          | Gradle (Kotlin DSL)                                    |

---

## 검증 항목 1 — Jackson 기반 쿠키 직렬화

### 배경

OAuth 샘플링에서 `HttpCookieOAuth2AuthorizationRequestRepository`를 Java 직렬화(`ObjectOutputStream`)로 구현했다.
Director 코드 리뷰에서 **역직렬화 공격 취약점**으로 식별되어 본 프로젝트 적용 전 Jackson JSON 직렬화로 교체가 필수다.

### 목표

`OAuth2AuthorizationRequest` 객체를 Jackson으로 직렬화/역직렬화해서 쿠키에 저장하고 OAuth2 플로우가 정상 동작하는지 확인한다.

### 검증 항목

- [ ] `OAuth2AuthorizationRequest`가 Jackson으로 직렬화 가능한지 확인
  - `OAuth2AuthorizationRequest`는 Jackson 기본 설정으로는 직렬화 불가 (생성자 없음)
  - Custom Serializer/Deserializer 작성 필요 여부 판단
  - 또는 필요 필드만 추출해서 DTO로 변환 후 직렬화하는 방식 검토
- [ ] 직렬화된 값이 쿠키 크기 제한(4KB) 이내인지 확인
- [ ] 역직렬화 후 `OAuth2AuthorizationRequest` 재구성 시 `state`, `redirectUri`, `additionalParameters` 값 동일성 검증
- [ ] 쿠키 보안 속성 동시 적용 확인
  - `HttpOnly=true`
  - `Secure=true` (HTTPS) — 샘플링 환경에서는 HTTP이므로 `false`로 테스트, 속성 코드만 확인
  - `SameSite=Lax`
  - `Max-Age=180`
- [ ] OAuth2 플로우 end-to-end 동작 확인 (state 생성 → 쿠키 저장 → 콜백에서 읽기 → state 검증 → 쿠키 삭제)

### 보고 항목

```
1. 채택한 직렬화 방식 (Custom Serializer vs DTO 변환 vs 다른 방법)
2. 최종 구현 코드 (`HttpCookieOAuth2AuthorizationRequestRepository` 전체)
3. 쿠키 크기 측정값 (bytes)
4. OAuth2 플로우 통합 테스트 통과 여부
5. 발견된 문제점 및 해결 방법
```

### 성공 기준

- [ ] Java 직렬화 코드 없음 (ObjectOutputStream, ObjectInputStream 미사용)
- [ ] OAuth2 플로우 통합 테스트 통과
- [ ] 쿠키 크기 4KB 이내

---

## 검증 항목 2 — Tenant 자동 생성 트랜잭션

### 배경

ERD 명세(`02_erd_specification.md`)에 따르면 OAuth 회원가입 시 다음 4개 테이블에 데이터를 동시에 생성해야 한다.

```
users           — 글로벌 사용자 (tenant_id 없음)
tenants         — personal tenant 자동 생성 (slug = username)
tenant_members  — tenant-user 연결 (role = 'owner')
user_settings   — 기본 설정 (locale='ko-KR', theme='system' 등)
```

OAuth 샘플링에서는 `users` 테이블만 저장했고 나머지 3개는 미검증 상태다.

### 목표

`CustomOAuth2UserService.loadUser()` 내부에서 4개 테이블을 단일 트랜잭션으로 생성하는 패턴을 검증한다.

### 검증 항목

#### 2-1. 트랜잭션 원자성

- [ ] user + tenant + tenant_members + user_settings 생성이 하나의 `@Transactional`로 묶이는지 확인
- [ ] 중간 단계 실패 시 전체 롤백 확인 (tenant 생성 성공 후 tenant_members 실패 시나리오)

#### 2-2. 중복 처리 (기존 사용자 매핑)

ERD에서 `oauth_identities`에 `uq_oauth_provider_user UNIQUE (provider, provider_user_id)` 제약이 있다.

- [ ] 신규 사용자: `oauth_identities`에 없으면 user + tenant 전체 생성
- [ ] 기존 사용자 (같은 provider로 재로그인): `oauth_identities`에 있으면 기존 user 반환, 신규 생성 없음
- [ ] 기존 사용자 (다른 provider, 같은 email): `users.email`로 조회 후 `oauth_identities`에 새 provider 추가만 수행

```
케이스별 처리 흐름:
A. oauth_identities 에 (provider, providerId) 존재 → 기존 user 반환
B. users 에 email 존재, oauth_identities 에 없음 → oauth_identities 에만 추가 (계정 연결)
C. 둘 다 없음 → 신규 가입 (4개 테이블 전체 생성)
```

- [ ] 케이스 A, B, C 각각 테스트

#### 2-3. Username / Slug 생성 규칙

`tenants.slug`는 URL 식별자다. OAuth 사용자는 username이 없으므로 자동 생성 필요.

- [ ] 생성 규칙 검증 (예: `email` 앞부분 추출 → 소문자 + 특수문자 제거 → 중복 시 숫자 suffix)
- [ ] 중복 slug 발생 시 처리 (동시 가입 등)

#### 2-4. GitHub email null 처리

GitHub은 public email 미설정 시 `email`이 null로 반환된다.

- [ ] email null인 GitHub 사용자 가입 처리 방식 결정 및 검증
  - 옵션 A: GitHub `/user/emails` API 추가 호출해서 primary email 획득
  - 옵션 B: email 없이 `provider + providerId`로만 식별 (users.email은 임시값)
- [ ] 선택한 방식으로 테스트 통과 확인

### 보고 항목

```
1. 트랜잭션 처리 구조 (단일 @Transactional 범위, 서비스 레이어 설계)
2. 케이스 A/B/C 처리 로직 최종 코드
3. Username/Slug 자동 생성 규칙 및 중복 처리 방법
4. GitHub email null 처리 — 채택 방식 (A or B) 및 근거
5. 각 케이스 테스트 코드 및 통과 여부
6. 발견된 문제점 및 해결 방법
```

### 성공 기준

- [ ] 신규 가입 시 4개 테이블 동시 생성 + 롤백 테스트 통과
- [ ] 케이스 A/B/C 통합 테스트 통과
- [ ] GitHub email null 상황 테스트 통과

---

## 검증 항목 3 — Flyway + PostgreSQL 마이그레이션

### 배경

OAuth 샘플링은 H2 in-memory DB를 사용했다.
본 프로젝트는 PostgreSQL 16이며, ERD에 다음 PostgreSQL 전용 기능이 포함되어 있어 실제 환경 검증이 필요하다.

- `uuid-ossp`, `pg_trgm`, `pgvector`, `pgcrypto` 확장
- `UUID v7` (ERD에서 PK로 사용)
- Partial Index (`WHERE deleted_at IS NULL`)
- `INET` 타입 (`users.ip_address` 등)

### Flyway 파일 범위 (Step 2에서 생성할 것)

```
V1__init_extensions.sql
V2__init_tenants_and_plans.sql     (tenants, plan_quotas, tenant_members)
V3__init_users_and_auth.sql        (users, oauth_identities, user_settings)
V16__enable_rls_policies.sql
V17__create_triggers.sql           (updated_at 자동 갱신)
V18__seed_plan_quotas.sql
```

> V4~V15, V19는 이후 Step에서 추가. Flyway는 버전 순서로 실행되므로 V4~V15 없이 V16부터 실행해도 무관.

### 검증 항목

#### 3-1. PostgreSQL 확장 활성화

- [ ] Docker PostgreSQL 16에서 아래 확장 정상 활성화 확인
  ```sql
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  CREATE EXTENSION IF NOT EXISTS "pg_trgm";
  CREATE EXTENSION IF NOT EXISTS "pgcrypto";
  ```
- [ ] `pgvector`는 별도 설치 필요 — Docker 이미지 `pgvector/pgvector:pg16` 사용 권장
  ```sql
  CREATE EXTENSION IF NOT EXISTS "vector";
  ```

#### 3-2. UUID v7

ERD에서 PK는 UUID v7이다. PostgreSQL 17 미만에서는 내장 함수가 없으므로 별도 처리가 필요하다.

- [ ] 선택지 검토 및 채택
  - 옵션 A: 애플리케이션에서 생성 (`com.github.f4b6a3:uuid-creator` 라이브러리)
  - 옵션 B: `uuid-ossp`의 `uuid_generate_v4()` 사용 (UUID v4 fallback)
  - 옵션 C: PostgreSQL 17로 업그레이드 (`gen_random_uuid()` v7 지원)
- [ ] 채택 방식으로 PK INSERT 및 정렬 동작 확인

#### 3-3. Partial Index

- [ ] Partial Index 생성 및 쿼리 플랜에서 사용 확인
  ```sql
  CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE deleted_at IS NULL;
  ```
- [ ] `EXPLAIN` 결과에서 Index Scan 사용 여부 확인

#### 3-4. INET 타입

- [ ] `users` 테이블의 `ip_address INET` 컬럼 INSERT/SELECT 정상 동작 확인
- [ ] IPv4, IPv6 모두 테스트

#### 3-5. RLS (Row Level Security)

Step 2 범위는 `users`, `oauth_identities` 등 글로벌 테이블이라 RLS 미적용이지만,
`tenants` 등 도메인 테이블에 RLS가 설정되므로 기본 동작을 확인한다.

- [ ] `ALTER TABLE tenants ENABLE ROW LEVEL SECURITY` 적용 후 Spring DataSource로 SELECT 시 동작 확인
  - RLS 없이 조회되면 `app.tenant_id` SET LOCAL 주입 패턴 검증 필요
- [ ] `current_setting('app.tenant_id')` 미설정 상태에서 에러 발생 여부 확인

#### 3-6. Flyway + Spring Boot 4.0 연동

- [ ] `spring.flyway.enabled=true` + PostgreSQL DataSource 연결 설정 확인
- [ ] `spring.jpa.hibernate.ddl-auto=validate` 설정 시 Flyway 마이그레이션 후 Entity 매핑 검증 통과 확인
- [ ] 테스트 환경 (`application-test.yml`)에서 H2 + `spring.flyway.enabled=false` 설정 병행 가능한지 확인

### 보고 항목

```
1. 확장 활성화 — 사용한 Docker 이미지 및 확장 목록
2. UUID v7 — 채택 방식 및 근거 (라이브러리명/버전 포함)
3. Partial Index — EXPLAIN 결과 스크린샷 또는 텍스트
4. RLS — 동작 방식 및 app.tenant_id 주입 패턴 확인 여부
5. Flyway 마이그레이션 파일 최종 DDL (V1~V3, V16~V18)
6. 운영(PostgreSQL) / 테스트(H2) 설정 분리 방법
7. 발견된 문제점 및 해결 방법
```

### 성공 기준

- [ ] `./gradlew flywayMigrate` (또는 앱 기동 시 자동 마이그레이션) 성공
- [ ] V1~V3, V16~V18 순서대로 적용 확인
- [ ] `spring.jpa.hibernate.ddl-auto=validate` 통과
- [ ] 테스트 환경 H2로 `./gradlew test` 성공

---

## 샘플링 결과 기록

> 샘플링 완료 후 아래 섹션에 항목별로 결과를 기록한다.

### 항목 1. Jackson 쿠키 직렬화

```
채택 방식: OAuth2AuthorizationRequestDto record로 필요한 필드만 추출한 뒤 Jackson JSON + Base64 URL 인코딩
최종 코드 위치: src/main/java/com/synapse/platform/auth/config/HttpCookieOAuth2AuthorizationRequestRepository.java, OAuth2AuthorizationRequestDto.java
쿠키 크기: 테스트 authorization request 기준 encoded payload 332 bytes, 4096 bytes 미만 assertion 통과
통합 테스트 통과 여부: .\gradlew.bat test --rerun-tasks, .\gradlew.bat build 통과
발견된 문제점: OAuth2AuthorizationRequest 직접 Jackson 직렬화 대신 DTO 재구성 방식이 필요했고, SameSite=Lax는 Set-Cookie header로 직접 기록
```

### 항목 2. Tenant 자동 생성 트랜잭션

```
트랜잭션 구조: CustomOAuth2UserService.loadUser() 단일 @Transactional 안에서 user, oauth_identity, tenant, tenant_member, user_settings 저장
GitHub email null 처리 채택 방식: users.email은 {login}@github.placeholder 임시값, oauth_identities.email은 provider 원본값 null 유지
케이스 A/B/C 테스트 통과 여부: CustomOAuth2UserServiceTest 및 .\gradlew.bat test --rerun-tasks 통과
Username/Slug 생성 규칙: email 앞부분 또는 placeholder email 앞부분을 소문자화하고 영숫자만 유지, 중복 시 _{6자리 난수} suffix 최대 10회
발견된 문제점: Tenant를 먼저 저장해 default_tenant_id를 User에 반영해야 FK validate와 신규 가입 흐름이 동시에 맞음
```

### 항목 3. Flyway + PostgreSQL

```
Docker 이미지: pgvector/pgvector:pg16
UUID v7 채택 방식: com.github.f4b6a3:uuid-creator:5.3.3, Entity @PrePersist에서 UuidCreator.getTimeOrderedEpoch()
RLS 동작 확인 여부: PostgreSQLMigrationTest rlsBlocksWithoutTenantId_shouldNotThrowMissingSettingError 통과
마이그레이션 성공 여부 (V1~V3, V16~V18): .\gradlew.bat "-Dspring.profiles.active=local" "-Dgroups=integration" test --tests com.synapse.platform.auth.PostgreSQLMigrationTest 통과
ddl-auto=validate 통과 여부: PostgreSQLMigrationTest의 spring.jpa.hibernate.ddl-auto=validate 컨텍스트 기동 통과
발견된 문제점: Flyway PostgreSQL 16 인식을 위해 org.flywaydb:flyway-database-postgresql 필요. plan_quotas enterprise seed가 NOT NULL 컬럼에 NULL을 넣어 0.00으로 수정. PostgreSQL jsonb DDL과 Entity TEXT 매핑 불일치를 Hibernate JSON 타입으로 수정. 작은 users 테이블에서는 planner가 다른 partial index를 선택해 테스트에서 샘플 row 삽입 후 ANALYZE 수행.
```

---

## 참고 — Step 2 대상 테이블 ERD (발췌)

> 출처: `02_erd_specification.md` v2.0 (2026-04-30)
> Step 2에서 생성할 테이블만 발췌. 전체 ERD는 원본 문서 참조.

### 명명 / 인덱스 규칙

- 테이블명: 복수형 snake_case
- PK: `id` (UUID v7)
- FK: `<참조테이블 단수>_id`
- 모든 도메인 테이블 인덱스는 `tenant_id`를 prefix로 가짐
- 부분 인덱스: `WHERE deleted_at IS NULL` 일반화

### `plan_quotas` — 플랜별 한도

| 컬럼                              | 타입          | 제약                                        |
| --------------------------------- | ------------- | ------------------------------------------- |
| `plan`                            | VARCHAR(50)   | PK (`free` / `pro` / `team` / `enterprise`) |
| `display_name`                    | VARCHAR(100)  | NOT NULL                                    |
| `price_usd_monthly`               | NUMERIC(10,2) | NOT NULL, DEFAULT 0                         |
| `price_usd_yearly`                | NUMERIC(10,2) |                                             |
| `max_notes`                       | INTEGER       | NULL = 무제한                               |
| `max_cards`                       | INTEGER       |                                             |
| `max_storage_bytes`               | BIGINT        |                                             |
| `max_ai_tokens_monthly`           | BIGINT        |                                             |
| `max_ai_card_generations_monthly` | INTEGER       |                                             |
| `max_users_per_tenant`            | INTEGER       | 1 = personal, NULL = 무제한                 |
| `features`                        | JSONB         | NOT NULL, DEFAULT '{}'::jsonb               |
| `is_active`                       | BOOLEAN       | NOT NULL, DEFAULT TRUE                      |
| `created_at`                      | TIMESTAMPTZ   | NOT NULL, DEFAULT NOW()                     |

**시드 데이터**:

```sql
INSERT INTO plan_quotas VALUES
('free',       'Free',       0.00,   NULL,   1000,  500,   100000000,   100000,  10,   1,    '{"graphView":false,"semanticSearch":false}', true, NOW()),
('pro',        'Pro',        9.99,   95.88,  50000, 50000, 10000000000, 5000000, 500,  1,    '{"graphView":true,"semanticSearch":true}',  true, NOW()),
('team',       'Team',       19.99,  191.88, NULL,  NULL,  NULL,        20000000,2000, 50,   '{"graphView":true,"semanticSearch":true,"sharedDecks":true}', true, NOW()),
('enterprise', 'Enterprise', NULL,   NULL,   NULL,  NULL,  NULL,        NULL,    NULL, NULL, '{"graphView":true,"semanticSearch":true,"sharedDecks":true,"ssoEnabled":true,"auditLog":true}', true, NOW());
```

---

### `tenants` — 테넌트 (격리 단위)

| 컬럼          | 타입         | 제약                                             | 설명                                            |
| ------------- | ------------ | ------------------------------------------------ | ----------------------------------------------- |
| `id`          | UUID         | PK                                               | UUID v7                                         |
| `name`        | VARCHAR(200) | NOT NULL                                         | 표시 이름                                       |
| `slug`        | VARCHAR(100) | UNIQUE, NOT NULL                                 | URL 식별자                                      |
| `plan`        | VARCHAR(50)  | NOT NULL, DEFAULT 'free', FK → plan_quotas(plan) |                                                 |
| `status`      | VARCHAR(20)  | NOT NULL, DEFAULT 'active'                       | `active` / `trialing` / `suspended` / `deleted` |
| `tenant_type` | VARCHAR(20)  | NOT NULL, DEFAULT 'personal'                     | `personal` / `team` / `enterprise`              |
| `region`      | VARCHAR(20)  | NOT NULL, DEFAULT 'ap-northeast-2'               |                                                 |
| `settings`    | JSONB        | NOT NULL, DEFAULT '{}'::jsonb                    |                                                 |
| `created_at`  | TIMESTAMPTZ  | NOT NULL, DEFAULT NOW()                          |                                                 |
| `updated_at`  | TIMESTAMPTZ  | NOT NULL, DEFAULT NOW()                          |                                                 |
| `deleted_at`  | TIMESTAMPTZ  |                                                  | Soft delete (30일 grace)                        |

**인덱스**:

```sql
CREATE UNIQUE INDEX uq_tenants_slug ON tenants(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status ON tenants(status) WHERE status != 'active';
CREATE INDEX idx_tenants_plan ON tenants(plan);
```

**비고**: 회원가입 시 personal tenant 자동 생성. 사용자명을 slug로 변환.

---

### `tenant_members` — 테넌트 멤버십

| 컬럼         | 타입        | 제약                                                                 |
| ------------ | ----------- | -------------------------------------------------------------------- |
| `tenant_id`  | UUID        | PK, FK → tenants(id) ON DELETE CASCADE                               |
| `user_id`    | UUID        | PK, FK → users(id) ON DELETE CASCADE                                 |
| `role`       | VARCHAR(20) | NOT NULL, DEFAULT 'member' (`owner` / `admin` / `member` / `viewer`) |
| `invited_by` | UUID        | FK → users(id)                                                       |
| `joined_at`  | TIMESTAMPTZ | NOT NULL, DEFAULT NOW()                                              |

**인덱스**:

```sql
CREATE INDEX idx_tenant_members_user ON tenant_members(user_id);
CREATE INDEX idx_tenant_members_tenant_role ON tenant_members(tenant_id, role);
```

---

### `users` — 사용자 (글로벌, tenant_id 없음)

| 컬럼                  | 타입         | 제약                    | 설명                     |
| --------------------- | ------------ | ----------------------- | ------------------------ |
| `id`                  | UUID         | PK                      |                          |
| `email`               | VARCHAR(255) | UNIQUE, NOT NULL        | 로그인 식별자            |
| `username`            | VARCHAR(50)  | UNIQUE, NOT NULL        | URL용                    |
| `password_hash`       | VARCHAR(255) |                         | NULL = OAuth-only 사용자 |
| `display_name`        | VARCHAR(100) |                         |                          |
| `avatar_url`          | VARCHAR(500) |                         |                          |
| `email_verified_at`   | TIMESTAMPTZ  |                         |                          |
| `mfa_enabled`         | BOOLEAN      | NOT NULL, DEFAULT FALSE |                          |
| `password_changed_at` | TIMESTAMPTZ  |                         |                          |
| `last_login_at`       | TIMESTAMPTZ  |                         |                          |
| `failed_login_count`  | INTEGER      | NOT NULL, DEFAULT 0     |                          |
| `locked_until`        | TIMESTAMPTZ  |                         | brute force 잠금         |
| `default_tenant_id`   | UUID         | FK → tenants(id)        |                          |
| `created_at`          | TIMESTAMPTZ  | NOT NULL, DEFAULT NOW() |                          |
| `updated_at`          | TIMESTAMPTZ  | NOT NULL, DEFAULT NOW() |                          |
| `deleted_at`          | TIMESTAMPTZ  |                         | GDPR soft delete         |
| `anonymized_at`       | TIMESTAMPTZ  |                         | 익명화 완료 시각         |

**인덱스**:

```sql
CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_username ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_locked ON users(locked_until) WHERE locked_until IS NOT NULL;
```

---

### `oauth_identities` — OAuth 연결

| 컬럼               | 타입         | 제약                                       | 설명                |
| ------------------ | ------------ | ------------------------------------------ | ------------------- |
| `id`               | UUID         | PK                                         |                     |
| `user_id`          | UUID         | FK → users(id) ON DELETE CASCADE, NOT NULL |                     |
| `provider`         | VARCHAR(50)  | NOT NULL                                   | `google` / `github` |
| `provider_user_id` | VARCHAR(255) | NOT NULL                                   |                     |
| `email`            | VARCHAR(255) |                                            |                     |
| `metadata`         | JSONB        | NOT NULL, DEFAULT '{}'::jsonb              |                     |
| `created_at`       | TIMESTAMPTZ  | NOT NULL, DEFAULT NOW()                    |                     |

**인덱스**:

```sql
CREATE UNIQUE INDEX uq_oauth_provider_user ON oauth_identities(provider, provider_user_id);
CREATE INDEX idx_oauth_user_id ON oauth_identities(user_id);
```

---

### `user_settings`

| 컬럼                    | 타입        | 제약                                 | 설명           |
| ----------------------- | ----------- | ------------------------------------ | -------------- |
| `user_id`               | UUID        | PK, FK → users(id) ON DELETE CASCADE |                |
| `locale`                | VARCHAR(10) | NOT NULL, DEFAULT 'ko-KR'            |                |
| `theme`                 | VARCHAR(20) | NOT NULL, DEFAULT 'system'           |                |
| `srs_config`            | JSONB       | NOT NULL, DEFAULT '{}'::jsonb        |                |
| `editor_config`         | JSONB       | NOT NULL, DEFAULT '{}'::jsonb        |                |
| `notification_prefs`    | JSONB       | NOT NULL, DEFAULT '{}'::jsonb        |                |
| `pii_redaction_enabled` | BOOLEAN     | NOT NULL, DEFAULT FALSE              |                |
| `marketing_opt_in`      | BOOLEAN     | NOT NULL, DEFAULT FALSE              | GDPR 명시 동의 |
| `updated_at`            | TIMESTAMPTZ | NOT NULL, DEFAULT NOW()              |                |

---

### PostgreSQL 확장 (V1)

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";      -- pgvector (Docker: pgvector/pgvector:pg16)
CREATE EXTENSION IF NOT EXISTS "btree_gin";
```

---

### RLS 정책 패턴 (V16)

```sql
-- tenants 등 도메인 테이블에 적용
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenants
  USING (id = current_setting('app.tenant_id')::uuid);

-- users는 글로벌 테이블 → RLS 미적용
```

---

### updated_at 자동 갱신 트리거 (V17)

```sql
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER trg_tenants_updated_at
  BEFORE UPDATE ON tenants
  FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
```
