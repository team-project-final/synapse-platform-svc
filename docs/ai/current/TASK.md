# TASK — W4 선행: Kafka 이벤트 계약 표준 적용 (Avro + Schema Registry)

> 출처: synapse-shared W4_KAFKA_WORKORDER §P1-1 / 이슈 #43, #30
> 표준: synapse-shared `docs/guides/EVENT_CONTRACT_STANDARD.md` (D-002 **Option 1** 채택안)

## 상태

- Phase: Worker 구현/로컬 검증 완료 → Director 리뷰 및 PR 대기
- 담당 Agent: Director(설계/분해/리뷰) → Worker(구현)
- 시작일: 2026-05-29
- 목표 완료일: 2026-06-02 (W4 2일차, EOD)
- 최근 검증: 2026-06-01 `generateAvroJava`, `ModuleStructureTest`, `check`, synapse-shared `--scenarios`, `kafka-avro-console-consumer` 수신 확인

---

## Step Goal

platform-svc의 Kafka 직렬화를 **수동 Avro 바이트(data:bytes 중첩 봉투)** 에서 **Confluent `KafkaAvroSerializer/Deserializer` + Schema Registry**(bare typed record)로 전환하여, 전 서비스가 synapse-shared의 단일 `.avsc` 계약으로 메시지를 주고받게 한다.

## Done When

- [x] 직렬화를 Confluent Avro + Schema Registry로 전환 (수동 bytes/`PlatformAvroEvents` 제거)
- [x] 네임스페이스 `com.synapse.event.platform` → `com.synapse.platform` 정렬
- [x] 발행: `platform.auth.user-registered-v1` = `UserRegistered`(userId, email, displayName + 공통메타 eventId/tenantId/occurredAt)
- [x] 소비: `platform.notification.notification-send-v1` = `NotificationSend` → FCM/이메일 발송 (notificationType=AI_CARDS_READY 포함)
- [x] 멱등성: 같은 `eventId` 재수신 시 1회만 처리 (중복 발송 없음)
- [x] `bash scripts/kafka-e2e-test.sh --scenarios` 통과 (synapse-shared 레포, 로컬 Kafka+Schema Registry 기동)
- [x] 로컬 `kafka-avro-console-consumer`로 user-registered-v1 수신 확인
- [x] `./gradlew check` 통과 (신규/변경 코드 커버리지 80%+)
- [ ] feature/PLAT-015 → **PR(base: dev)** 생성

> 외부 E2E 참고: `scripts/kafka-e2e-test.sh`는 CRLF 때문에 직접 실행이 실패하여, 파일 수정 없이 실행 시 `tr -d '\r'`로 정규화해 수행했다. 해당 스크립트는 현재 JSON CloudEvent transport smoke이며, Avro 검증은 별도 `kafka-avro-console-producer/consumer`로 `platform.auth.user-registered-v1`에서 수행했다.

## Scope

- In Scope:
  - 직렬화 전환(Producer/Consumer config), `.avsc` 벤더링 + 코드생성, Outbox payload 형식 교체
  - UserRegistered 발행 경로, NotificationSend 소비 경로(audit + notification 양쪽)
  - shared `.avsc` 임시 보정(displayName/eventId/occurredAt, NotificationSend namespace) + **병행 shared PR**
- Out of Scope:
  - `learning.ai.cards-generated-v1` 소비 (D-001로 철회 — HTTP 처리)
  - CardReviewDue/LevelUp/BadgeEarned 알림 소비 (W4 후속, 별도 작업)
  - dev → main 릴리스 머지 (PR base는 dev; dev→main은 별도/본인 처리)
  - shared 라이브러리 발행 메커니즘 (표준 §6, team-lead 소관)

## Instructions (10단계 워크플로 매핑)

1. ① TASK/표준 확인 — EVENT_CONTRACT_STANDARD.md(v2, Avro) + 카탈로그 §2
2. ④ 스키마 = `.avsc` 벤더링(보정본) → `src/main/avro/platform/` (CONTEXT 확정본 사용)
3. ⑥ build.gradle.kts: avro 플러그인 1.9.1 + avro 1.12.0 + kafka-avro-serializer 7.7.0, `generateAvroJava`
4. ⑦ Producer config: `eventKafkaTemplate` → `KafkaAvroSerializer`, schema.registry.url, auto.register.schemas
5. ⑦ Consumer config: `KafkaAvroDeserializer`(ErrorHandlingDeserializer 래핑 유지) + specific.avro.reader=true, group `platform-svc-group`
   - `@KafkaListener(groupId=...)` 하드코딩도 `platform-svc-group`으로 정렬
   - DLQ용 KafkaTemplate/Recoverer는 GenericRecord 전용이 아니라 Object/SpecificRecord를 수용하도록 전환
6. ⑧ `UserEventPublisher`/`OutboxEventPublisher`: 생성된 `UserRegistered` SpecificRecord 발행 (Outbox 유지, payload JSON 교체)
   - Outbox `eventKey`와 최종 Kafka key는 tenantId
7. ⑧ `AuditLogService`/`NotificationService`: GenericRecord 수동 디코딩 → SpecificRecord 필드 접근, eventId 멱등
8. ⑧ `PlatformAvroEvents` 및 수동 인코딩/디코딩 전면 제거
9. ⑨ application.yml 표준 Kafka 설정 적용 (key=tenantId, acks=all)
10. ⑩ 테스트 전면 재작성(Embedded Kafka + mock Schema Registry) + `./gradlew check` → PR

## Constraints (이 작업 한정)

- 벤더링 `.avsc`는 **shared 병행 PR과 항상 동일 내용 유지** (drift 금지 — 머지 즉시 동기화)
- bare typed record만 사용 — `data:bytes` 중첩 봉투 **금지**
- 역직렬화 실패 → 에러 로그 + skip (크래시/무한재시도 금지, DLQ 유지)
- 멱등성 키 = 레코드의 `eventId` (envelope.id 아님 — 봉투 폐기)
- 모듈 경계 유지 (audit/notification/auth), ApplicationModules.verify() 통과
- PR 생성은 Director 리뷰 및 사용자 커밋 승인 이후 진행. Worker Done When은 구현/검증 결과 보고까지.
- synapse-shared 기반 외부 E2E는 로컬 Kafka/Schema Registry/shared 레포가 준비된 경우 수행하고, 불가 시 실행 불가 사유를 결과에 기록

## Duration

1.5 ~ 2일 (Worker 구현 + Director 리뷰 + 로컬 E2E)

## Assignee / Reviewer

- Assignee(구현): Worker(Codex)
- Reviewer(설계/코드): Director(Claude) / 최종 @team-lead(계약 영향)
