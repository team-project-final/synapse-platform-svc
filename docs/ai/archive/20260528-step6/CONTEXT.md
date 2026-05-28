# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.

## 현재 확정된 것

### 브랜치 / 코드 상태
- 브랜치: `feature/PLAT-012-kafka-user-registered-producer-cards-consumer` (clean, 재시작)
- DB 마이그레이션: V28까지 완료, V29부터 신규
- Kafka 인프라: KafkaConsumerConfig (Avro), KafkaErrorHandlerConfig (DLQ 3회) 완료
- audit 모듈: AdminPlaceholder.java만 존재 (구현 없음)

### 설계 결정

**D-1. Kafka 메시지 포맷: Avro + Schema Registry**
- `docs/rules/08-kafka-event.md`의 Avro/Schema Registry MUST를 그대로 따른다.
- JSON CloudEvents String 임시 예외는 폐기한다.
- `synapse-shared` Avro schema PR 및 Schema Registry 등록은 Step 6 완료의 선행 조건이다.
- 토픽: `platform.auth.user-registered-v1`
- CloudEvents 1.0 envelope 준수 (specversion, id, source, type, time, tenantid, datacontenttype, data)

**D-2. 멱등성: audit_logs.event_id UNIQUE**
- ON CONFLICT (event_id) DO NOTHING
- 별도 processed_events 테이블 없음 (billing ProcessedEvent와 분리)

**D-3. OAuth 신규 사용자 감지: OAuthResolvedUser record**
- `OAuthUserResolver.resolveUser()` 반환 타입: `UserInfo` → `OAuthResolvedUser(UserInfo user, boolean newUser)`
- `CustomOAuth2UserService`, `CustomOidcUserService` enrichedAttributes에 추가:
  - `isNewUser` (Boolean)
  - `synapseEmail` (String)
  - `synapseDisplayName` (String)
  - `synapseTenantId` (String, UUID)

**D-4. 이벤트 발행: UserEventPublisher + OutboxEventPublisher**
- 위치: `auth/event/` 패키지
- `UserEventPublisher`: Kafka 직접 발행 금지, signup 트랜잭션 안에서 outbox_events 저장
- `UserEventPublisher`: Avro CloudEvents envelope + UserRegistered payload를 outbox에 저장
- `OutboxEventPublisher`: PENDING outbox_events를 `@Qualifier("eventKafkaTemplate")`로 Kafka 발행
- Kafka 비동기 발행 실패는 outbox attempts/last_error/next_attempt_at에 기록 후 재시도

**D-5. KafkaTemplate 빈 분리**
- `eventKafkaTemplate` — Schema Registry 기반 Avro serializer, KafkaProducerConfig
- `dltKafkaTemplate` — event payload와 호환되는 DLQ serializer 구성 필요
- KafkaErrorHandlerConfig.defaultErrorHandler() 파라미터에 `@Qualifier("dltKafkaTemplate")` 추가 필요

**D-6. Audit Consumer Factory 분리**
- `auditConsumerFactory` — Avro deserializer + Schema Registry 설정, group: audit-consumer-group
- `auditKafkaListenerContainerFactory` — audit 전용
- 기존 kafkaListenerContainerFactory와 충돌하지 않도록 qualifier를 명확히 한다.

**D-7. 관리자 API 보안**
- `@EnableMethodSecurity` 추가 → SecurityConfig
- `@PreAuthorize("hasRole('ADMIN')")` → AuditLogController
- `AuthRoles.ROLE_ADMIN = "ROLE_ADMIN"` 상수 추가

**D-8. 90일 보존 스케줄러**
- `@EnableScheduling` → PlatformApplication
- `@Scheduled(cron = "0 0 3 * * *")` → AuditLogService

**D-9. Producer 유실 방지: outbox_events**
- `EmailPasswordAuthService.signup()`과 `OAuthUserResolver.signUp()`은 같은 DB 트랜잭션 안에서 outbox row 저장
- `OAuth2SuccessHandler`는 UserRegistered 이벤트를 발행하지 않고 토큰/리다이렉트만 담당
- signup commit 성공 후 Kafka 장애가 나도 outbox row가 남아 재시도 가능

