# Work History: @platform

> **담당**: platform-svc / 인증·인가  
> **관련 문서**: [SCOPE](../scope/SCOPE_platform.md) | [TASK](../task/TASK_platform.md) | [WORKFLOW](../workflow/WORKFLOW_platform_W1.md)

---

## 진행 상태 대시보드

### W1 (2026-05-12 ~ 05-16)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 1 | platform-svc 골격 생성 | Done | 2026-05-12 | 2026-05-13 | PR #3, #4 merge 완료 |
| Step 2 | OAuth 회원가입/로그인 | Done | 2026-05-13 | 2026-05-14 | oauth_identities 분리 테이블, 3-케이스 처리 |
| Step 3 | JWT 발급/검증 + MFA 기초 | Done | 2026-05-14 | 2026-05-15 | RS256 JWT, DB+Redis Refresh Token, TOTP MFA, W1 보정(D-006~D-010) 완료 |

**W1 진행률**: 3/3 Steps 완료 (W1 auth 범위 완료)

### W2 prep (2026-05-18)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 1 재점검 | 신규 문서 기준 골격 수정 | Done | 2026-05-18 | 2026-05-18 | audit/billing package-info 복구, 테스트 클래스명 수정 |
| Step 2 재점검 | 신규 문서 기준 OAuth 수정 | Done | 2026-05-18 | 2026-05-18 | Apple OAuth OIDC 구현, Microsoft TODO 문서화 |
| Step 3 재점검 | 신규 문서 기준 JWT/MFA 점검 | Done | 2026-05-18 | 2026-05-18 | 코드 변경 없음 — 구현이 신규 문서 기준 완전 충족 확인 |

### W2 (2026-05-19 ~ 05-23)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Arch Migration | Spring Modulith v2 전환 (D-017) | Done | 2026-05-19 | 2026-05-19 | feature/PLAT-004-stripe-billing, `./gradlew test` 통과 |
| Step 4 | Stripe Checkout 결제 + Webhook | Done | 2026-05-19 | 2026-05-19 | StripeClient 32.1.0, processed_events 멱등성, TenantApi, JaCoCo 80%+ |
| Step 5 | FCM 디바이스 등록 | Done | 2026-05-19 | 2026-05-19 | NotificationSecurityConfig @Order(1), Modulith 경계 준수, 9개 통합 테스트, JaCoCo 92.38% |
| RT 보정 | 멀티 디바이스 세션 지원 (D-027) | Done | 2026-05-21 | 2026-05-21 | 사용자당 최대 5대 세션, FIFO 자동 만료, pg advisory lock 적용 |
| Step 6 | Kafka → audit_logs | Not Started | — | — | |

**W2 진행률**: 2/2 Steps + 1 보정 완료 (Step 4, Step 5, RT 보정 완료)

### W3 (2026-05-26 ~ 05-29)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 6 | Kafka Audit Log | Done | 2026-05-28 | 2026-05-28 | Avro+Schema Registry, outbox pattern, PUBLISHING lease, EmbeddedKafka 통합 테스트 통과 |
| Step 7 | FCM 푸시/SES 이메일 알림 | Not Started | — | — | |
| Step 8 | 관리자 테넌트/사용자 관리 | Not Started | — | — | |

**W3 진행률**: 1/3 Steps 완료

### W4 (2026-06-01 ~ 06-05)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 9 | 인증/결제 전체 E2E 테스트 | Not Started | — | — | |
| Step 10 | P0 버그 수정 및 알림 안정화 | Not Started | — | — | |

**W4 진행률**: 0/2 Steps 완료

---

## 작업 로그

### W1 (2026-05-12 ~ 05-16)

