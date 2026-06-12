# PLAT-102 Kafka 토픽 환경 프리픽스 적용

## Task ID
PLAT-102

## Title
`KAFKA_TOPIC_PREFIX` 기반 Kafka 발행/구독 토픽 환경 격리

## Owner
platform

## Status
IMPLEMENTED_LOCAL

## Priority
P1

## Branch
`fix/PLAT-102-kafka-topic-prefix`

## Base
`origin/dev`

## Issue
platform 이슈 #102: `[#199] Kafka 토픽 환경 프리픽스 적용 (KAFKA_TOPIC_PREFIX) - 발행·소비·감사 컨슈머 전체`

## Step Goal
dev/staging/prod가 같은 MSK를 공유하더라도 Kafka 토픽이 환경별로 분리되도록 platform 서비스의 모든 발행/구독 토픽명을 `${KAFKA_TOPIC_PREFIX}<base-topic>` 형태로 계산한다.

프리픽스가 없으면 기존 미접두 토픽을 그대로 사용해 로컬, 테스트, 하위호환을 유지한다. 토픽명 계산은 단일 resolver/config 지점으로 모아 producer, consumer, DLT/DLQ 경로가 서로 다르게 계산되지 않도록 한다.

## Done When
- [x] `KAFKA_TOPIC_PREFIX`가 기본값 `""`로 바인딩된다.
- [x] producer 토픽명이 `prefix + base`로 계산된다.
- [x] 모든 `@KafkaListener(topics=...)`가 중앙 resolver의 prefixed topic을 사용한다.
- [x] notification consumer 토픽이 prefixed topic을 사용한다.
- [x] audit consumer 토픽이 prefixed topic을 사용한다.
- [x] DLT/DLQ 토픽이 prefixed source topic 기준으로 계산된다.
- [x] 토픽 리터럴/placeholder가 여러 곳에 흩어지지 않고 단일 resolver/config로 정리된다.
- [x] prefix 미설정 시 기존 base topic을 유지하는 테스트가 있다.
- [x] `KAFKA_TOPIC_PREFIX=dev.` 설정 시 `dev.<base-topic>`으로 계산되는 테스트가 있다.
- [x] `notification-send` audit payload는 본문/HTML/data를 저장하지 않도록 마스킹한다.
- [x] `review-due` 직접 소비가 platform에서 처리 가능한지 확인한다.
- [x] `git diff --check`를 통과한다.
- [x] 관련 단일 테스트와 `clean build`가 통과한다.

## Scope

### In Scope
- `KafkaTopicProperties`
- 신규 Kafka topic resolver/config bean
- `UserEventPublisher`
- `KafkaPasswordResetCodeSender`
- `NotificationKafkaConsumer`
- `AuditKafkaConsumer`
- `KafkaErrorHandlerConfig`
- Kafka topic 관련 테스트
- 작업 이력 문서 갱신

### Out of Scope
- shared/gitops/knowledge/learning/engagement/frontend 레포 수정
- Kafka topic 생성 스크립트 수정
- Schema Registry 스키마 변경
- consumer group 이름 변경
- `.env` 또는 Spring profile 파일 수정
- `TASK_platform.md` 수정
- #62 W5 라이브 E2E 실행

## Current Evidence
- gitops 이슈 #199: dev/staging이 같은 MSK, 같은 topic, 같은 group으로 소비하면서 파티션이 환경 간 교차 분산됨.
- gitops PR #206: `KAFKA_TOPIC_PREFIX`를 dev/staging/prod overlay에 주입하고 prefixed topic을 생성하도록 merge 완료.
- shared PR #72: `EVENT_CONTRACT_STANDARD §2.1`에 topic 환경 prefix 표준 추가 후 merge 완료.
- platform 이슈 #102: platform 서비스가 앱 측 producer/consumer/audit/DLQ 적용을 담당.

현재 platform 코드의 영향 지점:
- `src/main/resources/application.yml`
- `src/test/resources/application.yml`
- `src/main/java/com/synapse/platform/global/kafka/KafkaTopicProperties.java`
- `src/main/java/com/synapse/platform/auth/event/UserEventPublisher.java`
- `src/main/java/com/synapse/platform/auth/service/KafkaPasswordResetCodeSender.java`
- `src/main/java/com/synapse/platform/notification/consumer/NotificationKafkaConsumer.java`
- `src/main/java/com/synapse/platform/audit/consumer/AuditKafkaConsumer.java`
- `src/main/java/com/synapse/platform/global/kafka/KafkaErrorHandlerConfig.java`