### 기존 테스트 중 업데이트 필요
- `OAuthUserResolverTest` — resolveUser() 반환 타입 OAuthResolvedUser로 변경
- `CustomOAuth2UserServiceTest` — isNewUser 속성 검증 추가
- `OAuth2SuccessHandlerTest` — 미사용 UserEventPublisher mock 제거

## Worker 리뷰 반영 결정 (2026-05-28)

**W-1. audit 모듈 위치: top-level 독립 모듈 확정**
- `com.synapse.platform.audit` — admin 하위가 아닌 별도 Modulith 모듈
- 근거: TASK_platform.md Step 6 Output Format "audit/는 독립 모듈 — admin 모듈의 일부가 아님"
- AGENTS.md/worker.md 모듈 목록은 구버전이므로 TASK_platform.md 기준 우선

**W-2. API 경로: `/api/v1/admin/audit-logs` 확정**
- TASK.md Done When의 `GET /admin/audit-logs`는 표기 축약 → 실제 구현은 `/api/v1/admin/audit-logs`
- 기존 API 패턴 (`/api/v1/...`) 일치

**W-3. tenantId null 시 이벤트 발행 금지**
- CloudEvents 규칙: `tenantid` 필수 (Rule 8.2)
- tenantId == null이면 `log.warn` 후 발행 스킵 (이벤트 미발행이 invalid 이벤트 발행보다 안전)
- UserEventPublisher에서 tenantId null 체크 후 early return

**W-4. 멱등성: exists-then-save 대신 catch DataIntegrityViolationException**
- 기존 지시 (existsByEventId → save): TOCTOU 경쟁 조건 존재
- 변경: INSERT 시도 → `DataIntegrityViolationException` catch → log.info("duplicate") → skip
- DB UNIQUE 제약이 최종 보호막 역할, 애플리케이션 레벨 체크 제거

**W-5. Flyway V29 / H2 호환성**
- @EmbeddedKafka 통합 테스트: H2(test profile) 사용 — entity String 필드로 H2 자동 생성
- JSONB/INET/TIMESTAMPTZ는 Flyway 마이그레이션에만, entity columnDefinition 미사용
- Flyway 마이그레이션 검증: 기존 Testcontainers 기반 dev-profile 테스트가 담당

**W-6. AuditLogResponse 필드 추가**
- `userAgent`, `oldValue` 포함 — 감사 목적상 모든 컬럼 응답에 포함

## Worker 구현 리뷰 기록 (2026-05-28)

**R-1. Flyway V29 DDL과 AuditLog Entity 매핑 검증 필요**
- 운영 설정은 `spring.jpa.hibernate.ddl-auto: validate`
- Flyway V29는 `old_value/new_value JSONB`, `ip_address INET`로 생성
- 현재 Entity는 H2 호환을 위해 `String` 기본 매핑을 사용하므로 PostgreSQL schema validate 또는 저장 시점에서 불일치 가능
- 처리: PostgreSQL Testcontainers 기반 Flyway + JPA validate 테스트 추가, JSONB 필드는 `@JdbcTypeCode(SqlTypes.JSON)`로 보정

**R-2. 이메일 가입 이벤트 발행 타이밍 보정 필요**
- `EmailPasswordAuthService.signup()`은 `@Transactional` 내부에서 Kafka 이벤트를 발행
- 트랜잭션 commit 실패 시 실제 가입은 롤백됐지만 `UserRegistered` 이벤트와 audit log가 남을 수 있음
- 처리: Kafka 직접 발행 대신 outbox row 저장으로 변경

**R-3. Kafka 비동기 발행 실패 로깅 보정 필요**
- `UserEventPublisher.publishUserRegistered()`가 `KafkaTemplate.send(...)` 반환 future를 관찰하지 않음
- 현재 try/catch는 직렬화 실패 또는 즉시 throw만 로깅하고, broker send async 실패는 누락 가능
- 처리: `OutboxEventPublisher`가 `whenComplete`에서 성공/실패를 관찰하고 실패 시 `log.error`와 outbox 상태를 기록

## Worker 수정 작업 계획 (2026-05-28)