#### 2026-05-12 (화)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-13 (수)
- **완료**:
  - AI Agent 워크플로 설계 (Director/Worker/Researcher 역할 분담)
  - docs/ai/ 폴더 구조 생성 (current/, decisions/, agent/, archive/)
  - CLAUDE.md, AGENTS.md, GEMINI.md 작성 (gitignore 처리)
  - Dockerfile multi-stage 빌드 작성 + docker build 검증 성공
  - docs/rules/13-git-rules.md 추가 (브랜치 전략, 커밋, PR 정책)
  - 브랜치 정리: feature/* → chore/PLAT-001, chore/PLAT-002
  - PR #3, #4 → dev merge 완료 / **Step 1 완료**
  - Step 2 브랜치 생성 (`feature/PLAT-004-oauth`)
  - OAuth 샘플링 완료 — A안 채택 (userId redirect, JWT는 Step 3에서 추가)
  - `application.properties` → `application.yml` 전환 + `spring.application.name=synapse-platform-svc`
  - Dockerfile / docker-compose.yml 포트 8080 → 8081 수정
  - `docs/ai/current/TASK.md` Step 2 내용으로 작성
  - `docs/ai/templates/` 폴더 분리 (템플릿 vs 실제 작업 문서 구조 개선)
  - `docs/spike/OAuth/` OAuth 샘플링 문서 추가
- **진행 중**: Step 2 분석 단계 (10단계 워크플로 ①②③ 완료)
- **이슈**: 없음
- **다음**: Step 2 설계 단계 (CONTEXT.md 작성 → HANDOFF.md → Worker 구현)

#### 2026-05-14 (목)
- **완료**:
  - Step 2 추가 샘플링 완료 (Jackson 쿠키 직렬화, Tenant 트랜잭션, Flyway+PostgreSQL 검증)
  - CONTEXT.md / HANDOFF.md 작성 (D-001~D-005 설계 결정 반영)
  - Worker 구현 완료 — Entity 5개, Repository 5개, OAuth 서비스/핸들러/SecurityConfig
  - Flyway V1~V3, V16~V18 마이그레이션 파일 작성
  - 테스트 20건+ 통과 / `./gradlew build` 성공
  - 룰북 준수 수정 ([MUST] 7건 + [SHOULD] 3건) — 29건 테스트 통과
  - PR #8 dev merge 완료 / **Step 2 완료**
  - `feature/PLAT-005-jwt-mfa` 브랜치 생성 / **Step 3 시작**
  - Step 3 작업 문서 확인 및 `docs/ai/current/PLAN.md` 작성
  - JWT/TOTP/Redis 의존성 추가 및 dev/prod 프로파일 설정
  - RS256 JWT Access/Refresh Token 발급, issuer/type 검증, Security Filter 구현
  - Refresh Token Redis 저장/조회/삭제 및 `/api/v1/auth/refresh` 구현
  - TOTP MFA setup/verify API 구현, TOTP secret AES 암호화 저장, Flyway V19 작성
  - 리뷰 보정 반영: Access/Refresh token type 분리 검증, MFA setup 재호출 시 secret 교체
  - 검증 완료: `checkstyleMain checkstyleTest spotbugsMain spotbugsTest`, `test`, `test --tests "*ModuleStructureTest"`, `build`
  - 전체 테스트 결과: 69건 통과, 실패 0건
- **진행 중**: Step 3 커밋/PR 준비
- **이슈**: 없음
- **다음**: Step 3 커밋 → W1 보정 작업 시작

#### 2026-05-15 (금)
- **완료**:
  - 팀리더 최신 문서(new_md) 검토 및 D-005~D-008 설계 결정 확정
    - 모듈 구조: billing/audit → user/admin (D-005)
    - Refresh Token: Redis 전용 → DB(token_hash) + Redis 캐시 병행 (D-006)
    - OAuth access_token_enc 암호화 저장 추가 (D-007)
    - MFA 테이블: totp_credentials → mfa_credentials (D-008)
  - Worker HANDOFF.md 작성 (변경 4건 명세)
  - Worker 구현 완료 — Flyway V20~V22, 모듈 재편, MfaCredential, RefreshTokenService DB+Redis
  - Worker 구현 리뷰 후 버그 3건 발견 → D-009~D-010 확정
    - HIGH: RefreshTokenService.save() deleteAllByUserId 누락
    - MEDIUM: OAuthIdentity 재로그인 시 access_token_enc 미갱신
    - TEST: RefreshTokenServiceTest Mockito → Testcontainers 전환
  - Worker FIX 완료 — Flyway V23, TransactionSynchronization, DB unique index
  - 전체 검증: compileJava, RefreshTokenServiceTest, ModuleStructureTest, checkstyle, spotbugs, build 전체 통과 / **Step 3 (W1 보정) 완료**
  - 공식 문서 최신화: TASK_platform.md, WORKFLOW_platform_W1.md (new_md 기준)
  - docs/ai/current/ archive 이동 (20260515-w1-correction) + 초기화
- **진행 중**: 없음
- **이슈**: 없음
- **주간 요약**: W1 Step 1~3 완료 + W1 보정(D-005~D-010) 완료. 모듈 구조·Refresh Token·MFA·OAuth 저장 방식 팀리더 최신 기준으로 정렬. 전체 빌드/테스트 통과.

### W2 (2026-05-19 ~ 05-23)

#### 2026-05-18 (월, W2 시작 전)
- **완료**:
  - 팀장 문서 리뉴얼 이후 Step 1 신규 문서 기준 재점검
  - audit/package-info.java 복구 (git D 상태 → 재생성, @ApplicationModule)
  - billing/package-info.java 신규 생성 (@ApplicationModule)
  - ModuleStructureTest → ApplicationModulesTest 클래스명 rename
  - ApplicationModulesTest 통과 확인 / archive 이동 완료
- **진행 중**: 없음
- **이슈**: 없음
- **다음**: Step 2 재점검 (신규 문서 기준) → 완료 (동일 날짜)

#### 2026-05-19 (화)
- **완료**:
  - ARCHITECTURE_v2.md 기준 D-017 결정 (D-013 번복 — Spring Modulith 단일 앱 복원)
  - D-018 (Spring Modulith 2.0.6, Boot 4.0 호환), D-019 (UserApi 설계 — @NamedInterface, UserInfo DTO, createForOAuth) 설계 결정
  - TASK.md / CONTEXT.md / HANDOFF.md 작성 — Worker(Codex) 전달
  - Worker 구현 완료: 멀티모듈 → Spring Modulith 전환
    - settings.gradle.kts 단순화, 루트 build.gradle.kts 단일 앱 전환
    - 5개 모듈 패키지 생성 (auth, user, notification, admin, shared)
    - OAuthUserResolver / TotpService → UserApi 경유로 user 모듈 경계 준수
    - `io.synapse.platform.common.*` → `shared.*` 전수 교체
    - .env.example 추가, docker-compose.yml env_file 보정
  - `./gradlew test` 전체 통과 확인 / **Arch Migration 완료**
  - current/ → archive/20260519-arch-migration-v2/ 이동 + 초기화
- **진행 중**: 없음
- **이슈**: 없음
- **다음**: Step 4 (Stripe Checkout 결제) 착수 — TASK_platform.md Step 4 기준

#### 2026-05-19 (화) — 추가
- **완료**: Step 4 Stripe Checkout 결제 + Webhook 구현 완료 (PR #17, `./gradlew check` 108 tests 통과)
- **완료**: Step 5 FCM 디바이스 등록 구현 완료
  - V27__create_device_tokens.sql (tenant_id, is_active, CHECK constraint, tenant_id prefix index)
  - Platform enum (AttributeConverter + @JsonCreator/@JsonValue), DeviceToken entity, Repository (native UPSERT)
  - DeviceTokenService (register/unregister, UserApi tenantId resolve), DeviceTokenController (POST 201 / DELETE 204)
  - NotificationSecurityConfig @Bean @Order(1) — Modulith 경계 준수 (@Qualifier Filter 주입)
  - SecurityConfig @Bean @Order(2) 추가
  - GlobalExceptionHandler: EntityNotFoundException→404, HttpMessageNotReadableException→400 추가
  - 통합 테스트 9개 시나리오 전체 통과, JaCoCo 92.38% (기준 80%)
  - `./gradlew test`: BUILD SUCCESSFUL

#### 2026-05-20 (수)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-21 (목)
- **완료**:
  - Refresh Token 멀티 디바이스 세션 지원 (최대 5대) 설계 및 구현 완료
  - `uq_refresh_tokens_user_id` UNIQUE INDEX 해제 마이그레이션 (`V28`) 추가
  - Redis 캐시 키를 `refresh:{userId}:{tokenHash}`로 고도화하여 독립 제어
  - 사용자당 최대 5개 세션 제한 및 초과 시 FIFO 자동 만료 구현
  - `rotate()` 시 구버전 토큰 정보를 넘겨 메타데이터(IP, 핑거프린트) 승계 및 특정 기기 세션만 회전하도록 개선
  - concurrent save 시 5개 세션 한도 초과 방지를 위해 PostgreSQL transaction-scoped advisory lock 도입
  - 회전 도중 세션 누락 시 500 에러를 401(Unauthorized) 및 `PLAT-002` 응답으로 전환
  - `RefreshTokenServiceTest` 및 `AuthControllerTest` 고도화 및 `./gradlew test` 전체 성공
- **진행 중**: 없음
- **이슈**: 없음
- **다음**: Step 6 (Kafka 이벤트 기반 Audit Log 자동 기록) 착수

#### 2026-05-22 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

### W3 (2026-05-26 ~ 05-29)

#### 2026-05-26 (화)
- **완료**:
  - 프론트엔드 W2 잔무 현황 파악 및 백엔드 대응 항목 정리
  - Refresh Token 전달 방식 결정 (D-028): HttpOnly Cookie 전환, 프론트엔드 팀 합의 완료
  - `feature/PLAT-008-httponly-refresh-cookie` 브랜치 생성 및 구현 완료 (PR #29 → dev merge)
    - OAuth2SuccessHandler: Refresh Token Cookie Set, redirect URL에서 제거
    - AuthController: /refresh Cookie 기반 전환 + Origin 검증 추가
    - SecurityConfig: CORS allowCredentials 적용, CorsConfig 통합
    - application.yml: forward-headers-strategy native, 프로파일별 cookie/cors 분리
    - 테스트 전체 통과 (AuthControllerTest 7케이스, OAuth2SuccessHandlerTest, SecurityConfigTest)
  - 프론트엔드 변경 안내 내용 정리 (withCredentials, /refresh body 제거)
  - docs/ai/current/ → archive/20260526-plat-008-httponly-cookie/ 이동 + 초기화
  - PLAT-009 이메일/비밀번호 회원가입·로그인 구현 및 리뷰 후속 보정 완료
    - 회원가입/로그인 API, BCrypt password_hash 저장, access token body + refresh_token HttpOnly Cookie 응답 구현
    - 리뷰 후속: login 실패 카운터를 `PESSIMISTIC_WRITE` user row lock으로 직렬화해 동시 bad-password 시도도 5회 잠금을 우회하지 못하게 보정
    - 리뷰 후속: signup DB unique 충돌을 `PLAT-009-001` / HTTP 409로 변환해 동시 중복 가입 race가 500으로 노출되지 않게 보정
- **진행 중**: 없음
- **이슈**: 없음
- **다음**: GET /billing/plans 구현 (W2 잔무 2번) 또는 W3 Step 6 착수

#### 2026-05-27 (수)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-28 (목)
- **완료**:
  - **Step 6 완료**: Kafka 이벤트 기반 Audit Log 자동 기록
    - Flyway V29 (audit_logs), V30 (outbox_events) 마이그레이션 추가
    - `PlatformAvroEvents` (CloudEventEnvelope + UserRegistered Avro schema 단일 정의, synapse-shared 대체)
    - `UserEventPublisher` (outbox 저장), `OutboxEventPublisher` (PUBLISHING lease + async 실패 기록)
    - `AuditKafkaConsumer` + `AuditLogService` (DataIntegrityViolationException 멱등)
    - `GET /api/v1/admin/audit-logs` @PreAuthorize("hasRole('ADMIN')")
    - 90일 보존 @Scheduled(cron = "0 0 3 * * *")
    - `OAuthResolvedUser` record, OAuthUserResolver 반환 타입 변경
    - `KafkaProducerConfig` (eventKafkaTemplate Avro), ErrorHandlingDeserializer 적용
    - `AuditKafkaIntegrationTest` (EmbeddedKafka + mock://platform-test Schema Registry)
    - `./gradlew test`, `./gradlew check` 전체 통과
    - docs/ai/current/ → archive/20260528-step6/ 이동 + 초기화
- **진행 중**: 없음
- **이슈**: 없음
- **다음**: Step 7 (FCM 푸시/SES 이메일 알림) 또는 Step 8 (관리자 API) 착수

#### 2026-05-29 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

### W4 (2026-06-01 ~ 06-05)

#### 2026-06-01 (월)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-02 (화)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-03 (수)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-04 (목)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-05 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

---

## 변경 이력

| 날짜 | 변경 사항 |
|------|-----------|
| 2026-05-19 | 멀티모듈 아키텍처 마이그레이션 결정 (D-013~D-015). PLAT-007 폐기. Step 번호 전면 재정비(4~10 → 5~11, 신규 Step 4 삽입). feature/PLAT-000-multi-module-migration 브랜치 시작 |
| 2026-05-18 | Step 3 재점검 완료 — RS256 JWT, DB+Redis Refresh Token, TOTP MFA 신규 문서 기준 전면 충족 확인. 코드 변경 없음. WORKFLOW/TASK 체크박스 업데이트 |
| 2026-05-18 | Step 2 재점검 완료 — Apple OAuth OIDC 구현(OAuthUserResolver 추출, CustomOidcUserService), Microsoft TODO 문서화 |
| 2026-05-18 | Step 1 재점검 완료 — 신규 문서 기준, audit/billing package-info 복구, 테스트 클래스명 수정 |
| 2026-05-15 | W1 보정 완료 기록 — D-005~D-010, Step 3 완료일 갱신, 대시보드 Step 번호 TASK 기준으로 정렬 |
| 2026-05-14 | Step 3 Done 반영 (JWT 발급/검증, Redis Refresh Token, TOTP MFA, 검증 결과 기록) |
| 2026-05-13 | 전체 일정 재정비 (05-12~06-05, 월~금), Step 1 Done 반영 |
| 2026-05-11 | W2/W3/W4 대시보드 및 로그 템플릿 추가 |
| 2026-05-11 | 초기 템플릿 생성 |

## 2026-05-18 Worker Log

**Completed**
- Step 4 Gradle multi-module migration implemented.
- Created platform-common, auth-service, billing-service, audit-service, notification-service modules.
- Moved auth/user code into auth-service and shared exception/crypto code into platform-common.
- Replaced com.synapse package root with io.synapse package root.
- Removed Spring Modulith references, ApplicationModulesTest, PlatformSvcApplication, and root src directory.
- Added placeholder Boot apps for billing, audit, and notification services.

**Fixes recorded for director review**
- Fixed invalid encoding strings in migrated auth files and SlugGeneratorTest.
- Added ConfigurationPropertiesScan to AuthServiceApplication for JwtProperties binding.
- Updated SpotBugs exclude package patterns from com.synapse to io.synapse.

**Verification**
- .\gradlew.bat :auth-service:test :platform-common:test :billing-service:build :audit-service:build :notification-service:build build -> PASS.
- Test-Path src -> False.
- rg com.synapse/spring-modulith/Modulith/Stripe patterns in Java/KTS/YML/XML -> no matches.
