# TASK — Step 7: FCM 푸시 + SES 이메일 알림 발송

> 출처: TASK_platform.md Step 7

## 상태

- Phase: 구현
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-28
- 목표 완료일: 2026-05-29

---

## Step Goal

사용자에게 `notification.send` 토픽 이벤트 기반 FCM 푸시와 SES 이메일 알림이 발송된다.

## Done When

- [ ] `notification.send` 토픽 이벤트 수신 → FCM 푸시 발송 (gamification/community 토픽 직접 소비 아님)
- [ ] `notification.send` 토픽 이벤트 수신 → SES 이메일 발송
- [ ] `card.review.due` 이벤트 → `notification.send` 토픽 경유 → FCM 푸시 + SES 이메일 발송
- [ ] 알림 발송 이력 저장 (`notifications` 테이블)
- [ ] 발송 실패 시 재시도 로직 동작 (exponential backoff, 최대 3회)
- [ ] 통합 테스트 통과

## Scope

- In Scope:
  - Kafka Consumer (`notification.send` 토픽 구독)
  - FCM 푸시 발송 서비스 (Firebase Admin SDK)
  - SES 이메일 발송 서비스 (AWS SES SDK)
  - `notifications` 테이블 + Flyway V31 마이그레이션
  - 발송 이력 저장 및 멱등성 보장
  - 발송 실패 재시도 (exponential backoff 1s/2s/4s, max 3회)
  - 통합 테스트 (Mock FCM/SES)
- Out of Scope:
  - 알림 설정(사용자 선호도) 관리
  - SMS 알림
  - 실시간 웹소켓 알림
  - gamification/community 토픽 직접 소비

## Input

- 기존 `DeviceTokenRepository` (userId → 활성 FCM 토큰 목록)
- 기존 `UserApi` (userId → email)
- 기존 Kafka 인프라 (`auditConsumerFactory` 패턴 참조)
- Step 6에서 확립된 CloudEvent envelope 패턴 (`PlatformAvroEvents`)

## Instructions

1. `V31__create_notifications.sql` 마이그레이션 작성
2. `Notification` entity + `NotificationStatus` enum + `NotificationChannel` enum
3. `NotificationRepository` 작성
4. `PlatformAvroEvents`에 `NotificationSend` 스키마 + 헬퍼 메서드 추가
5. `KafkaTopicProperties`에 `notificationSend` 필드 추가
6. `KafkaErrorHandlerConfig`에 `notificationErrorHandler` 빈 추가 (ExponentialBackOff)
7. `KafkaConsumerConfig`에 `notificationConsumerFactory` + `notificationKafkaListenerContainerFactory` 빈 추가
8. `FcmConfig` (ConditionalOnProperty) + `SesConfig` (ConditionalOnProperty) 작성
9. `FcmPushService` + `SesEmailService` 작성
10. `NotificationService` 작성 (라우팅 + 멱등성 + 이력 저장)
11. `NotificationKafkaConsumer` 작성
12. `build.gradle.kts` 의존성 추가
13. `application.yml` 설정 추가
14. 통합 테스트 + 단위 테스트 작성

## Output Format

`notification` 모듈 발송 코드 + Kafka Consumer + 테스트 코드

## Constraints

- FCM 발송 지연: 이벤트 수신 후 10초 이내
- SES 발송: 이벤트 수신 후 30초 이내
- 재시도: exponential backoff (1s, 2s, 4s)
- 일일 이메일 발송 한도: 사용자당 10건
- `@ConditionalOnProperty`로 FCM/SES 비활성화 가능 (테스트 환경)
- `card.review.due` bridge는 platform-svc 범위 밖. 통합 테스트에서는 `notificationType = "CARD_REVIEW_DUE"`인 mock `notification.send` 이벤트를 직접 발행해 platform-svc 책임 범위만 검증한다.
- FCM/SES 서비스 빈이 없으면 레코드 생성 없이 skip한다. 발송하지 않은 알림을 `SENT`로 저장하지 않는다.
- `NotificationService`는 `ObjectProvider<FcmPushService>`, `ObjectProvider<SesEmailService>` 생성자 주입을 사용한다.

## Duration

2일 (2026-05-28 ~ 2026-05-29)

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
