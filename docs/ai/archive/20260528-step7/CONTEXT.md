# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

### 코드베이스 현황
- **Flyway 최신 버전**: V30 (`create_outbox_events.sql`) → 신규 마이그레이션은 **V31**
- **notification 모듈**: DeviceToken 등록/해제만 구현됨 (Step 5). 발송 기능 없음.
- **audit 모듈**: `AuditKafkaConsumer` + `AuditLogService` 구현됨 → **Consumer 패턴 참조 대상**
- **KafkaConsumerConfig**: `auditConsumerFactory` + `auditKafkaListenerContainerFactory` 패턴 존재 → 동일 패턴으로 `notificationConsumerFactory` 추가
- **KafkaErrorHandlerConfig**: `defaultErrorHandler` = FixedBackOff(1000ms, 3회) → notification은 **별도** `notificationErrorHandler` (ExponentialBackOff) 필요
- **KafkaTopicProperties**: `dlqSuffix`(`.DLT`), `userRegistered` 2개 필드만 존재 → `notificationSend` 추가 필요
- **PlatformAvroEvents**: `USER_REGISTERED_TYPE` + `CloudEvent`/`UserRegistered` 스키마 → `NOTIFICATION_SEND_TYPE` + `NotificationSend` 스키마 추가 필요
- **Firebase Admin SDK, AWS SES SDK**: **build.gradle.kts에 없음** → 추가 필수
- **UserApi**: `notification` 모듈이 이미 `DeviceTokenService`에서 사용 중 → 의존성 추가 불필요

### 확정된 설계 결정
- **토픽 이름**: `platform.notification.notification-send-v1` (Rule 8.1 준수)
- **Consumer group**: `notification-consumer-group`
- **notifications 테이블 PK**: `UNIQUE (event_id, channel)` → 멱등성 키
- **FCM/SES 비활성화**: `@ConditionalOnProperty(name="app.fcm.enabled", havingValue="true")` → 테스트에서 MockBean 사용
- **재시도**: `ExponentialBackOff(1000L, 2.0)`, `maxElapsedTime = 7000L` (1s+2s+4s 총 3회 후 DLT)
- **DLT suffix**: `.DLT` (기존 `defaultErrorHandler` 기준과 동일)
- **card.review.due bridge**: platform-svc 범위 밖. 테스트에서는 learning-card-owner가 발행한 것으로 가정한 `notificationType = "CARD_REVIEW_DUE"` mock `notification.send` 이벤트를 직접 publish한다.
- **비활성 채널 처리**: FCM/SES 서비스 빈이 없으면 해당 채널은 레코드 생성 없이 skip한다. 발송하지 않은 알림을 `SENT`로 저장하지 않는다.
- **NotificationService 의존성 주입**: `@Autowired(required = false)` 필드 주입 금지. `ObjectProvider<FcmPushService>`, `ObjectProvider<SesEmailService>` 생성자 주입 사용.

## 현재 미결 사항

- (없음)

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token: Redis 전용, DB 저장 금지
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- Entity @Setter 금지 → 도메인 메서드(`markSent()`, `markFailed()`, `incrementAttempts()`)만 허용
- `@ApplicationModule` 없는 새 패키지 금지
- 일일 이메일 발송 한도: 사용자당 10건 (NotificationService에서 검증)

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md Step 7
- docs/rules/08-kafka-event.md (Consumer 멱등성, DLQ, CloudEvent 규칙)
- docs/rules/07-platform-spring.md (Entity, DTO, Validation 규칙)
- src/main/java/com/synapse/platform/audit/ (Consumer 구현 참조)
- src/main/java/com/synapse/platform/global/kafka/ (Kafka 인프라 참조)
