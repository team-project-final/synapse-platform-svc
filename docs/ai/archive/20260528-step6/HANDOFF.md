# HANDOFF

## FROM

Director (Claude)

## TO

Worker (Codex)

## 요청 내용

Step 6: Kafka user.registered Producer + audit_logs Consumer 구현 계획 정렬

> Kafka 메시지는 `docs/rules/08-kafka-event.md` 기준대로 Avro + Schema Registry를 MUST로 적용한다. JSON CloudEvents String 임시 예외는 폐기한다. `synapse-shared`의 Avro schema PR 및 Schema Registry 등록은 Step 6 구현 완료의 선행 조건이다.

### 신규 파일 목록

**1. `src/main/resources/db/migration/V29__create_audit_logs.sql`**
```sql
CREATE TABLE audit_logs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID         NOT NULL,
    action        VARCHAR(100) NOT NULL,
    user_id       UUID,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(200),
    old_value     JSONB,
    new_value     JSONB,
    ip_address    VARCHAR(45),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_audit_logs_event_id ON audit_logs (event_id);
CREATE INDEX idx_audit_logs_action     ON audit_logs (action);
CREATE INDEX idx_audit_logs_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
```

**2. `src/main/java/com/synapse/platform/auth/service/OAuthResolvedUser.java`**
```java
package com.synapse.platform.auth.service;
import com.synapse.platform.user.api.UserInfo;
public record OAuthResolvedUser(UserInfo user, boolean newUser) {}
```

**3. `synapse-shared/src/main/avro/...` + platform Avro model**
- `CloudEventEnvelope.avsc`와 `UserRegistered.avsc`를 Rule 8 기준으로 등록한다.
- CloudEvents 1.0 envelope 필드: specversion("1.0"), id(UUID string), source("platform-service"), type("com.synapse.event.platform.UserRegistered"), time(timestamp-millis), tenantid, datacontenttype, traceparent, data(bytes)
- UserRegistered data 필드: userId, tenantId, email, displayName, registeredAt 등 모든 필드에 default 지정
- `platform.auth.user-registered-v1` 토픽은 등록된 schema/generated class 또는 shared artifact를 사용한다.

**4. `src/main/java/com/synapse/platform/auth/event/UserEventPublisher.java`**
- `@Component`
- `OutboxEventRepository` 주입
- `@Value("${app.kafka.topics.user-registered:platform.auth.user-registered-v1}")` 주입
- `publishUserRegistered(UUID userId, String email, String displayName, UUID tenantId)` 메서드
- Avro `CloudEventEnvelope` + `UserRegistered` payload를 생성해 outbox_events에 PENDING 이벤트 저장
- tenantId == null이면 log.warn 후 발행 스킵 (invalid CloudEvents 방지, Rule 8.2 tenantid 필수)
- Kafka 직접 발행 금지 — signup 트랜잭션과 이벤트 저장을 같은 DB commit으로 묶음

**4-1. `src/main/java/com/synapse/platform/auth/event/OutboxEvent*.java`**
- `OutboxEvent`, `OutboxEventStatus`, `OutboxEventRepository`, `OutboxEventPublisher`
- `OutboxEventPublisher`는 PENDING 이벤트를 `eventKafkaTemplate`로 발행
- `KafkaTemplate.send(...).whenComplete(...)`에서 성공 시 PUBLISHED, 실패 시 attempts/last_error/next_attempt_at 기록

**4-2. `src/main/resources/db/migration/V30__create_outbox_events.sql`**
- outbox_events 테이블 생성
- 필드: id, topic, event_key, event_type, payload, status, attempts, last_error, next_attempt_at, published_at, created_at
- payload 저장 형식은 Avro 발행과 재시도에 필요한 직렬화 경계가 명확해야 한다. JSON String 전제는 사용하지 않는다.

**5. `src/main/java/com/synapse/platform/global/kafka/KafkaProducerConfig.java`**
- `@Configuration`
- `@Bean("eventKafkaTemplate")` — event payload는 Schema Registry를 사용하는 Avro serializer 적용
- `StringSerializer` 기반 event producer는 사용하지 않는다.

