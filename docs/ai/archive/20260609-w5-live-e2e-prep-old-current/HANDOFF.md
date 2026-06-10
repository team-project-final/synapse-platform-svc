# HANDOFF

> Agent 간 작업 전달 문서. 태스크마다 덮어씁니다.

## FROM
Director (Claude)

## TO
Worker (Codex)

## 작업 브랜치
`feature/PLAT-015-kafka-avro-registry` (이미 생성됨, base: dev). 커밋은 사용자 확인 후.

## 요청 내용

platform-svc Kafka 직렬화를 **Confluent Avro + Schema Registry**로 전환한다. 설계·스키마는 [[CONTEXT]] 확정본을 그대로 따른다. 새 설계 결정 금지 — 모호하면 멈추고 Director에 질의.

> 배경: 수동 Avro 봉투(`PlatformAvroEvents`, data:bytes 중첩) 전면 폐기 → 토픽당 단일 bare typed record(생성된 SpecificRecord). [[D-029]] [[D-030]]

---

### 1. `.avsc` 벤더링 (CONTEXT 확정본 그대로)

신규 파일 2개 — `src/main/avro/platform/` 에 생성:

`UserRegistered.avsc`:
```json
{
  "type": "record", "name": "UserRegistered", "namespace": "com.synapse.platform",
  "doc": "Emitted when a new user completes registration.",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "tenantId", "type": "string"},
    {"name": "occurredAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
    {"name": "traceparent", "type": ["null", "string"], "default": null},
    {"name": "userId", "type": "string"},
    {"name": "email", "type": "string"},
    {"name": "displayName", "type": "string"}
  ]
}
```

`NotificationSend.avsc`:
```json
{
  "type": "record", "name": "NotificationSend", "namespace": "com.synapse.platform",
  "doc": "Notification dispatch request consumed by platform-svc (FCM/email).",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "tenantId", "type": "string"},
    {"name": "occurredAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
    {"name": "traceparent", "type": ["null", "string"], "default": null},
    {"name": "userId", "type": "string"},
    {"name": "notificationType", "type": "string"},
    {"name": "channels", "type": {"type": "array", "items": "string"}, "default": []},
    {"name": "title", "type": "string"},
    {"name": "body", "type": "string"},
    {"name": "emailSubject", "type": ["null", "string"], "default": null},
    {"name": "emailHtmlBody", "type": ["null", "string"], "default": null},
    {"name": "data", "type": {"type": "map", "values": "string"}, "default": {}}
  ]
}
```

### 2. build.gradle.kts
- plugins: `id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"`
- deps: `org.apache.avro:avro` 1.11.3 → **1.12.0**, `io.confluent:kafka-avro-serializer:7.7.0`(유지)
- `./gradlew generateAvroJava` 로 `com.synapse.platform.UserRegistered/NotificationSend` SpecificRecord 생성 확인
- ⚠️ avro 1.12.0 ↔ kafka-avro-serializer 7.7.0 호환 충돌 시 → avro 1.11.3 유지하고 **Director에 보고**(CONTEXT 미결 항목)

### 3. Producer config — `KafkaProducerConfig.java`
- `eventKafkaTemplate`: value-serializer `KafkaAvroSerializer`, `KafkaTemplate<String, Object>`(또는 SpecificRecord 상위)
- props: `schema.registry.url`, `auto.register.schemas=true`, `acks=all`, key-serializer String

### 4. Consumer config — `KafkaConsumerConfig.java`
- `KafkaAvroDeserializer` + `specific.avro.reader=true`, group `platform-svc-group`
- `@KafkaListener(groupId=...)` 하드코딩도 `platform-svc-group`으로 정렬. 기존 `audit-consumer-group` / `notification-consumer-group` 유지 금지.
- 기존 `ErrorHandlingDeserializer` 래핑 + `DefaultErrorHandler`(DLQ/skip) **유지** (표준 §5)
- audit/notification 두 리스너가 **동일 consumer factory** 공유 가능(토픽만 다름). 기존 분리 팩토리는 단일화 검토
- `data:bytes`/GenericRecord 수동 디코딩 경로 제거
- `KafkaErrorHandlerConfig`의 DLT producer/template도 GenericRecord 전용 타입에서 `Object`/SpecificRecord 수용 타입으로 전환. 역직렬화 실패 raw `byte[]` DLT 경로는 유지.

