# PLAT-102 Context

## Current State
- Target task: PLAT-102
- Working branch: `fix/PLAT-102-kafka-topic-prefix`
- Base: `origin/dev`
- Target PR branch: `dev`
- 작업 대상 repo: `synapse-platform-svc`
- 현재 상태: 로컬 구현 및 검증 완료, PR 전

## Background
gitops 이슈 #199에서 dev와 staging이 같은 MSK, 같은 Kafka topic, 같은 consumer group을 공유하면서 partition assignment가 환경 간에 섞이는 문제가 확인됐다.

예시:
- dev pod 1개, staging pod 2개가 같은 topic/group을 소비
- topic partition 3개가 dev/staging pod에 나뉘어 배정
- dev에서 발행한 이벤트 일부가 staging consumer로 소비
- 결과적으로 dev 검색 색인 누락, staging DLT 발생 가능

팀 결정은 option B, topic 환경 prefix다.

```text
actual topic = ${KAFKA_TOPIC_PREFIX}<base topic>
```

예시:

```text
dev.knowledge.note.note-created-v1
staging.knowledge.note.note-created-v1
prod.knowledge.note.note-created-v1
```

## Contract Sources
- platform issue #102: platform 앱 측 적용 범위
- gitops issue #199: 환경 교차 소비 원인과 option B 결정
- gitops PR #206: dev/staging/prod overlay에 `KAFKA_TOPIC_PREFIX` 주입, prefixed topic 생성 merge 완료
- shared PR #72: `EVENT_CONTRACT_STANDARD §2.1` topic 환경 prefix 표준 merge 완료

## Contract Requirements
- env var: `KAFKA_TOPIC_PREFIX`
- default: empty string
- missing env must not fail startup
- producer topic: `prefix + base`
- consumer topic: `prefix + base`
- `@KafkaListener` literal/placeholder topic also uses resolver
- hardcoded topic constants must not bypass prefix
- consumer group name remains unchanged
- Schema Registry subject is separated by topic name automatically
- schema itself does not change
- DLT/DLQ topic also follows prefixed source topic

## Current Platform Topic Configuration

`src/main/resources/application.yml` currently defines base topics under `app.kafka.topics`:

```yaml
app:
  kafka:
    topics:
      user-registered: platform.auth.user-registered-v1
      notification-send: ${KAFKA_TOPIC_NOTIFICATION_SEND:platform.notification.notification-send-v1}
      note-created: ${KAFKA_TOPIC_NOTE_CREATED:knowledge.note.note-created-v1}
      note-updated: ${KAFKA_TOPIC_NOTE_UPDATED:knowledge.note.note-updated-v1}
      review-completed: ${KAFKA_TOPIC_REVIEW_COMPLETED:learning.card.review-completed-v1}
      badge-earned: ${KAFKA_TOPIC_BADGE_EARNED:engagement.gamification.badge-earned-v1}
      level-up: ${KAFKA_TOPIC_LEVEL_UP:engagement.gamification.level-up-v1}
```

`KafkaTopicProperties` previously had only:
- `dlqSuffix = ".DLT"`
- `userRegistered`
- `notificationSend`

But audit listeners also use:
- `note-created`
- `note-updated`
- `review-completed`
- `badge-earned`
- `level-up`

These are now first-class properties and resolver targets so topic calculation stays centralized.

## Current Producer Usage
- `UserEventPublisher`: publishes `UserRegistered` to `app.kafka.topics.user-registered`
- `KafkaPasswordResetCodeSender`: publishes `NotificationSend` to `app.kafka.topics.notification-send`

Both now receive `KafkaTopicResolver` and store resolved topics.

## Current Consumer Usage
- `NotificationKafkaConsumer`: consumes `app.kafka.topics.notification-send`
- `AuditKafkaConsumer` consumes:
  - `user-registered`
  - `note-created`
  - `note-updated`
  - `review-completed`
  - `badge-earned`
  - `level-up`

All listener topic expressions now resolve through `KafkaTopicResolver` SpEL.

## DLT/DLQ Behavior
`KafkaErrorHandlerConfig` currently uses:

```java
record.topic() + topicProperties.getDlqSuffix()
```

If listeners subscribe to `dev.<base>`, DLT/DLQ publishes to `dev.<base>.dlq` with the new standard suffix.

Decision:
- Align with #102/shared standard and use `.dlq` by default.
- Keep `KAFKA_TOPIC_DLQ_SUFFIX` override for operational fallback.

## Known Gap
#102 mentions `notification-send` in audit coverage. This work adds a `NotificationSend` audit listener and mapper.

Mapping:
- action: `NOTIFICATION_SEND`
- resourceType: `USER`
- resourceId: notification recipient `userId`
- newValue: redacted JSON payload only

Notification content is intentionally not persisted in audit logs. `body`, `emailHtmlBody`, and `data` can contain reset codes or user message content, so audit storage keeps only routing/metadata fields and `contentRedacted=true`.

`learning.card.review-due-v1` is not added because platform currently has no ReviewDue Avro schema/consumer and earlier Step 7 docs mark that bridge outside platform-svc scope. A platform-side check found no `ReviewDue` schema or main-code consumer, so direct consumption needs an owner contract/schema first.

## Proposed Design
Added a central resolver bean:

```java
@Component("kafkaTopicResolver")
public class KafkaTopicResolver {
    public String userRegistered() { ... }
    public String notificationSend() { ... }
    public String noteCreated() { ... }
    public String noteUpdated() { ... }
    public String reviewCompleted() { ... }
    public String badgeEarned() { ... }
    public String levelUp() { ... }
    public String dltTopic(String sourceTopic) { ... }
}
```

Listeners use SpEL:

```java
@KafkaListener(topics = "#{@kafkaTopicResolver.notificationSend()}", ...)
```

Producers use resolver injection:

```java
kafkaTopicResolver.userRegistered()
```

## Risk Review

### Main Risks
- A listener remains on an unprefixed topic while producer moves to prefixed topic.
- Producer remains unprefixed while consumer moves to prefixed topic.
- Test `application.yml` shadows main config and misses prefix binding.
- DLT/DLQ suffix is changed unintentionally.
- Audit topic coverage is expanded without matching event mapper/idempotency expectation.

### Risk Controls
- Central resolver only.
- Tests for empty prefix and `dev.` prefix.
- Static search for topic placeholders/literals after change.
- Keep consumer group IDs unchanged.
- Do not edit other repos.

## Verification Strategy
1. Unit test resolver with empty prefix.
2. Unit test resolver with `dev.` prefix.
3. Confirm producers use resolver topics.
4. Confirm `@KafkaListener` annotations use resolver SpEL.
5. Confirm DLT/DLQ target uses prefixed source topic.
6. Run targeted Kafka config tests.
7. Run `clean build`.
8. Run `git diff --check`.

## Related Issues
- #102: current task
- #101: Prometheus actuator exposure, merged to dev and closed
- #62: W5 E2E umbrella