**6. `src/main/java/com/synapse/platform/audit/package-info.java`**
- `com.synapse.platform.audit`는 top-level 독립 Modulith 모듈 (admin 하위가 아님)
- AGENTS.md/worker.md 모듈 목록은 구버전 — TASK_platform.md 기준 우선
```java
@ApplicationModule(displayName = "Audit Module")
package com.synapse.platform.audit;
import org.springframework.modulith.ApplicationModule;
```

**7. `src/main/java/com/synapse/platform/audit/entity/AuditLog.java`**
- `@Entity @Table(name = "audit_logs")`
- `@Getter @NoArgsConstructor(access = PROTECTED)`
- 필드: id(UUID), eventId(UUID, unique), action(String), userId(UUID), resourceType, resourceId, oldValue(String), newValue(String), ipAddress(String), userAgent(String), createdAt(OffsetDateTime)
- old_value, new_value는 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`
- ip_address는 `VARCHAR(45)` — IPv4/IPv6 문자열 저장, PostgreSQL `INET` Hibernate 검증 리스크 제거
- 정적 팩토리: `of(UUID eventId, String action, UUID userId, String resourceType, String resourceId, String newValue)`
- `@PrePersist` createdAt 설정

**8. `src/main/java/com/synapse/platform/audit/repository/AuditLogRepository.java`**
- `JpaRepository<AuditLog, UUID>`
- `boolean existsByEventId(UUID eventId)`
- `Page<AuditLog> findByAction(String action, Pageable pageable)`
- `Page<AuditLog> findByUserId(UUID userId, Pageable pageable)`
- `void deleteByCreatedAtBefore(OffsetDateTime cutoff)`

**9. `src/main/java/com/synapse/platform/audit/dto/AuditLogResponse.java`**
- Java record: id, eventId, action, userId, resourceType, resourceId, oldValue, newValue, ipAddress, userAgent, createdAt
- 정적 팩토리: `from(AuditLog log)`

**10. `src/main/java/com/synapse/platform/audit/service/AuditLogService.java`**
- `@Service`
- `processEvent(CloudEventEnvelope envelope)` 또는 동등한 Avro envelope 입력을 받아 AuditLog 빌드 후 save() 시도
  - 멱등성: existsByEventId 체크 제거 → save() 후 `DataIntegrityViolationException` catch → log.info("duplicate event skipped") → return
  - TOCTOU 경쟁 조건 방지: DB UNIQUE 제약(event_id)이 최종 보호막
- action 결정 로직: CloudEvents `type` 필드 기준 (UserRegistered → "USER_REGISTERED")
- `@Scheduled(cron = "0 0 3 * * *") @Transactional deleteOldLogs()` — 90일 이전 삭제
- `Page<AuditLogResponse> getAuditLogs(String action, UUID userId, Pageable pageable)` — 조회

**11. `src/main/java/com/synapse/platform/audit/consumer/AuditKafkaConsumer.java`**
```java
@KafkaListener(
    topics = {"${app.kafka.topics.user-registered:platform.auth.user-registered-v1}"},
    groupId = "audit-consumer-group",
    containerFactory = "auditKafkaListenerContainerFactory"
)
public void consume(CloudEventEnvelope envelope) {
    auditLogService.processEvent(envelope);
}
```
- Avro deserializer가 반환한 CloudEvents envelope 타입을 consume한다.

**12. `src/main/java/com/synapse/platform/audit/controller/AuditLogController.java`**
- `@RestController @RequestMapping("/api/v1/admin/audit-logs")` — TASK Done When의 `/admin/audit-logs`는 표기 축약, 실제 경로는 이것
- `@PreAuthorize("hasRole('ADMIN')")`
- `GET /` — action(optional), userId(optional), page, size 파라미터
- 응답: `Page<AuditLogResponse>`

**13. `src/test/java/com/synapse/platform/audit/AuditKafkaIntegrationTest.java`**
- `@SpringBootTest @EmbeddedKafka`
- `@ActiveProfiles("test")`
- `@EmbeddedKafka(topics = {"platform.auth.user-registered-v1"})`
- Avro serializer + Schema Registry mock 또는 Testcontainers 기반 Schema Registry를 사용해 테스트 메시지 발행
- Awaitility로 1초 이내 audit_logs 저장 확인
- 동일 event_id 중복 발행 시 1건만 저장되는지 확인
- outbox publisher를 활성화한 signup → outbox → Kafka → audit_logs end-to-end 경로를 검증

---

### 수정 파일 목록

**14. `OAuthUserResolver.java`**
- `resolveUser()` 반환 타입: `UserInfo` → `OAuthResolvedUser`
- 기존 사용자(identity 존재 or email 매핑): `new OAuthResolvedUser(user, false)`
- 신규 가입(signUp 호출): `new OAuthResolvedUser(user, true)`

**15. `CustomOAuth2UserService.java`**
- `UserInfo user = ...` → `OAuthResolvedUser resolved = ...`
- enrichedAttributes 추가:
  ```java
  enrichedAttributes.put("userId", resolved.user().id().toString());
  enrichedAttributes.put("isNewUser", resolved.newUser());
  enrichedAttributes.put("synapseEmail", resolved.user().email() != null ? resolved.user().email() : "");
  enrichedAttributes.put("synapseDisplayName", resolved.user().displayName() != null ? resolved.user().displayName() : "");
  enrichedAttributes.put("synapseTenantId", resolved.user().defaultTenantId() != null ? resolved.user().defaultTenantId().toString() : "");
  ```

**16. `CustomOidcUserService.java`**
- 동일하게 OAuthResolvedUser 처리 + enrichedAttributes 추가

**17. `OAuth2SuccessHandler.java`**
- UserRegistered 직접 발행 제거
- OAuth 신규 가입 이벤트는 `OAuthUserResolver.signUp()` 트랜잭션 안에서 outbox_events에 저장
- 성공 핸들러는 JWT/Refresh Token 발급, 쿠키 설정, redirect만 담당

**18. `EmailPasswordAuthService.java`**
- `UserEventPublisher userEventPublisher` 생성자 주입
- `signup()` 트랜잭션 안에서 `userEventPublisher.publishUserRegistered(user.id(), email, user.displayName(), tenant.getId())` 호출
- 이 호출은 Kafka 직접 발행이 아니라 outbox_events 저장
- 주의: UserInfo.displayName()을 displayName으로 사용

**19. `KafkaConsumerConfig.java`**
- `auditConsumerFactory()` 빈 추가 — Avro deserializer + Schema Registry 설정, group-id "audit-consumer-group"
- `auditKafkaListenerContainerFactory()` 빈 추가 — AckMode.RECORD, errorHandler 주입

**20. `KafkaErrorHandlerConfig.java`**
- `defaultErrorHandler()` 파라미터에 `@Qualifier("dltKafkaTemplate")` 추가 (eventKafkaTemplate과 충돌 방지)

**21. `KafkaTopicProperties.java`**
- `userRegistered` 필드 추가 (default: "platform.auth.user-registered-v1")

**22. `application.yml`**
- `app.kafka.topics.user-registered: platform.auth.user-registered-v1` 추가

**23. `PlatformApplication.java`**
- `@EnableScheduling` 추가

**24. `SecurityConfig.java`**
- `@EnableMethodSecurity` 추가

**25. `AuthRoles.java`**
- `ROLE_ADMIN = "ROLE_ADMIN"` 상수 추가

---

### 기존 테스트 업데이트

**26. `OAuthUserResolverTest.java`**
- `UserInfo result = resolver.resolveUser(...)` → `OAuthResolvedUser result = resolver.resolveUser(...)`
- `result.id()` → `result.user().id()`
- `assertThat(result.newUser()).isTrue()` 추가

**27. `CustomOAuth2UserServiceTest.java`**
- 신규 사용자 테스트: `assertThat(result.getAttributes()).containsEntry("isNewUser", true)` 추가
- 기존 사용자 테스트: `assertThat(result.getAttributes()).containsEntry("isNewUser", false)` 추가

**28. `OAuth2SuccessHandlerTest.java`**
- `OAuth2SuccessHandler` 생성자에서 사용하지 않는 `UserEventPublisher` 의존성 제거
- 기존 user와 신규 user 모두 success handler가 토큰/쿠키/redirect만 담당하는지 검증
- OAuth 신규 가입 이벤트 저장은 `OAuthUserResolverTest`와 `UserEventPublisherTest`에서 검증

---

## 수정 작업 계획 (2026-05-28)

1. Avro/Schema Registry 전환
   - `synapse-shared`에 `CloudEventEnvelope.avsc`, `UserRegistered.avsc`를 추가하고 Rule 8의 namespace/default/tenantId/compatibility 조건을 만족시킨다.
   - platform-svc는 String JSON 이벤트 record를 제거하고 shared Avro generated model 또는 shared artifact를 사용한다.
   - `eventKafkaTemplate`은 Schema Registry 기반 Avro serializer로 구성한다.
   - audit consumer는 Avro deserializer로 envelope를 수신하고 `AuditLogService`는 envelope/data를 기준으로 audit log를 생성한다.
   - 통합 테스트는 Schema Registry mock 또는 Testcontainers 전략을 명시적으로 사용한다.

2. Outbox publisher 실패 처리 보정
   - `KafkaTemplate.send(...)` 호출 자체가 동기 예외를 던지는 경우도 attempts/last_error/next_attempt_at에 기록한다.
   - 해당 경로를 검증하는 단위 테스트를 추가한다.

3. Outbox 중복 발행 방지
   - 다중 scheduler 또는 다중 인스턴스에서 같은 PENDING row를 동시에 발행하지 않도록 claim/lock 전략을 추가한다.
   - 후보: `PUBLISHING` 상태 전이 또는 DB row claim (`SELECT FOR UPDATE SKIP LOCKED`/atomic update).
   - claim 실패 row는 처리하지 않고, claim 성공 row만 publish한다.

4. End-to-end 검증 보강
   - test profile의 outbox publisher 비활성 설정에 의존하지 않는 별도 통합 테스트를 둔다.
   - signup → outbox 저장 → publisher 발행 → audit consumer 수신 → audit_logs 저장 → outbox PUBLISHED까지 검증한다.

5. 불필요 의존성 제거
   - `OAuth2SuccessHandler`의 미사용 `UserEventPublisher` 생성자 의존성과 관련 mock 검증을 제거한다.

## 작업 완료 기록 (2026-05-28)

- Kafka event producer/consumer를 `GenericRecord` + Confluent Avro serializer/deserializer 기준으로 변경했다.
- Rule 8의 CloudEventEnvelope/UserRegistered schema는 현재 workspace에 `synapse-shared` artifact가 없어 `PlatformAvroEvents` 단일 유틸에 정의했다.
- outbox payload는 Avro binary payload로 저장하고, Flyway V30 payload 컬럼은 `BYTEA`로 변경했다.
- outbox publish 전 atomic claim + PUBLISHING 상태 전이를 추가했다.
- `KafkaTemplate.send(...)` 호출 자체 실패와 async 실패 모두 outbox 실패 상태로 기록한다.
- `OAuth2SuccessHandler`의 미사용 `UserEventPublisher` 의존성을 제거했다.
- 검증: `./gradlew test`, `./gradlew test --tests "*ModuleStructureTest"`, `./gradlew check` 통과.

## 필요한 출력 형식

구현 완료 후:
1. `./gradlew test` 전체 통과 확인
2. `./gradlew check` (checkstyle + spotbugs) 통과 확인
3. 결과 요약 (통과 테스트 수, 실패 시 원인)

## 첨부할 파일

- `docs/ai/agent/worker.md`
- `docs/ai/current/CONTEXT.md`
- `docs/ai/current/TASK.md`

## 기한

2026-05-28
