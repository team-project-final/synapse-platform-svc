# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

### Step 2 설계 결정 (샘플링 2026-05-13 완료 기준)

**D-001: OAuth2AuthorizationRequest 직렬화**
- 방식: `OAuth2AuthorizationRequestDto` record + Jackson JSON + Base64 URL 인코딩
- Java 직렬화(`ObjectOutputStream`) 미사용 — 역직렬화 공격 방지
- 쿠키 크기: ~332 bytes (4KB 이하)

**D-002: UUID v7 생성**
- `com.github.f4b6a3:uuid-creator:5.3.3`
- 모든 Entity `@PrePersist`에서 `UuidCreator.getTimeOrderedEpoch()` 사용

**D-003: GitHub email null 처리**
- `users.email` → `{github_login}@github.placeholder`
- `users.email_verified_at` → NULL (미인증 명시)
- `oauth_identities.email` → NULL (provider 원본 보존)

**D-004: oauth_identities 분리 테이블**
- `users`에 provider/provider_id 필드 없음 → `oauth_identities` 1:N 구조
- 다중 provider 계정 연결 지원

**D-005: Username/Slug 자동 생성 규칙**
- email 앞부분 → 소문자 + 영숫자만 → 중복 시 `_{6자리 난수}` suffix
- 최대 10회 시도 → 모두 실패 시 `IllegalStateException`

**Step 2 / Step 3 경계**
- `OAuth2SuccessHandler`: `/auth/callback?userId={userId}` redirect만 수행
- JWT 발급은 Step 3에서 처리 (A안 채택)

**SecurityConfig 배치**
- `com.synapse.platform.auth.config` 패키지 → `ApplicationModules.verify()` 통과 확인

**3-케이스 사용자 처리**
- Case A: `oauth_identities`에 (provider, providerId) 존재 → 기존 user 반환
- Case B: `users.email` 존재, `oauth_identities` 없음 → oauth_identities에만 추가
- Case C: 둘 다 없음 → 4개 테이블 전체 생성 (tenants → users → oauth_identities → tenant_members → user_settings)

**Flyway**
- Docker 이미지: `pgvector/pgvector:pg16`
- 마이그레이션 파일: V1~V3, V16~V18
- 의존성: `flyway-database-postgresql` 필수 (PostgreSQL 16 인식)

**RLS**
- `current_setting('app.tenant_id', TRUE)` — 두 번째 인자 TRUE 필수 (미설정 시 예외 방지)
- 미설정 상태 SELECT → 빈 결과 (에러 아님)

**JSONB ↔ Entity 매핑**
- `@Column(columnDefinition = "jsonb")` + `@JdbcTypeCode(SqlTypes.JSON)` 병기 필수

**Partial Index 테스트**
- 소규모 테이블에서 ANALYZE 선행 필요

**plan_quotas 시드 수정**
- enterprise 플랜 `price_usd_monthly = 0.00` (원본 NULL → NOT NULL 위반 수정)

## 현재 미결 사항

- OAuth Client ID/Secret: 실제 값은 환경변수로 주입 (HANDOFF에서 placeholder 처리)

## 활성 제약

- JWT 서명: RS256 고정 (Step 3에서 적용)
- Refresh Token: Redis 전용, DB 저장 금지 (Step 3)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- 쿠키: `HttpOnly=true`, `Secure=true`(프로덕션), `SameSite=Lax`, `Max-Age=180`
- OAuth 직렬화: Java 직렬화 사용 금지
- Case C 저장 순서: Tenant → User → OAuthIdentity → TenantMember → UserSettings

## 참고할 공식 문서

- docs/sampling/OAuth/OAUTH_SUMMARY.md
- docs/sampling/Step2-추가/STEP2_ADDITIONAL_SUMMARY.md
- docs/rules/06-auth-token.md
- docs/rules/07-platform-spring.md
- docs/rules/01-security.md
