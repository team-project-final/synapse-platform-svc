# PLAT-102 Handoff

## Status
로컬 구현 및 검증 완료. PR 전.

## Branch
- Base: `origin/dev`
- Working branch: `fix/PLAT-102-kafka-topic-prefix`
- Target PR branch: `dev`

## Archived
이전 PLAT-101 current 문서는 아래 경로에 보존했다.

- `docs/ai/archive/20260612-plat-101-completed/TASK.md`
- `docs/ai/archive/20260612-plat-101-completed/CONTEXT.md`
- `docs/ai/archive/20260612-plat-101-completed/HANDOFF.md`

## Current Task
platform 이슈 #102를 처리한다. `KAFKA_TOPIC_PREFIX`를 읽어 platform 서비스의 Kafka producer, consumer, audit consumer, DLT/DLQ topic을 환경별 prefixed topic으로 계산한다.

## Must Preserve
- `KAFKA_TOPIC_PREFIX` 미설정 시 기존 unprefixed topic 사용
- startup must not fail when prefix env is absent
- consumer group names unchanged
- schema unchanged
- other repos untouched
- `TASK_platform.md` untouched

## Implementation Checklist
- [x] `KafkaTopicProperties`에 `prefix` 및 누락 topic fields 추가
- [x] `KafkaTopicResolver` 추가
- [x] `UserEventPublisher` topic 주입을 resolver로 변경
- [x] `KafkaPasswordResetCodeSender` topic 주입을 resolver로 변경
- [x] `NotificationKafkaConsumer` topic을 resolver SpEL로 변경
- [x] `AuditKafkaConsumer` topic을 resolver SpEL로 변경
- [x] DLT/DLQ topic 계산 검토 및 테스트
- [x] prefix empty/default 테스트 추가
- [x] prefix `dev.` 테스트 추가
- [x] 하드코딩 topic literal/placeholder 잔여 검색
- [x] `docs/project-management/history/HISTORY_platform.md` 작업 이력 기록
- [x] 단일 테스트 실행
- [x] `clean build` 실행
- [x] `git diff --check` 실행

## Decisions

### DLT/DLQ suffix
현재 platform은 `.DLT`, #102/shared 표준은 `.dlq`다.

결정:
- #102/shared 표준에 맞춰 `.dlq`로 정렬한다.
- `KAFKA_TOPIC_DLQ_SUFFIX` override는 유지한다.

### notification-send audit
#102는 audit 대상에 `notification-send`를 언급한다. 현재 platform에는 notification-send audit listener가 없다.

결정:
- 이번 범위에서 `NotificationSend` audit listener와 `AuditLogService.processEvent(NotificationSend)`를 추가한다.
- 감사 로그 payload는 본문/HTML/data를 제외한 redacted JSON으로 저장한다.
- 비밀번호 재설정 알림처럼 민감 값이 들어갈 수 있는 `emailHtmlBody`, `body`, `data`는 저장하지 않는다.

### review-due
현재 platform에는 `ReviewDue` schema/consumer가 없고, Step 7 기존 문서에서 `card.review.due -> notification.send` bridge는 platform-svc 범위 밖으로 정리돼 있다.

결정:
- 신규 `review-due` consumer는 만들지 않는다.
- 기존 `NotificationSend` 소비/audit 경로에 prefix를 적용한다.
- platform main 코드와 Avro schema에 `ReviewDue`가 없어 platform 단독 직접 소비 구현은 보류한다.

## Exact Change Targets

```text
src/main/resources/application.yml
src/test/resources/application.yml
src/main/java/com/synapse/platform/global/kafka/KafkaTopicProperties.java
src/main/java/com/synapse/platform/global/kafka/KafkaTopicResolver.java
src/main/java/com/synapse/platform/global/kafka/KafkaErrorHandlerConfig.java
src/main/java/com/synapse/platform/auth/event/UserEventPublisher.java
src/main/java/com/synapse/platform/auth/service/KafkaPasswordResetCodeSender.java
src/main/java/com/synapse/platform/notification/consumer/NotificationKafkaConsumer.java
src/main/java/com/synapse/platform/audit/consumer/AuditKafkaConsumer.java
src/test/java/com/synapse/platform/global/kafka/*
src/test/java/com/synapse/platform/auth/event/UserEventPublisherTest.java
src/test/java/com/synapse/platform/auth/service/KafkaPasswordResetCodeSenderTest.java
docs/project-management/history/HISTORY_platform.md
docs/ai/current/*
```

## Do Not Change
- shared/gitops/knowledge/learning/engagement/frontend repos
- `.env`
- Spring profile files
- Avro schema files
- consumer group IDs
- `TASK_platform.md`

## Test Commands

```powershell
.\gradlew.bat test --tests "*KafkaConsumerConfigSmokeTest"
.\gradlew.bat test --tests "*UserEventPublisherTest"
.\gradlew.bat test --tests "*KafkaPasswordResetCodeSenderTest"
.\gradlew.bat test --tests "*KafkaTopic*"
.\gradlew.bat clean build
git diff --check
```

현재 실행 결과:
- `.\gradlew.bat test --tests "*KafkaTopicResolverTest" --tests "*KafkaConsumerConfigSmokeTest" --tests "*UserEventPublisherTest" --tests "*KafkaPasswordResetCodeSenderTest"`: PASS
- `.\gradlew.bat test --tests "*AuditConsumerIntegrationTest" --tests "*NotificationKafkaConsumerIT"`: PASS
- `.\gradlew.bat cleanTest test --tests "*AuditLogServiceTest" --tests "*AuditConsumerIntegrationTest"`: PASS
- `.\gradlew.bat checkstyleTest`: PASS
- `.\gradlew.bat spotbugsMain`: PASS
- `.\gradlew.bat clean build`: PASS
- `git diff --check`: PASS

참고:
- Windows EmbeddedKafka 종료 시 임시 파일 삭제 경고가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.

## PR
- Title: `fix(infra): Kafka 토픽 환경 프리픽스 적용 (#102)`
- Body related issue: `Closes #102`
- Target: `dev`