### 5. Producer 경로 — `UserEventPublisher` + `OutboxEventPublisher` (Outbox 유지)
- `UserEventPublisher.publishUserRegistered(...)`: `UserRegistered` SpecificRecord 빌드 — eventId=UUID, occurredAt=now(epoch millis), userId/email/displayName/tenantId 채움 → Outbox 저장
- **Outbox payload 교체**: Avro-bytes → **JSON DTO 문자열**(userId,email,displayName,tenantId,eventId,occurredAt). `OutboxEvent.payload` byte[] 유지(UTF-8 JSON 저장) → V30 마이그레이션 변경 불요
- Outbox `eventKey` 저장값도 `tenantId`로 변경. 최종 Kafka record key는 항상 tenantId.
- `OutboxEventPublisher.publish()`: payload(JSON) → `UserRegistered` SpecificRecord 재구성 → `eventKafkaTemplate.send(topic, tenantId, record)`. lease/재시도/실패기록 로직 유지
- `displayName` 출처 확인: 발행 호출부(OAuth/이메일 가입 성공 지점)에서 displayName 전달되는지 확인, 없으면 UserApi로 조회

### 6. Consumer 경로
- `AuditKafkaConsumer.consume(UserRegistered ev)` — SpecificRecord 시그니처. `AuditLogService.processEvent(ev)`: `ev.getEventId()` 멱등, action=USER_REGISTERED, data=레코드 필드 직렬화
- `NotificationKafkaConsumer.consume(NotificationSend ev)` — SpecificRecord 시그니처
- `NotificationService.processNotificationSend(NotificationSend ev)`: 봉투 디코딩 제거, `ev.getEventId()`를 멱등 키로 사용(notifications UNIQUE(event_id,channel) 그대로). channels/title/body/email*/data 매핑 유지. FCM/SES 분기·일일 한도·재시도 로직 **변경 없음**

### 7. 제거
- `PlatformAvroEvents.java` 및 모든 참조(인코딩/디코딩 유틸, CloudEventEnvelope/USER_REGISTERED_SCHEMA/NOTIFICATION_SEND_SCHEMA, *Envelope/decode* 메서드) 삭제
- 영향 파일(참조 21개): `PlatformAvroEvents`, `KafkaProducerConfig`, `KafkaConsumerConfig`, `KafkaErrorHandlerConfig`, `UserEventPublisher`, `OutboxEventPublisher`, `AuditLogService`, `AuditKafkaConsumer`, `NotificationService`, `NotificationKafkaConsumer` + 테스트 10종

### 8. application.yml (`src/main/resources` + `src/test/resources`)
- 표준 §4.1 복붙: producer KafkaAvroSerializer/acks=all/auto.register, consumer KafkaAvroDeserializer/specific.avro.reader=true/group `platform-svc-group`, schema.registry.url(8086)
- test 프로파일: Embedded Kafka + **mock Schema Registry**(`mock://...`) 사용, fcm/ses enabled=false 유지

### 9. 테스트 (커버리지 80%+ 유지)
- 기존 Avro/GenericRecord 테스트 전면 재작성: SpecificRecord 직접 생성으로 발행/소비
- `NotificationKafkaConsumerIT`, `AuditKafkaIntegrationTest`: EmbeddedKafka + `schema.registry.url=mock://platform-test`, AI_CARDS_READY 케이스 포함, 동일 eventId 중복 발송 없음 검증
- `PlatformAvroEventsTest` 삭제, 대체 테스트(SpecificRecord round-trip) 추가

## 필요한 출력 형식
- 파일 경로 + 전체 코드 블록. 변경/삭제/신규 구분 명시
- `./gradlew generateAvroJava`, `./gradlew check` 실행 로그 요약(통과/실패 수)
- 독립 결정·이탈 사항은 별도 "Director review 필요" 섹션에 기록
- 커밋하지 말 것 (Director 리뷰 후 사용자 확인 → 커밋)

## 검증 (Done When 매핑)
1. `./gradlew generateAvroJava` → SpecificRecord 생성
2. `./gradlew test --tests '*ModuleStructureTest'` → generated Avro class import가 Modulith 경계를 깨지 않는지 확인
3. `./gradlew check` 전체 통과
4. (로컬 가능 시) synapse-shared에서 `docker compose up -d zookeeper kafka schema-registry kafka-init` → platform 기동 → `bash scripts/kafka-e2e-test.sh --scenarios` + `kafka-avro-console-consumer`로 user-registered-v1 수신 확인. 환경 미구성 시 사유 기록.

## 첨부할 파일
- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md
- docs/ai/current/HANDOFF.md (이 문서)
- docs/rules/08-kafka-event.md
- synapse-shared `docs/guides/EVENT_CONTRACT_STANDARD.md`, `src/main/avro/platform/*.avsc`

## 기한
2026-06-02 (W4 2일차 EOD)