**P-1. Avro/Schema Registry 전환**
- `synapse-shared`에 Rule 8 기준 `CloudEventEnvelope.avsc`, `UserRegistered.avsc` 추가가 먼저 필요하다.
- platform-svc는 JSON String 이벤트 record/ObjectMapper 파싱 경로를 제거하고 shared Avro model을 사용한다.
- `eventKafkaTemplate`은 Avro serializer, audit consumer는 Avro deserializer로 구성한다.
- 테스트는 Schema Registry mock 또는 Testcontainers 기반 Schema Registry로 구성한다.

**P-2. Outbox send 동기 예외 처리**
- `KafkaTemplate.send(...)` 호출 자체가 throw하는 경우도 outbox 실패 상태로 저장한다.
- attempts, last_error, next_attempt_at 갱신을 단위 테스트로 검증한다.

**P-3. Outbox 중복 발행 방지**
- PENDING row를 발행 전에 claim/lock한다.
- 다중 scheduler/다중 인스턴스에서도 같은 outbox row가 중복 publish되지 않아야 한다.
- `PUBLISHING` 상태 또는 DB atomic claim 전략 중 하나를 코드 기준에 맞게 선택한다.

**P-4. End-to-end 통합 테스트 보강**
- outbox publisher enabled 상태에서 signup → outbox → Kafka → audit_logs → outbox PUBLISHED 흐름을 검증한다.
- 기존 direct Kafka publish 테스트만으로는 producer outbox 경로를 검증한 것으로 보지 않는다.

**P-5. OAuth2SuccessHandler 의존성 정리**
- success handler는 token/cookie/redirect만 담당한다.
- 생성자에서 미사용 `UserEventPublisher`를 제거하고 테스트 mock도 제거한다.

## Worker 수정 완료 기록 (2026-05-28)

**C-1. Avro/Schema Registry 전환 완료**
- Kafka event producer/consumer는 `GenericRecord` + Confluent Avro serializer/deserializer를 사용한다.
- test profile은 `mock://platform-test` Schema Registry를 사용한다.
- `synapse-shared` 외부 artifact는 현재 workspace에 없어 `PlatformAvroEvents`에 Rule 8 schema를 단일 정의로 둔다.

**C-2. Outbox 안정성 보정 완료**
- outbox payload는 JSONB가 아니라 Avro binary payload로 저장한다.
- PENDING row는 publish 전 atomic claim으로 PUBLISHING 상태로 전이한다.
- `KafkaTemplate.send(...)` 동기 예외와 async 실패 모두 attempts/last_error/next_attempt_at에 기록한다.

**C-3. 검증 완료**
- `./gradlew test` 통과
- `./gradlew test --tests "*ModuleStructureTest"` 통과
- `./gradlew check` 통과

## 현재 미결 사항

- `billing.subscription-changed-v1` Producer/Consumer — Step 6 Done When 미포함, 추후 별도 처리
- Avro 스키마 등록 및 Schema Registry 반영 — Step 6 완료 전 선행 처리 필요

## Worker 리뷰 후속 수정 기록 (2026-05-28)

**F-1. Outbox PUBLISHING 고착 방지**
- `PUBLISHING` row는 `nextAttemptAt`을 publish lease 만료 시각으로 사용한다.
- scheduler 실행 시작 시 만료된 `PUBLISHING` row를 `PENDING`으로 복구한 뒤 publish 대상을 조회한다.
- claim 쿼리는 `PENDING -> PUBLISHING` 전이와 lease 만료 시각 갱신을 같은 atomic update로 처리한다.

**F-2. Kafka deserialization failure DLT 경로 보강**
- consumer key/value deserializer는 `ErrorHandlingDeserializer`로 감싸고 delegate를 `StringDeserializer`/`KafkaAvroDeserializer`로 지정한다.
- DLT publish는 정상 listener 실패의 Avro `GenericRecord`와 역직렬화 실패의 raw `byte[]`를 모두 처리할 수 있도록 Avro DLT template과 byte[] DLT template을 분리한다.

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token: Redis 전용, DB 저장 금지
- 모듈 간 순환 의존 금지 (ApplicationModules.verify() CI 통과 필수)
- 테스트 커버리지: 신규 코드 80% 이상
- Kafka Consumer at-least-once 보장 + DLQ 설정

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md (Step 6)
- docs/project-management/workflow/WORKFLOW_platform_W3.md (Step 6 체크리스트)
- docs/rules/08-kafka-event.md
- docs/rules/07-platform-spring.md
