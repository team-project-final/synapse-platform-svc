# 8. Kafka 이벤트 RULE — Kafka + Avro + Schema Registry

> **참조**: [전체 Rule 목록](../rules/) | [SCHEMA_EVOLUTION.md](https://github.com/team-project-final/synapse-shared/blob/main/docs/SCHEMA_EVOLUTION.md)

---

## 8.1 토픽 네이밍 \[MUST\]

토픽 이름은 `{서비스}.{도메인}.{이벤트}-v{N}` 패턴을 따라야 해.

- 서비스: `platform`, `knowledge`, `learning`, `engagement`
- 도메인: bounded context (예: `auth`, `note`, `card`)
- 이벤트: kebab-case 과거형 동사 / 버전: 메이저 스키마 버전

### 예시

| 상태 | 토픽 이름 | 설명 |
|------|-----------|------|
| ✅ Good | `platform.auth.user-registered-v1` | 서비스.도메인.이벤트-버전 |
| ✅ Good | `knowledge.note.note-created-v1` | knowledge 서비스, note 도메인 |
| ✅ Good | `learning.card.review-completed-v2` | 메이저 스키마 변경으로 v2 |
| ❌ Bad | `UserRegistered` | 패턴 무시, PascalCase |
| ❌ Bad | `note_created` | 서비스/도메인 prefix 없음, snake_case |
| ❌ Bad | `platform-user-registered` | dot separator 아닌 hyphen 사용 |
| ❌ Bad | `platform.auth.userRegistered-v1` | camelCase 이벤트명 |

> **이유**: 토픽명에 서비스·도메인이 포함되면 ACL, 모니터링 필터링, Consumer Group 매핑이 일관돼. MSK에서 수백 개 토픽이 생겨도 정렬만으로 파악 가능.

---

## 8.2 CloudEvents 1.0 Envelope \[MUST\]

모든 Kafka 메시지는 CloudEvents 1.0 envelope으로 감싸야 해.

### 필수 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `specversion` | string | ✅ | 항상 `"1.0"` |
| `id` | string (UUID) | ✅ | 이벤트 고유 식별자 |
| `source` | string | ✅ | 발행 서비스명 (예: `platform-service`) |
| `type` | string | ✅ | 이벤트 FQDN (예: `com.synapse.event.platform.UserRegistered`) |
| `time` | timestamp-millis | ✅ | 이벤트 발생 시각 (UTC) |
| `tenantid` | string | ✅ | 멀티테넌트 식별자 |
| `datacontenttype` | string | ✅ | `"application/json"` 고정 |
| `traceparent` | string (nullable) | ❌ | W3C Trace Context |

### CloudEventEnvelope.avsc 예시

```json
{
  "type": "record",
  "name": "CloudEventEnvelope",
  "namespace": "com.synapse.event.shared",
  "fields": [
    {"name": "specversion", "type": "string", "default": "1.0"},
    {"name": "id", "type": "string", "default": ""},
    {"name": "source", "type": "string", "default": ""},
    {"name": "type", "type": "string", "default": ""},
    {"name": "time", "type": {"type": "long", "logicalType": "timestamp-millis"}, "default": 0},
    {"name": "tenantid", "type": "string", "default": ""},
    {"name": "datacontenttype", "type": "string", "default": "application/json"},
    {"name": "traceparent", "type": ["null", "string"], "default": null},
    {"name": "data", "type": "bytes", "default": ""}
  ]
}
```

> **이유**: CloudEvents 1.0 envelope으로 통일하면 공통 미들웨어(로깅, 트레이싱, 라우팅)를 한 번만 구현하면 돼. `tenantid`는 멀티테넌트 SaaS라서 필수.

---

## 8.3 Avro 스키마 작성 \[MUST\]

`synapse-shared` 레포의 `src/main/avro/` 디렉토리에 `.avsc` 파일로 관리해.

### 규칙

- **namespace**: `com.synapse.event.{도메인}` 형식만 허용
  - 허용 도메인: `platform`, `knowledge`, `learning`, `engagement`, `shared`
- **모든 필드에 default 값 필수** — backward compatibility 보장을 위해
- `tenantId` 필드 반드시 포함
- timestamp 필드는 Avro logical type `timestamp-millis` 사용
- 필드명은 camelCase

### ✅ Good — 올바른 Avro 스키마

```json
{
  "type": "record",
  "name": "NoteCreated",
  "namespace": "com.synapse.event.knowledge",
  "fields": [
    {"name": "noteId", "type": "string", "default": ""},
    {"name": "tenantId", "type": "string", "default": ""},
    {"name": "title", "type": "string", "default": ""},
    {"name": "createdBy", "type": "string", "default": ""},
    {"name": "createdAt", "type": {"type": "long", "logicalType": "timestamp-millis"}, "default": 0},
    {"name": "tags", "type": {"type": "array", "items": "string"}, "default": []}
  ]
}
```

### ❌ Bad — 잘못된 Avro 스키마

```json
{
  "type": "record",
  "name": "NoteCreated",
  "namespace": "com.synapse.knowledge",
  "fields": [
    {"name": "note_id", "type": "string"},
    {"name": "title", "type": "string"},
    {"name": "created_at", "type": "long"}
  ]
}
```

> **이유**: namespace에 `event` 세그먼트 누락, snake_case 필드명, tenantId 없음, default 없음, logicalType 미지정 — 전부 호환성과 일관성을 깨는 실수야. default 없으면 기존 Consumer가 역직렬화에 실패해.

---

## 8.4 호환성 정책 \[MUST\]

Schema Registry의 호환성 모드는 도메인별로 아래와 같이 설정해:

| 도메인 | 호환성 모드 | 이유 |
|--------|-------------|------|
| platform | BACKWARD | 기본 정책 — Consumer가 새 스키마를 먼저 배포해도 안전 |
| knowledge | BACKWARD_TRANSITIVE | 핵심 도메인이라 모든 이전 버전과의 호환 보장 필수 |
| learning | BACKWARD | 기본 정책 |
| engagement | BACKWARD | 기본 정책 |
| shared | BACKWARD_TRANSITIVE | 공용 스키마는 최대한 보수적으로 |

> **이유**: `BACKWARD` = 직전 버전만 호환, `BACKWARD_TRANSITIVE` = 모든 이전 버전 호환. `knowledge`는 노트·지식그래프 등 핵심 데이터를 다루기 때문에, Consumer 롤백 시에도 어떤 버전이든 읽을 수 있어야 해.

---

## 8.5 금지 사항 \[MUST\]

**절대 예외 없음.** 아래 항목은 어떤 상황에서도 금지야:

| # | 금지 항목 | 대안 |
|---|-----------|------|
| 1 | ❌ 호환 모드 `NONE` 설정 | BACKWARD 또는 BACKWARD_TRANSITIVE 사용 |
| 2 | ❌ 필드 이름 변경 | `aliases`로 이전 이름 유지하면서 새 이름 추가 |
| 3 | ❌ default 없는 필드 추가 | 모든 새 필드에 반드시 default 지정 |
| 4 | ❌ enum 값 제거 | deprecated 마킹만 허용, 실제 삭제 금지 |
| 5 | ❌ 필수 필드 삭제 | deprecated 후 다음 메이저 버전에서만 제거 |

### ❌ Bad — 필드 이름 변경

```json
// v1에서 "userName" → v2에서 "displayName"으로 바꾸면 안 됨
{"name": "displayName", "type": "string", "default": ""}
```

### ✅ Good — aliases 사용

```json
{"name": "displayName", "type": "string", "default": "", "aliases": ["userName"]}
```

> **이유**: 스키마 변경은 되돌릴 수 없어. 한 번 배포되면 수십 개 Consumer가 의존하기 때문에, 호환성을 깨는 변경은 전체 시스템 장애로 이어져.

---

## 8.6 Consumer 규칙 \[MUST\]

Kafka는 **at-least-once** 전달을 보장해. 같은 메시지가 2번 이상 올 수 있으니 Consumer는 반드시 **멱등(idempotent)** 처리해야 해.

- idempotency key = CloudEvents `id` 필드 (UUID)
- 처리 완료된 이벤트 ID는 최소 7일간 보관
- 중복 감지 시 로그만 남기고 skip

### ✅ Good — 멱등 처리 패턴

```java
@KafkaListener(topics = "knowledge.note.note-created-v1")
public void handleNoteCreated(CloudEventEnvelope envelope) {
    String eventId = envelope.getId();

    // 이미 처리된 이벤트인지 확인
    if (idempotencyStore.exists(eventId)) {
        log.info("Duplicate event skipped: {}", eventId);
        return;
    }

    // 비즈니스 로직 처리
    NoteCreated event = deserialize(envelope.getData());
    noteSearchService.index(event);

    // 처리 완료 기록 (TTL 7일)
    idempotencyStore.save(eventId, Instant.now(), Duration.ofDays(7));
}
```

### ❌ Bad — 멱등 미처리

```java
@KafkaListener(topics = "knowledge.note.note-created-v1")
public void handleNoteCreated(CloudEventEnvelope envelope) {
    // 중복 체크 없이 바로 처리 — 같은 노트가 2번 인덱싱됨
    NoteCreated event = deserialize(envelope.getData());
    noteSearchService.index(event);
}
```

> **이유**: 네트워크 파티션, 리밸런싱 등으로 메시지 재전달은 반드시 발생해. 멱등하지 않으면 데이터 중복, 과금 오류 같은 심각한 버그가 생겨.

---

## 8.7 DLQ (Dead Letter Queue) \[SHOULD\]

처리 실패한 메시지는 재시도 후 DLQ로 보내서 나중에 수동 또는 자동으로 재처리해.

- 최대 **3회 재시도** (exponential backoff) 후 DLQ 전송
- DLQ 토픽 네이밍: `{원본토픽}.dlq` (예: `knowledge.note.note-created-v1.dlq`)
- DLQ 메시지에는 원본 헤더 + 실패 사유 포함
- DLQ 모니터링 알림 필수 (Slack 또는 PagerDuty)

### Spring Kafka DLQ 설정 예시

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory(
        ConsumerFactory<String, byte[]> consumerFactory,
        KafkaTemplate<String, byte[]> kafkaTemplate) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(consumerFactory);

    // 3회 재시도 (1초, 2초, 4초 백오프)
    var backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxElapsedTime(7000L);

    // DLQ로 전송하는 recoverer
    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));

    var errorHandler = new DefaultErrorHandler(recoverer, backOff);
    factory.setCommonErrorHandler(errorHandler);

    return factory;
}
```

> **이유**: 재시도 없이 바로 실패하면 일시적 장애(DB 타임아웃, 네트워크 지연)에도 메시지를 잃어버려. DLQ가 있으면 수동 재처리로 데이터 복구가 가능해.

---

## 8.8 스키마 변경 PR 절차 \[MUST\]

Avro 스키마 변경은 아래 절차를 **반드시** 거쳐야 해:

1. `synapse-shared` 레포의 `src/main/avro/`에 `.avsc` 파일 수정/추가 후 PR 생성
2. CI `schema-check.yml`이 자동 실행 → Schema Registry 호환성 검증
3. 영향 받는 서비스 트랙 owner **모두** approve
4. **@team-lead** 최종 승인
5. main 머지 시 CI가 Schema Registry에 자동 등록

### PR Description 템플릿

```markdown
## 스키마 변경 요약
- **변경 스키마**: `NoteCreated.avsc`
- **변경 내용**: `summary` 필드 추가 (string, default: "")
- **호환성**: BACKWARD_TRANSITIVE ✅ (CI 통과)
- **영향 서비스**: knowledge-service, search-service, analytics-service

## 체크리스트
- [ ] 모든 새 필드에 default 값 있음
- [ ] namespace 규칙 준수
- [ ] tenantId 필드 포함
- [ ] CI schema-check 통과
- [ ] 영향 서비스 owner approve 완료
```

> **이유**: 스키마 변경은 모든 Producer/Consumer에 영향을 줘. CI 검증 + 다자 승인 없이 머지하면 런타임에 역직렬화 에러가 터져서 장애 전파가 일어나.
