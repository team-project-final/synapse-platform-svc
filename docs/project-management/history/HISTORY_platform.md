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
| Step 7 | FCM 푸시/SES 이메일 알림 | Done | 2026-05-28 | 2026-05-28 | FCM(Firebase Admin SDK) + SES(AWS SDK v2), notifications 테이블(V31), Kafka Consumer, 재시도 로직, 통합 테스트, PR #40 |
| Step 8 | 관리자 테넌트/사용자 관리 | Done | 2026-05-28 | 2026-05-29 | PR #42 dev 머지(`50f92d7`). V32 User.status(AttributeConverter), AdminUserService/AdminTenantService, UserSessionsRevocationRequested 이벤트, InvalidUserStatusFilterException, AdminSelfActionException, `./gradlew check` 통과 |

**W3 진행률**: 3/3 Steps 완료

### W4 (2026-06-01 ~ 06-05)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Kafka 계약 표준 | Avro+Schema Registry 전환 (선행, 이슈 #43/#30) | In Progress | 2026-05-29 | — | D-029/D-030. feature/PLAT-015-kafka-avro-registry. Worker 구현/검증 완료, Director 리뷰 및 PR 대기. 기한 06-02 |
| Step 9 | 인증/결제 전체 E2E 테스트 | Done | 2026-06-04 | 2026-06-04 | PLAT-023, PR #57. Testcontainers PG E2E 5시나리오, gap 이슈 #56 등록 |
| Step 10 | P0 버그 수정 및 알림 안정화 | Done | 2026-06-05 | 2026-06-05 | PLAT-028, PR #64. P0 0건. FCM/SES 재시도 + Micrometer 메트릭(성공/실패/지연) |

