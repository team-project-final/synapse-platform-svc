# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 태스크 완료 시 archive로 이동 후 초기화합니다.

## 현재 확정된 것

- **방향**: D-002 **Option 1** — Confluent Avro + Schema Registry (이전 JSON CloudEvent 방향 폐기). [[D-029]]
- **머지**: feature/PLAT-015 → PR(base: dev). dev→main 릴리스는 별도/본인 처리.
- **Outbox**: 유지 (user-registered 유실 방지). payload는 Avro-bytes → JSON DTO로 교체, 발행 시 SpecificRecord 재구성.
- **shared 정합 전략**: 벤더링본을 보정본으로 즉시 사용 + **동일 내용 shared PR 병행**. [[D-030]]
- **단일 출처**: 이벤트 필드/네임스페이스 = synapse-shared `src/main/avro/`. 변경은 shared PR.
- **로컬 Schema Registry**: 외부 `http://localhost:8086`, compose 내부 `http://schema-registry:8081`.

### 확정 스키마 (벤더링 + shared PR 공통 — 보정본)

`src/main/avro/platform/UserRegistered.avsc` (namespace `com.synapse.platform`):
```
공통메타: eventId(string), tenantId(string), occurredAt(long, timestamp-millis), traceparent(["null","string"] default null)
도메인:   userId(string), email(string), displayName(string)
```
> shared 원본 대비 변경: **displayName 추가**, **eventId/occurredAt 추가**, registeredAt(string) → occurredAt(long)로 대체.

`src/main/avro/platform/NotificationSend.avsc` (namespace `com.synapse.platform` — 구 `com.synapse.event.platform`에서 정정):
```
공통메타: eventId, tenantId, occurredAt, traceparent
도메인:   userId(string), notificationType(string), channels(array<string> default []),
          title(string), body(string),
          emailSubject(["null","string"] default null), emailHtmlBody(["null","string"] default null),
          data(map<string> default {})
```
> shared 원본 대비 변경: **namespace 정정**, **eventId/occurredAt 추가**, DRAFT/봉투 doc 제거.

## 현재 미결 사항

- shared `.avsc` 보정안(위)의 **team-lead 최종 비준** — engagement(UserRegistered 소비자)에 영향. 병행 PR로 제기.
- learning-ai NotificationSend 발행(이슈 #32) 완료 전까지 notification-send-v1 **끝단 E2E는 미검증** (platform 자가 발행으로 라운드트립만 검증 가능).

## 현재 검증된 것

- avro 1.12.0 ↔ kafka-avro-serializer 7.7.0 조합은 `generateAvroJava` + `./gradlew check`로 통과 확인.
- `./gradlew test --tests '*ModuleStructureTest'` 통과 — 생성 Avro 클래스 import가 Modulith 경계를 깨지 않음.
- synapse-shared 로컬 Kafka/Schema Registry 기동 후 `bash scripts/kafka-e2e-test.sh --scenarios` 통과(PASS 5 / FAIL 0). 단, 해당 스크립트는 현재 JSON CloudEvent transport smoke 성격.
- `kafka-avro-console-producer/consumer`로 `platform.auth.user-registered-v1` Avro value 수신 확인. `timestamp-millis` console produce에는 `avro.use.logical.type.converters=true` 필요.

## 활성 제약

- bare typed record만 (data:bytes 중첩 봉투 금지)
- 멱등성 키 = 레코드 `eventId`
- 역직렬화 실패 → 로그 + skip + DLQ (크래시 금지)
- JWT RS256 / Refresh Token DB+Redis / 모듈 순환 의존 금지 / 신규 코드 커버리지 80%+
- 메시지 key = tenantId, subject = `<topic>-value`, 호환 BACKWARD

## 참고할 공식 문서

- synapse-shared `docs/guides/EVENT_CONTRACT_STANDARD.md` (v2, Avro)
- synapse-shared `docs/designs/D-002_SCHEMA_FAMILY_DECISION.md` (Option 1)
- synapse-shared `docs/work-orders/W4_KAFKA_WORKORDER.md` §P1-1
- synapse-shared `src/main/avro/platform/*.avsc` (벤더링 원본)
- synapse-shared `scripts/kafka-e2e-test.sh`, `scripts/create-kafka-topics.sh`
- docs/rules/08-kafka-event.md