## Implementation Decisions

### DLT/DLQ suffix
표준과 #102 본문은 `<topic>.dlq`를 기준으로 설명한다. 현재 platform 구현은 `KafkaTopicProperties.dlqSuffix = ".DLT"`를 사용한다.

결정:
- #102/shared 표준에 맞춰 기본 suffix를 `.dlq`로 변경한다.
- `KAFKA_TOPIC_DLQ_SUFFIX`로 override 가능한 설정은 유지한다.

### Audit notification-send
#102 본문은 audit 대상에 `notification-send`도 포함한다. 현재 `AuditKafkaConsumer`와 `AuditLogService`에는 `NotificationSend` 감사 처리 메서드가 없다.

결정:
- #102 범위에서 `notification-send` audit listener와 mapper를 추가한다.
- 감사 로그에는 `eventId`, `tenantId`, `occurredAt`, `traceparent`, `userId`, `notificationType`, `channels`, `contentRedacted`만 저장한다.
- `body`, `emailHtmlBody`, `data`는 비밀번호 재설정 코드 등 민감 정보가 포함될 수 있어 저장하지 않는다.

### review-due consumer
#102 본문에는 `learning.card.review-due-v1`도 언급되어 있으나 현재 platform에는 해당 Avro schema/consumer가 없다.

결정:
- 이번 PR에서는 기존 구현된 `NotificationSend` 소비 경로와 audit 경로에 prefix를 적용한다.
- `review-due -> notification-send` bridge는 기존 Step 7 문서 기준 platform-svc 범위 밖으로 정리된 상태라 신규 consumer를 만들지 않는다.
- 추가 확인 결과 `src/main/avro`와 `src/main`에는 `ReviewDue` schema/consumer가 없으므로 platform 단독으로 직접 소비를 구현하지 않는다.

## Implementation Plan
1. `KafkaTopicProperties`에 `prefix`와 모든 base topic 필드를 명시한다.
2. `KAFKA_TOPIC_PREFIX`는 기본값 `""`로 설정하고, 값이 없으면 base topic을 그대로 반환한다.
3. `KafkaTopicResolver`를 추가해 `userRegistered()`, `notificationSend()`, `noteCreated()` 등 prefixed topic getter를 제공한다.
4. producer는 `@Value("${app.kafka.topics...}")` 직접 주입 대신 resolver를 사용한다.
5. `@KafkaListener`는 SpEL `#{@kafkaTopicResolver...}` 형태로 resolver를 사용한다.
6. `KafkaErrorHandlerConfig`는 prefixed source topic에 suffix를 붙이는 방식으로 DLT/DLQ 경로를 유지한다.
7. prefix 미설정/설정 케이스 테스트를 보강한다.
8. history/current 문서를 갱신한다.

## Test Plan
우선 관련 테스트:

```powershell
.\gradlew.bat test --tests "*KafkaConsumerConfigSmokeTest"
.\gradlew.bat test --tests "*UserEventPublisherTest"
.\gradlew.bat test --tests "*KafkaPasswordResetCodeSenderTest"
```

필요 시 추가/수정되는 topic resolver 테스트:

```powershell
.\gradlew.bat test --tests "*KafkaTopic*"
```

최종 검증:

```powershell
.\gradlew.bat clean build
git diff --check
```

## Test Results
- `.\gradlew.bat test --tests "*KafkaTopicResolverTest" --tests "*KafkaConsumerConfigSmokeTest" --tests "*UserEventPublisherTest" --tests "*KafkaPasswordResetCodeSenderTest"`: PASS
- `.\gradlew.bat test --tests "*AuditConsumerIntegrationTest" --tests "*NotificationKafkaConsumerIT"`: PASS
- `.\gradlew.bat cleanTest test --tests "*AuditLogServiceTest" --tests "*AuditConsumerIntegrationTest"`: PASS
- `.\gradlew.bat checkstyleTest`: PASS
- `.\gradlew.bat spotbugsMain`: PASS
- `.\gradlew.bat clean build`: PASS
- `git diff --check`: PASS

Notes:
- Windows 환경에서 EmbeddedKafka 종료 시 임시 파일 삭제 경고 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.

## PR Plan
- Target branch: `dev`
- PR title: `fix(infra): Kafka 토픽 환경 프리픽스 적용 (#102)`
- Related issue: `Closes #102`

## Do Not Touch
- shared/gitops/knowledge/learning/engagement/frontend 레포
- `.env`
- Spring profile 파일
- Schema `.avsc`
- consumer group 이름
- `TASK_platform.md`