**W4 진행률**: 2/2 Steps 완료 (Step 9, Step 10 완료 — W4 종료)

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
  - **Step 7 시작**: FCM 푸시/SES 이메일 알림 발송 — Director 설계 완료, HANDOFF.md 작성 완료
    - 코드베이스 분석: notification 모듈 현황 파악, Kafka 인프라 패턴 확인, Flyway 최신 V30 확인
    - 확정된 설계: `platform.notification.notification-send-v1` 토픽, ExponentialBackOff(1s/2s/4s), UNIQUE(event_id, channel) 멱등성 키
    - Firebase Admin SDK 9.4.1 + AWS SES SDK v2 (sesv2:2.26.29) 추가 예정
    - V31 마이그레이션(notifications 테이블) + 14개 신규 파일 + 6개 파일 수정 명세 완료
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
  - **Step 7 완료**: FCM 푸시 + SES 이메일 알림 발송 구현 완료 (PR #40)
    - `FcmConfig` / `SesConfig` 빈 설정, `NotificationKafkaConsumer` (notification.send 토픽 구독)
    - `FcmPushService` (Firebase Admin SDK, 디바이스 토큰 유효성 검증)
    - `SesEmailService` (AWS SES SDK v2, 재시도 포함)
    - `NotificationService` 오케스트레이션 (이벤트 타입별 FCM/SES 라우팅)
    - `Notification` 엔티티 + `V31__create_notifications.sql` Flyway 마이그레이션
    - `KafkaErrorHandlerConfig` DLQ 설정 추가
    - 단위 테스트 5종 + 통합 테스트 1종 (`NotificationKafkaConsumerIT`) 전체 통과
    - `feature/PLAT-013-notification-fcm-ses` → PR #40 생성 (base: dev)
- **Step 8 완료**: 관리자 테넌트/사용자 관리 API 구현 완료
    - `V32__add_user_status.sql`: users 테이블 status(active|suspended|deleted), suspended_at 컬럼 추가
    - `UserStatus` enum + `AttributeConverter` (소문자 DB 저장, `@Enumerated` 미사용)
    - `AdminUserService` (목록/검색/정지/삭제), `AdminTenantService` (목록/상태 변경)
    - `AdminUserController` (user 모듈), `AdminTenantController` (billing 모듈)
    - SecurityConfig `/api/v1/admin/**` → `hasRole("ADMIN")` 적용
    - 세션 무효화: `UserSessionsRevocationRequested` 이벤트 → `UserSessionRevocationListener` (모듈 순환 없음)
    - `AdminSelfActionException` (관리자 본인 정지/삭제 400), `InvalidUserStatusFilterException` (잘못된 status 쿼리 400)
    - 로그인 차단: OAuthUserResolver `isLoginAllowed()` 선검증, EmailPasswordAuthService status 검증
    - `./gradlew check` BUILD SUCCESSFUL
- **진행 중**: 없음
- **이슈**: 없음
- **다음**: PR 생성 후 W4 Step 9 (E2E 테스트) 착수

#### 2026-05-29 (금)
- **완료**:
  - **Step 8 종료**: PR #42 (PLAT-014 관리자 테넌트/사용자 관리 API) → dev 머지 완료 (merge commit `50f92d7`)
  - W3 전체 마무리 — Step 6·7·8 모두 Done, 8/10 Step 완료
- **진행 중**: 없음
- **이슈**: 없음
  - **W4 선행 착수**: Kafka 이벤트 계약 표준(Avro+Schema Registry, D-002 Option 1) 적용 작업 — team-lead work-order #43/#30 기준 방향 전환(이전 JSON CloudEvent 검토안 폐기). Director 설계 완료: D-029/D-030 결정, current/ TASK·CONTEXT·HANDOFF 작성, feature/PLAT-015-kafka-avro-registry 브랜치 생성. shared `.avsc`가 표준 미정합(displayName/eventId/occurredAt 누락, NotificationSend namespace 구버전) 발견 → 벤더링 보정 + 병행 shared PR 전략 확정.
- **다음**: Worker(Codex) HANDOFF 구현 → Director 리뷰 → PR(base: dev). 이후 Step 9(E2E)
- **주간 요약**: W3 Step 6(Kafka→audit_logs, PR #39) / Step 7(FCM 푸시+SES 이메일, PR #40) / Step 8(관리자 테넌트·사용자 관리, PR #42) 3개 Step 전부 완료. W4 선행으로 Kafka 계약 표준(Avro+Registry) 전환 착수. Avro+Schema Registry 기반 outbox 패턴, notifications 테이블(V31), User.status(V32) + 세션 무효화 이벤트까지 구현. 모든 PR dev 머지 완료. W1~W3 책임 범위 전부 종료, 남은 작업은 W4 Step 9·10(E2E·안정화).

### W4 (2026-06-01 ~ 06-05)

#### 2026-06-01 (월)
- **완료**:
  - PLAT-015 Kafka 계약 표준 전환 Worker 잔업 처리.
  - `PlatformAvroEvents` 제거, `UserRegistered`/`NotificationSend` SpecificRecord 기반 producer/consumer/outbox/audit/notification 경로 정렬 상태 점검.
  - 운영 `application.yml`에 커스텀 Kafka 빈이 읽는 공통 `spring.kafka.properties` 복구, Kafka smoke test가 Schema Registry URL을 검증하도록 보강.
  - 검증 통과: `.\gradlew.bat generateAvroJava`, `.\gradlew.bat test --tests '*ModuleStructureTest'`, `.\gradlew.bat check`.
  - synapse-shared Kafka/Schema Registry 로컬 기동 후 `kafka-e2e-test.sh --scenarios` PASS 5 / FAIL 0.
  - `kafka-avro-console-producer/consumer`로 `platform.auth.user-registered-v1` Avro value 수신 확인.
- **진행 중**: Director 리뷰 및 PR(base: dev) 준비
- **이슈**:
  - synapse-shared 로컬 `scripts/kafka-e2e-test.sh`는 CRLF로 직접 실행 실패. 파일 수정 없이 실행 시 `tr -d '\r'` 정규화로 수행.
  - synapse-shared 현재 `platform/*.avsc`는 아직 보정 전 원본이라 병행 shared PR 및 team-lead 비준 필요.
- **다음**: Director 리뷰 → 사용자 커밋 승인 → PR 생성

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
  - 브랜치 정리: 로컬 `main` → `origin/main` fast-forward, 병합 완료된 orphan 브랜치 `docs/PLAT-016-readme-update` 삭제 (원격 `gone` 확인)
  - **PLAT-017 (이슈 #47, CI Docker Hub rate-limit)**: `dev-smoke` 잡 `Start dev services` 앞에 `docker/login-action@v3` 로그인 스텝 추가. 시크릿 선행조건(`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`) 팀장 등록 확인. PR #49 (Closes #47) → **머지 완료**
  - **PLAT-018 (flaky 테스트 수정)**: #49 CI `build` 잡이 `AuditKafkaIntegrationTest`에서 반복 실패 → 코드 변경 0(워크플로 1줄)인데 실패해 flaky로 진단. 근본 원인 = 테스트 `application.yml` 컨슈머에 `auto-offset-reset` 누락 → Kafka 기본값 `latest` → 컨슈머 할당이 발행보다 늦으면 메시지 유실. 운영과 동일하게 `earliest` 고정 + audit await 3→10초. 로컬 BUILD SUCCESSFUL. PR #50 → **머지 완료**
  - 전 PR 머지 후 로컬 dev 동기화(fast-forward) + 머지된 feature 브랜치 정리
  - HISTORY 2026-06-04 정리(PLAT-019, PR #53) → 머지
  - **#52 리뷰(팀장 audit S6 PR)**: 6-pillar 코드 리뷰 후 spotbugs 블로커(벤더링 Avro 네임스페이스 미제외) 수정 푸시(`b8457e0`)로 CI green 처리. (초기 Request Changes는 dismiss — 교훈: 팀 PR 블로킹 액션은 사전 확인)
  - **PLAT-022 (이슈 #51, Kafka security.protocol)**: producer/consumer 팩토리에 `spring.kafka.security.protocol` 배선(기본 PLAINTEXT, MSK TLS-only 대비). TDD. PR #54 → 머지
  - **PLAT-021 (JWT flaky 결정적 수정)**: 서명 첫 글자(6비트 온전)를 원본과 다른 값으로 치환. 로컬 3회 연속 통과. PR #55 → 머지
  - **minikube 로컬 개발 환경 구축**: 인프라 5종 + platform-svc만 기동(불필요 워크로드 scale 0), `/actuator/health` UP 검증. 공유 gitops 파일 수정 0.
  - **Step 9 (PLAT-023, 인증/결제 E2E)**: @SpringBootTest+MockMvc 5시나리오(가입→로그인→JWT, MFA, Checkout→Webhook→구독, 갱신, 전체연속). Testcontainers PG(pgvector pg16)+Flyway. PR #57 → 머지. **Step 9 Done**
- **진행 중**: 없음
- **이슈**:
  - **gap 등록 #56**: billing 웹훅 멱등성 `ON CONFLICT` 네이티브 SQL이 H2 테스트 프로파일에서 미검증(repo 항상 모킹) → E2E에서 발견. E2E는 Testcontainers PG로 실행해 우회. 후속 조치 이슈에 정리.
- **다음**: Step 10(P0 버그 수정 + 알림 안정화) / (선택) OAuth 로그인 leg E2E 보강

#### 2026-06-05 (금)
- **완료**:
  - **KAFKA_ENABLED 게이트 (이슈 #59, PLAT-026, PR #61)**: producer/consumer/error-handler/consumer에 `@ConditionalOnProperty(synapse.kafka.enabled)`, OutboxEventPublisher는 `@ConditionalOnExpression`. gitops env no-op 해소.
  - **Step 10 알림 안정화 (PLAT-028, PR #64)**: FCM/SES 재시도(설정형 max-attempts/backoff) + Micrometer 메트릭(`notification.send` 성공/실패/지연). P0 버그 0건 확인. → **Step 10 Done**
  - **Flyway 버전 표준 (이슈 #65, PLAT-030, PR #66)**: flyway-guard caller + `application.yml` out-of-order/baseline. synapse-shared 표준(#22) 적용. Flyway Guard CI green.
  - 룰 4.6(native SQL은 PG 통합테스트로 검증, PR #60) 추가.
  - 로컬 다중 서비스 실행 충돌 진단: synapse-shared 공유 DB(`synapse`) + 서비스별 V28 번호 겹침이 원인 → minikube(DB/포트 격리) 또는 4서비스 flyway 표준 롤아웃으로 해소.
- **진행 중**: 없음
- **이슈**:
  - 미해결 이슈 0(코드). 열린 이슈는 dev 반영 완료분으로 dev→main 릴리스 시 자동 close. #62(W5 라이브 E2E)는 W5 작업.
- **주간 요약**: W4 종료 — Step 9(E2E)·Step 10(알림 안정화) 완료(2/2). 추가로 CI 안정화(flaky 2건 근본 수정), KAFKA_ENABLED 게이트, Kafka security.protocol, Flyway 표준, 룰 4.6, minikube 로컬 환경까지 처리. dev 11+커밋 미릴리스(dev→main 대기).

### W5 (2026-06-08 ~ 06-12)

| 구분 | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| W5 작업 로그 | 라이브 E2E 및 staging 검증 | In Progress | 2026-06-09 | — | #62 라이브 E2E, #37 staging health/profile 정합 |
| W5 작업 로그 | 프론트 연동 백엔드 갭 구현 | In Progress | 2026-06-09 | — | A-1/A-3/A-4 완료, A-2/A-5/A-6 후속. 7번/B 타 서비스 항목 제외 |

**W5 진행 상태**: 라이브 E2E 재검증 대기 + 프론트 연동 백엔드 갭 구현 진행 중

#### 2026-06-09 (화)

- **완료**:
  - W5 PRD/WORKFLOW와 열린 이슈 #62/#37 확인.
  - `origin/dev` 기준 `feature/PLAT-062-w5-live-e2e` 브랜치 생성.
  - 기존 `docs/ai/current` W4 Kafka 문서를 archive로 이동하고 W5 실행용 `TASK.md`, `CONTEXT.md`, `HANDOFF.md` 작성.
  - synapse-gitops/synapse-shared 최신 main fast-forward 및 platform 관련 W5 문서/overlay/Avro 정본 확인.
  - #37 staging datasource 정합 확인: 최신 gitops overlay가 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`를 주입하고 shared 문서가 platform-svc staging Healthy를 기록.
  - platform `UserRegistered.avsc`, `NotificationSend.avsc`를 shared 최신 정본과 동일하게 정렬.
  - 검증 통과: `generateAvroJava`, `AuthBillingE2ETest`, notification 테스트 묶음, `clean build`(286 tests, failures 0).
  - `origin/dev` 기준 `feature/PLAT-063-frontend-backend-gap` 브랜치 생성.
  - 루트 `docs/BACKEND_GAP_platform.md` 기준 프론트 연동 백엔드 갭 작업 문서 작성. `TASK_platform.md`는 원본 개발 목록으로 유지.
  - A-1 User 셀프서비스 API 구현: 내 프로필 조회/수정, 비밀번호 변경, OAuth 연결 조회/해제, 본인 계정 삭제.
  - 검증 통과: User/OAuthConnection 테스트, Modulith 구조 테스트, `clean build`.
- **진행 중**:
  - #62 cross-service live 알림 E2E는 engagement/learning P0 선결 후 재실행 필요.
  - A-2~A-6 프론트 연동 백엔드 갭은 후속 구현 필요.
- **이슈**:
  - 프론트엔드 결제 UI는 아직 미연동이므로 결제 검증은 백엔드 Stripe Test Mode 기준으로 설명해야 한다.
  - 로그아웃 전용 HTTP endpoint 없음. 현재 토큰 무효화는 `RefreshTokenService.delete(userId)` 및 세션 무효화 이벤트 경로만 존재.
  - shared W5 Day1 기준 P0는 platform이 아니라 engagement `UserRegistered` reader(F1), learning-ai `NotificationSend` writer(F2/F3).
- **다음**:
  - #62 코멘트/보고 후, engagement/learning P0 머지 이후 cross-service live E2E 재실행.

---

#### 2026-06-10 (수)

- **완료**:
  - `origin/dev` 기준 `feature/PLAT-067-billing-read-apis` 브랜치 생성.
  - PLAT-067 작업문서 작성 및 PLAT-066 current 문서 archive.
  - A-4 Billing 보강 구현: 결제 이력 조회, 사용량/플랜 한도 조회, 영수증/인보이스 조회 API 추가.
  - `payment_history`에 Stripe invoice id/url/pdf url 메타데이터 nullable 컬럼 추가.
  - `TenantApi` named interface로 `plan_quotas` 조회 계약을 추가해 billing 모듈 경계를 유지.
  - `invoice.paid` Webhook 저장 로직에 invoice 메타데이터 반영.
  - 검증 통과: Billing controller/service/repository/security 테스트, PlatformModuleStructureTest, `clean build`.
- **진행 중**:
  - A-2 Auth 보강, A-5 Tenant 셀프관리, A-6 Admin 대시보드 보강은 후속.
- **이슈**:
  - 실제 note/card/storage/AI 사용량 정본은 platform 단독 소관이 아니므로 이번 API는 `NOT_CONNECTED` source와 plan quota 한도만 반환.
- **다음**:
  - 작업 내용 리뷰 후 PR 준비.

---

#### 2026-06-10 (수) - PLAT-070

- **완료**:
  - `origin/dev` 기준 `feature/PLAT-070-auth-recovery` 브랜치 생성.
  - PLAT-070 작업문서 작성 및 PLAT-069 current 문서 archive.
  - A-2 Auth 복구 플로우 보강: 비밀번호 재설정 요청/검증/확정 API 추가.
  - MFA 백업 코드 발급/재발급/검증 API 추가.
  - password reset code email을 notification 일일 quota에서 제외.
  - reset code와 MFA backup code 원문 미저장, 만료/1회 사용/동시성 잠금 검증 보강.
  - 검증 통과: auth recovery 단위/통합 테스트, notification repository/service 테스트, `clean build`.
- **진행 중**:
  - 실제 email 발송은 Kafka/SES 설정이 켜진 환경에서 연동 확인 필요.
- **이슈**:
  - 로그인 전 MFA challenge에서 backup code를 사용하려면 별도 challenge session 모델이 필요하다.
- **다음**:
  - PR 생성 후 review 대응.

---

#### 2026-06-10 (수) - PLAT-071

- **완료**:
  - `origin/dev` 기준 `feature/PLAT-071-admin-analytics` 브랜치 생성.
  - PLAT-071 작업문서 작성 및 PLAT-070 current 문서 archive.
  - A-6 Admin 대시보드 보강 1차: `GET /api/v1/admin/analytics/summary` API 추가.
  - 사용자/테넌트/구독/알림/감사로그 기준 platform-local 운영 요약을 반환하도록 구현.
  - `*.today` 지표를 최근 24시간이 아닌 `generatedAt` 날짜 00:00 이후 기준으로 정리.
  - 사용자 total/deleted는 soft-delete 행 포함 native count 기준으로 정리.
  - AI token/storage 등 cross-service 정본이 필요한 값은 fake count 없이 `NOT_CONNECTED`로 반환.
  - admin 모듈은 각 도메인 named interface API만 사용하도록 Modulith 경계를 유지.
  - repository query 통합 테스트 보강: user/tenant/billing/notification/audit analytics query 검증.
  - 검증 통과: Admin analytics 단위/보안 테스트, repository query 테스트, PlatformModuleStructureTest, `clean build`.
- **진행 중**:
  - Admin 시스템 설정/피처 플래그 API와 GDPR/data request API는 후속 작업으로 분리.
- **이슈**:
  - DAU/MAU는 별도 analytics event가 아니라 `users.last_login_at` 기준 후보 지표.
  - learning/knowledge 정본 사용량은 해당 서비스 계약 확정 후 연결 필요.
- **다음**:
  - 작업 내용 리뷰 후 PR 준비.

---

#### 2026-06-10 (수) - PLAT-072

- **완료**:
  - `feature/PLAT-072-admin-settings` 브랜치에서 A-6 Admin 대시보드 보강 2차 구현.
  - `GET /api/v1/admin/settings`, `PUT /api/v1/admin/settings` API 추가.
  - Plan quota는 수정 없이 `TenantApi.listPlanQuotas()` 공개 계약으로 조회하도록 정리.
  - 피처 플래그와 API 요청 제한 설정 저장용 `admin_settings` 테이블 추가.
  - feature flag key는 영문 stable key로 저장하고, `apiRequestsPerMinute`는 `1..10000` 범위 검증.
  - admin settings service/controller/security 테스트 추가 및 보강.
  - 검증 통과: `*AdminSettings*`, `AdminSecurityIntegrationTest`, `PlatformModuleStructureTest`, `clean build`.
- **진행 중**:
  - GDPR/data request API는 후속 PLAT-073 후보.
- **이슈**:
  - 이번 작업은 설정 저장까지만 포함하며 실제 feature flag 적용과 rate limit enforcement는 별도 작업.
- **다음**:
  - 작업 내용 리뷰 후 PR 준비.

---

#### 2026-06-10 (수) - PLAT-084

- **완료**:
  - `fix/PLAT-084-openapi-docs` 브랜치에서 OpenAPI/SpringDoc 문서 노출 이슈 대응.
  - SpringDoc WebMVC UI 의존성을 추가하고 `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` 인증 예외를 추가.
  - Confluent Avro serializer 전이 의존성의 구버전 `swagger-annotations`를 제외해 SpringDoc 3.x 런타임 충돌을 제거.
  - 인증 없이 `/v3/api-docs`, `/swagger-ui/index.html` 접근이 가능한지 통합 테스트 추가.
  - 검증 통과: OpenAPI/security 관련 테스트, `clean build`.
- **진행 중**:
  - Gateway `/api/platform/**` 경로 보호 정책은 shared 기준상 정상 범위라 이번 platform 수정 대상에서 제외.
- **이슈**:
  - 없음.
- **다음**:
  - PR 생성 후 CI 확인.

---

#### 2026-06-10 (수) - PLAT-091

- **완료**:
  - `fix/PLAT-091-oauth-provider-column` 브랜치에서 OAuth provider 컬럼/Flyway migration 정합 조사 완료.
  - 현재 tracked repo 기준 `OAuthIdentity.providerUserId`는 `@Column(name = "provider_id")`로 명시 매핑되어 있음을 확인.
  - `V3__init_users_and_auth.sql`과 `OAuthIdentitySchemaTest`도 `provider_id` 기준으로 정합함을 확인.
  - `V28__rename_oauth_provider_id_column.sql`은 tracked 파일이 아니며, repo 기준 V28 중복 migration은 없음을 확인.
  - 결론: `provider_user_id` rename migration은 추가하지 않는다.
  - 검증 통과: OAuth/schema 테스트, #91 언급 DB 통합 테스트, `clean build`.
- **진행 중**:
  - 없음.
- **이슈**:
  - 팀장님 로컬 checkout의 untracked rename migration 파일은 커밋하지 않거나 제거해야 한다.
- **다음**:
  - #91에 조사 결과 공유 후 close 여부 결정.

---

#### 2026-06-10 (수) - PLAT-086

- **완료**:
  - `fix/PLAT-086-admin-role-contract` 브랜치에서 ADMIN role 발급/계약 정합 보강 완료.
  - 최초/운영 어드민 부여 방식은 기존 결정대로 DB 수동 grant를 유지했다.
  - 자동 seed admin, bootstrap admin, 승격 API, env/profile 변경은 추가하지 않았다.
  - platform DB/Spring Security 정본은 `ROLE_USER`, `ROLE_ADMIN`으로 유지했다.
  - access token `roles` claim에는 engagement 호환을 위해 `ROLE_ADMIN`과 함께 `ADMIN` alias를 포함하도록 보강했다.
  - `JwtTokenProvider.getAuthentication()`은 bare role alias를 Spring authority로 정규화해 platform 내부 admin endpoint 권한을 유지한다.
  - 검증 통과: JWT 단위 테스트, role/auth/admin 보안 테스트, `clean build`.
- **진행 중**:
  - 없음.
- **이슈**:
  - 없음.
- **다음**:
  - #86에 변경 결과 공유 후 PR 생성.

---

## 2026-06-09 PLAT-064 작업 기록

- DB 기반 사용자 role 저장을 위해 `user_roles` 테이블을 추가했다.
- 삭제되지 않은 기존 사용자에게 `ROLE_USER`를 백필하고, 신규 이메일/비밀번호 및 OAuth 가입 시 기본 `ROLE_USER`를 저장하도록 정리했다.
- 로그인, OAuth 성공, refresh 재발급 access token 발급 시 DB role을 읽어 JWT `roles` claim에 반영하도록 변경했다.
- 운영/로컬 최초 어드민은 자동 seed/profile 없이 승인된 DB 작업으로 `ROLE_ADMIN`을 부여하는 절차를 문서화했다.
- `TASK_platform.md`는 최초 개발 목록 문서이므로 수정하지 않았다.

## 변경 이력

| 날짜 | 변경 사항 |
|------|-----------|
| 2026-06-10 | **PLAT-086 ADMIN role 발급/계약 정합 보강** — 어드민 부여 방식은 DB 수동 grant로 유지, JWT `roles` claim에 `ROLE_ADMIN`/`ADMIN` 호환 표현을 함께 제공, platform 내부 authority는 `ROLE_ADMIN`으로 정규화. JWT/auth/admin 보안 테스트 및 `clean build` 통과 |
| 2026-06-10 | **PLAT-091 OAuth provider 컬럼/Flyway migration 정합 조사** — `OAuthIdentity`와 V3 DDL 모두 `provider_id` 기준임을 확인, tracked repo에 V28 rename migration 없음 및 V28 중복 없음 확인, rename migration 추가 불필요로 결정. OAuth/schema 테스트, DB 통합 테스트, `clean build` 통과 |
| 2026-06-10 | **PLAT-084 OpenAPI/SpringDoc 문서 노출 이슈 대응** — SpringDoc WebMVC UI 의존성 추가, OpenAPI/Swagger UI 인증 예외 추가, Confluent 구버전 Swagger annotation 충돌 제거, OpenAPI 보안 통합 테스트 추가. `TASK_platform.md` 미수정. `clean build` 통과 |
| 2026-06-10 | **PLAT-072 프론트 연동 백엔드 갭 A-6 2차 구현** — Admin settings API 추가, `admin_settings` 저장소, `TenantApi.listPlanQuotas()` quota 조회 계약, settings/security 테스트 보강. `TASK_platform.md` 미수정. `clean build` 통과 |
| 2026-06-10 | **PLAT-071 프론트 연동 백엔드 갭 A-6 1차 구현** — Admin analytics summary API 추가, user/tenant/billing/notification/audit 공개 API 기반 집계, today 지표 00:00 기준 및 soft-delete 포함 user total 정리, cross-service 값 `NOT_CONNECTED` 처리. `TASK_platform.md` 미수정. `clean build` 통과 |
| 2026-06-10 | **PLAT-070 프론트 연동 백엔드 갭 A-2 구현** — Auth password reset API, MFA backup code API, notification quota 예외, 복구 플로우 테스트 보강. `TASK_platform.md` 미수정. `clean build` 통과 |
| 2026-06-10 | **PLAT-067 프론트 연동 백엔드 갭 A-4 구현** — Billing payments/usage/receipt read API 추가, invoice metadata 저장, `TenantApi` plan quota 계약 추가. `TASK_platform.md` 미수정. `clean build` 통과 |
| 2026-06-09 | **PLAT-063 프론트 연동 백엔드 갭 A-1 구현** — User self-service API(`/users/me`, password, OAuth connection, delete) 추가. `TASK_platform.md` 미수정. `clean build` 통과 |
| 2026-06-09 | **W5 라이브 E2E 작업 진행** — #37 최신 gitops/shared 기준 해소 확인, platform Avro 벤더링 shared 정본 정렬, Auth/Billing·Notification 테스트 및 `clean build` 통과. platform P0 없음, cross-service P0는 engagement/learning 선결 |
| 2026-06-05 | **Step 10 완료** — 알림 안정화(FCM/SES 재시도 + Micrometer 메트릭, PLAT-028, PR #64, P0 0건). KAFKA_ENABLED 게이트(이슈 #59, PLAT-026, PR #61). Flyway 버전 표준(이슈 #65, PLAT-030, PR #66). 룰 4.6(PR #60). **W4 종료(2/2)** |
| 2026-06-04 | **Step 9 완료** — 인증/결제 E2E(PLAT-023, PR #57, Testcontainers PG 5시나리오). Kafka security.protocol 배선(이슈 #51, PLAT-022, PR #54). JWT flaky 결정적 수정(PLAT-021, PR #55). #52(팀장 audit S6) 리뷰+spotbugs 수정. minikube 로컬 환경 구축. gap 이슈 #56 등록(billing ON CONFLICT H2 미검증) |
| 2026-06-04 | 이슈 #47 대응 — `dev-smoke` CI에 Docker Hub 로그인 스텝 추가(`ci(infra)`, PLAT-017, PR #49, 머지). EmbeddedKafka 통합 테스트 flaky 수정(consumer `auto-offset-reset: earliest`, PLAT-018, PR #50, 머지). JWT 변조 테스트 flaky 발견(PLAT-021로 수정). 브랜치 정리(main fast-forward, orphan docs/PLAT-016 삭제) |
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
