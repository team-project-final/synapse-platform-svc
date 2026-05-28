# HANDOFF — Step 7: FCM 푸시 + SES 이메일 알림 발송

## FROM

Director (Claude)

## TO

Worker (Codex)

## 날짜

2026-05-28

---

## 0. 작업 개요

`notification.send` Kafka 토픽을 구독하여 FCM 푸시 / SES 이메일 알림을 발송하는 기능을 구현한다.

- 브랜치: `feature/PLAT-013-notification-fcm-ses` (이미 생성됨)
- 패키지 루트: `com.synapse.platform.notification`
- 테스트: 신규 코드 커버리지 80% 이상 필수

### 구현 전 확정 사항 (Director 검토 완료)

**[1] card.review.due → notification.send bridge는 platform-svc 범위 밖**

Done When의 "card.review.due 이벤트 → notification.send 토픽 경유"에서
`경유`는 learning-card-owner 서비스가 card.review.due 이벤트 수신 후
`notification.send` 토픽에 직접 발행하는 것을 의미한다.
platform-svc는 `notification.send` 토픽만 구독한다 (Scope 명시).
→ **bridge 구현 불필요.** 통합 테스트에서 `notificationType = "CARD_REVIEW_DUE"`인
  `notification.send` 이벤트를 직접 발행하여 Done When을 충족시킨다.
  즉 테스트에서는 learning-card-owner가 발행한 것으로 가정한 mock `notification.send`
  이벤트를 publish하고, platform-svc 범위의 책임인 FCM/SES 발송 및 notifications
  저장만 검증한다.

**[2] FCM/SES 비활성화 시 Notification 레코드 생성 안 함**

`app.fcm.enabled=false` 상태에서 `FcmPushService` 빈이 없을 때,
현재 명세의 warn+return 이후 `markSent()` 호출은 "발송 안 됐는데 SENT" 문제를 유발한다.
→ `sendToChannel()` 진입 시 서비스 빈 null 체크 → null이면 레코드 생성 없이 return.
  이 경우 `notifications` 테이블에 아무 레코드도 남지 않는다 (skip 시맨틱).

**[3] ObjectProvider 생성자 주입 사용**

`@Autowired(required = false)` 필드 주입 대신
`ObjectProvider<FcmPushService>` + `ObjectProvider<SesEmailService>`를 생성자로 받는다.
테스트에서 `@MockBean`은 정상 빈으로 등록되므로 `getIfAvailable()` → non-null 반환.
비활성화 환경에서는 `getIfAvailable()` → null 반환 → [2] 흐름으로 처리.

**[4] SDK 의존성 다운로드**

Firebase Admin SDK 9.4.1 / AWS SES SDK v2(sesv2:2.26.29)는 Maven Central에 존재.
첫 빌드 시 네트워크 접근 필요. 오프라인 환경이면 `--offline` 플래그로 확인 후 진행.

**[5] ExponentialBackOff maxElapsedTime 수정**

`maxElapsedTime=7500L` 버그 수정: Spring `ExponentialBackOff`는 interval을
누적 후 다음 호출 시 `elapsed >= maxElapsedTime` 체크.
- elapsed 합계: 1s+2s+4s = 7000ms. 7000 < 7500 → 4번째 재시도 발생 (의도 초과)
- 수정값: `maxElapsedTime = 7000L` → elapsed 7000 >= 7000 → STOP (정확히 3회)

---

## 1. 현재 코드베이스 상태 (입력)

### notification 모듈 기존 파일 (건드리지 말 것)

```
notification/
├── config/NotificationSecurityConfig.java   (@Order(1) Security 필터)
├── controller/DeviceTokenController.java    (POST/DELETE /api/v1/notifications/devices)
├── entity/DeviceToken.java                  (device_tokens 테이블)
├── entity/Platform.java                     (enum: IOS, ANDROID, WEB)
├── entity/PlatformConverter.java
├── exception/DeviceRegistrationLimitExceededException.java
├── repository/DeviceTokenRepository.java    (findByUserId, findByToken, countByUserId, upsert)
└── service/DeviceTokenService.java          (register, unregister, UserApi 사용)
```

### Kafka 인프라 기존 파일

```
global/kafka/
├── KafkaConsumerConfig.java      → auditConsumerFactory + auditKafkaListenerContainerFactory 패턴
├── KafkaErrorHandlerConfig.java  → defaultErrorHandler (FixedBackOff 1000ms, 3회)
├── KafkaTopicProperties.java     → dlqSuffix(".DLT"), userRegistered 2개 필드
├── KafkaProducerConfig.java
└── event/PlatformAvroEvents.java → CLOUD_EVENT_SCHEMA + USER_REGISTERED_SCHEMA + 헬퍼 메서드
```

### Flyway 최신 버전: V30 (`create_outbox_events.sql`) → 신규는 **V31**

---

## 2. 구현 목록

### 2-A. 수정할 파일 (6개)

| 파일 | 변경 내용 |
|------|----------|
| `build.gradle.kts` | Firebase Admin SDK, AWS SES SDK 의존성 추가 |
| `src/main/resources/application.yml` | notification-send 토픽, fcm, ses 설정 추가 |
| `global/kafka/KafkaTopicProperties.java` | `notificationSend` 필드 추가 |
| `global/kafka/KafkaErrorHandlerConfig.java` | `notificationErrorHandler` 빈 추가 (ExponentialBackOff) |
| `global/kafka/KafkaConsumerConfig.java` | `notificationConsumerFactory` + `notificationKafkaListenerContainerFactory` 빈 추가 |
| `global/kafka/event/PlatformAvroEvents.java` | `NOTIFICATION_SEND_TYPE` 상수 + `NOTIFICATION_SEND_SCHEMA` + 헬퍼 메서드 2개 추가 |

### 2-B. 새로 만들 파일

```
src/main/resources/db/migration/
└── V31__create_notifications.sql

src/main/java/com/synapse/platform/notification/
├── config/
│   ├── FcmConfig.java
│   └── SesConfig.java
├── consumer/
│   └── NotificationKafkaConsumer.java
├── entity/
│   ├── Notification.java
│   ├── NotificationChannel.java  (enum)
│   └── NotificationStatus.java   (enum)
├── exception/
│   └── SesEmailException.java
├── repository/
│   └── NotificationRepository.java
└── service/
    ├── FcmPushService.java
    ├── NotificationService.java
    └── SesEmailService.java

src/test/java/com/synapse/platform/notification/
├── consumer/NotificationKafkaConsumerIT.java
└── service/
    ├── FcmPushServiceTest.java
    ├── NotificationServiceTest.java
    └── SesEmailServiceTest.java
```

---

## 3. 상세 구현 명세

### 3-1. build.gradle.kts 추가

```kotlin
// Firebase Admin SDK
implementation("com.google.firebase:firebase-admin:9.4.1")

// AWS SES SDK v2
implementation("software.amazon.awssdk:sesv2:2.26.29")
```

---

### 3-2. application.yml 추가

기존 `app.kafka.topics` 블록 확장 + fcm/ses 설정 추가:

```yaml
app:
  kafka:
    topics:
      notification-send: ${KAFKA_TOPIC_NOTIFICATION_SEND:platform.notification.notification-send-v1}
  fcm:
    enabled: ${FCM_ENABLED:false}
    service-account-path: ${FCM_SERVICE_ACCOUNT_PATH:}
    project-id: ${FCM_PROJECT_ID:}
  ses:
    enabled: ${SES_ENABLED:false}
    region: ${SES_REGION:ap-northeast-2}
    from-email: ${SES_FROM_EMAIL:noreply@synapse.app}
```

`application-test.yml`에 추가 (테스트 환경에서 실제 SDK 불필요):
```yaml
app:
  fcm:
    enabled: false
  ses:
    enabled: false
```

---

### 3-3. V31__create_notifications.sql

```sql
CREATE TABLE notifications (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id          UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    tenant_id         UUID        NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel           VARCHAR(20) NOT NULL CHECK (channel IN ('FCM', 'EMAIL')),
    title             VARCHAR(500),
    body              TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts          INT         NOT NULL DEFAULT 0,
    error_message     TEXT,
    sent_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_notifications_user_id    ON notifications (user_id);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
CREATE INDEX idx_notifications_status     ON notifications (status);
```

---

### 3-4. NotificationStatus.java (enum)

```java
package com.synapse.platform.notification.entity;

public enum NotificationStatus {
    PENDING, SENT, FAILED
}
```

---

### 3-5. NotificationChannel.java (enum)

```java
package com.synapse.platform.notification.entity;

public enum NotificationChannel {
    FCM, EMAIL
}
```

---

### 3-6. Notification.java (Entity)

규칙: `@Setter` 절대 금지. 상태 변경은 도메인 메서드만.

```java
package com.synapse.platform.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant sentAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = NotificationStatus.PENDING;
    }

    public static Notification create(UUID eventId, UUID userId, UUID tenantId,
                                      String notificationType, NotificationChannel channel,
                                      String title, String body) {
        Notification n = new Notification();
        n.eventId = eventId;
        n.userId = userId;
        n.tenantId = tenantId;
        n.notificationType = notificationType;
        n.channel = channel;
        n.title = title;
        n.body = body;
        n.status = NotificationStatus.PENDING;
        n.attempts = 0;
        return n;
    }

    public void incrementAttempts() { this.attempts++; }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public boolean isSent() { return NotificationStatus.SENT == this.status; }
}
```

---

### 3-7. NotificationRepository.java

```java
package com.synapse.platform.notification.repository;

import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE n.userId = :userId
          AND n.channel = com.synapse.platform.notification.entity.NotificationChannel.EMAIL
          AND n.status = com.synapse.platform.notification.entity.NotificationStatus.SENT
          AND n.sentAt >= :dayStart
    """)
    long countTodayEmailByUserId(UUID userId, Instant dayStart);
}
```

---

### 3-8. PlatformAvroEvents.java — 추가할 내용

기존 파일 맨 아래(private constructor 위)에 아래 내용 추가.

**상수:**
```java
public static final String NOTIFICATION_SEND_TYPE =
    "com.synapse.event.platform.NotificationSend";
```

**스키마 (기존 USER_REGISTERED_SCHEMA 아래):**
```java
private static final Schema NOTIFICATION_SEND_SCHEMA = new Schema.Parser().parse("""
    {
      "type": "record",
      "name": "NotificationSend",
      "namespace": "com.synapse.event.platform",
      "fields": [
        {"name": "userId",           "type": "string",  "default": ""},
        {"name": "tenantId",         "type": "string",  "default": ""},
        {"name": "notificationType", "type": "string",  "default": ""},
        {"name": "channels",
         "type": {"type": "array", "items": "string"},  "default": []},
        {"name": "title",            "type": "string",  "default": ""},
        {"name": "body",             "type": "string",  "default": ""},
        {"name": "emailSubject",
         "type": ["null", "string"], "default": null},
        {"name": "emailHtmlBody",
         "type": ["null", "string"], "default": null},
        {"name": "data",
         "type": {"type": "map", "values": "string"},   "default": {}}
      ]
    }
    """);
```

**헬퍼 메서드 2개:**
```java
public static GenericRecord decodeNotificationSend(GenericRecord envelope) {
    Object data = envelope.get("data");
    if (!(data instanceof ByteBuffer buffer)) {
        throw new IllegalArgumentException("CloudEvent data must be bytes");
    }
    return decode(toByteArray(buffer), NOTIFICATION_SEND_SCHEMA);
}

// 통합 테스트에서 test 이벤트를 직접 생성할 때 사용
public static GenericRecord notificationSendEnvelope(
        UUID userId, UUID tenantId,
        String notificationType, java.util.List<String> channels,
        String title, String body,
        String emailSubject, String emailHtmlBody) {
    long now = Instant.now().toEpochMilli();

    GenericRecord payload = new GenericData.Record(NOTIFICATION_SEND_SCHEMA);
    payload.put("userId", userId.toString());
    payload.put("tenantId", tenantId.toString());
    payload.put("notificationType", notificationType);
    payload.put("channels", channels);
    payload.put("title", title);
    payload.put("body", body);
    payload.put("emailSubject", emailSubject);
    payload.put("emailHtmlBody", emailHtmlBody);
    payload.put("data", new java.util.HashMap<String, String>());

    GenericRecord envelope = new GenericData.Record(CLOUD_EVENT_SCHEMA);
    envelope.put("specversion", "1.0");
    envelope.put("id", UUID.randomUUID().toString());
    envelope.put("source", "learning-service");
    envelope.put("type", NOTIFICATION_SEND_TYPE);
    envelope.put("time", now);
    envelope.put("tenantid", tenantId.toString());
    envelope.put("datacontenttype", "application/json");
    envelope.put("traceparent", null);
    envelope.put("data", ByteBuffer.wrap(encode(payload, NOTIFICATION_SEND_SCHEMA)));
    return envelope;
}
```

---

### 3-9. KafkaTopicProperties.java — notificationSend 필드 추가

기존 필드 아래 추가:
```java
private String notificationSend = "platform.notification.notification-send-v1";

public String getNotificationSend() { return notificationSend; }
public void setNotificationSend(String notificationSend) { this.notificationSend = notificationSend; }
```

---

### 3-10. KafkaErrorHandlerConfig.java — notificationErrorHandler 빈 추가

기존 `defaultErrorHandler` 빈 아래 추가. import에 `ExponentialBackOff` 추가:

```java
import org.springframework.util.backoff.ExponentialBackOff;

@Bean("notificationErrorHandler")
public DefaultErrorHandler notificationErrorHandler(
        @Qualifier("dltKafkaTemplate") KafkaTemplate<String, GenericRecord> dltKafkaTemplate,
        @Qualifier("dltBytesKafkaTemplate") KafkaTemplate<String, byte[]> dltBytesKafkaTemplate) {
    Map<Class<?>, KafkaOperations<? extends Object, ? extends Object>> templates = new LinkedHashMap<>();
    templates.put(byte[].class, dltBytesKafkaTemplate);
    templates.put(Void.class, dltBytesKafkaTemplate);
    templates.put(GenericRecord.class, dltKafkaTemplate);
    templates.put(Object.class, dltKafkaTemplate);
    var recoverer = new DeadLetterPublishingRecoverer(templates,
        (record, ex) -> new TopicPartition(
            record.topic() + topicProperties.getDlqSuffix(), record.partition()));
    // exponential backoff: 1s → 2s → 4s (총 3회 재시도)
    // elapsed = 1000+2000+4000 = 7000ms → 7000 >= 7000 → STOP (정확히 3회)
    var backOff = new ExponentialBackOff(1_000L, 2.0);
    backOff.setMaxElapsedTime(7_000L);
    return new DefaultErrorHandler(recoverer, backOff);
}
```

---

### 3-11. KafkaConsumerConfig.java — notification 팩토리 빈 추가

기존 `auditKafkaListenerContainerFactory` 빈 아래 추가:

```java
@Bean("notificationConsumerFactory")
public ConsumerFactory<String, GenericRecord> notificationConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-consumer-group");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
    props.put("schema.registry.url", schemaRegistryUrl);
    props.put("specific.avro.reader", false);
    return new DefaultKafkaConsumerFactory<>(props);
}

@Bean("notificationKafkaListenerContainerFactory")
public ConcurrentKafkaListenerContainerFactory<String, GenericRecord> notificationKafkaListenerContainerFactory(
        @Qualifier("notificationConsumerFactory") ConsumerFactory<String, GenericRecord> notificationConsumerFactory,
        @Qualifier("notificationErrorHandler") DefaultErrorHandler notificationErrorHandler) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, GenericRecord>();
    factory.setConsumerFactory(notificationConsumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.setMissingTopicsFatal(false);
    factory.setCommonErrorHandler(notificationErrorHandler);
    return factory;
}
```

---

### 3-12. FcmConfig.java

```java
package com.synapse.platform.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class FcmConfig {

    @Value("${app.fcm.service-account-path}")
    private String serviceAccountPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        try (FileInputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
            return FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        }
    }
}
```

---

### 3-13. SesConfig.java

```java
package com.synapse.platform.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "app.ses.enabled", havingValue = "true")
public class SesConfig {

    @Value("${app.ses.region:ap-northeast-2}")
    private String region;

    @Bean
    public SesV2Client sesV2Client() {
        return SesV2Client.builder()
            .region(Region.of(region))
            .build();
    }
}
```

---

### 3-14. SesEmailException.java

```java
package com.synapse.platform.notification.exception;

import com.synapse.platform.global.exception.BusinessException;

public class SesEmailException extends BusinessException {
    public SesEmailException(String detail) {
        super("PLAT-NOTIFICATION-002", 500, "SES email send failed: " + detail);
    }
}
```

> `BusinessException`의 패키지 경로: `com.synapse.platform.global.exception.BusinessException`

---

### 3-15. FcmPushService.java

```java
package com.synapse.platform.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(FirebaseApp.class)
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);
    private final DeviceTokenRepository deviceTokenRepository;

    public FcmPushService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * 사용자의 활성 디바이스에 FCM 푸시 발송.
     * @return 성공 발송 건수 (활성 디바이스 없으면 0, 예외 없음)
     */
    public int sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        List<String> tokens = deviceTokenRepository.findByUserId(userId).stream()
            .filter(DeviceToken::isActive)
            .map(DeviceToken::getToken)
            .collect(Collectors.toList());

        if (tokens.isEmpty()) {
            log.debug("No active FCM tokens for user {}", userId);
            return 0;
        }

        MulticastMessage message = MulticastMessage.builder()
            .setNotification(
                com.google.firebase.messaging.Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
            .putAllData(data != null ? data : Collections.emptyMap())
            .addAllTokens(tokens)
            .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            if (response.getFailureCount() > 0) {
                log.warn("FCM partial failure for user {}: {}/{} succeeded",
                    userId, response.getSuccessCount(), tokens.size());
            }
            return response.getSuccessCount();
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException("FCM multicast failed for user " + userId, e);
        }
    }
}
```

---

### 3-16. SesEmailService.java

```java
package com.synapse.platform.notification.service;

import com.synapse.platform.notification.exception.SesEmailException;
import com.synapse.platform.user.api.UserApi;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Service
@ConditionalOnBean(SesV2Client.class)
public class SesEmailService {

    private static final Logger log = LoggerFactory.getLogger(SesEmailService.class);

    private final SesV2Client sesV2Client;
    private final UserApi userApi;

    @Value("${app.ses.from-email:noreply@synapse.app}")
    private String fromEmail;

    public SesEmailService(SesV2Client sesV2Client, UserApi userApi) {
        this.sesV2Client = sesV2Client;
        this.userApi = userApi;
    }

    public void sendToUser(UUID userId, String subject, String htmlBody) {
        String email = userApi.findById(userId)
            .map(u -> u.email())
            .orElseThrow(() -> new SesEmailException("User not found: " + userId));

        try {
            sesV2Client.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder()
                    .simple(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                            .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                            .build())
                        .build())
                    .build())
                .build());
        } catch (SesEmailException e) {
            throw e;
        } catch (Exception e) {
            log.error("SES send failed for user {}: {}", userId, e.getMessage());
            throw new SesEmailException(e.getMessage());
        }
    }
}
```

---

### 3-17. NotificationService.java

`@Autowired(required = false)` 대신 `ObjectProvider<T>` 생성자 주입 사용.
서비스 빈이 없으면(비활성화) `sendToChannel` 진입 전 early return — notifications 레코드 생성 없음.

```java
package com.synapse.platform.notification.service;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import com.synapse.platform.notification.entity.*;
import com.synapse.platform.notification.repository.NotificationRepository;
import java.time.*;
import java.util.*;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DAILY_EMAIL_LIMIT = 10;

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<FcmPushService> fcmPushServiceProvider;
    private final ObjectProvider<SesEmailService> sesEmailServiceProvider;

    public NotificationService(NotificationRepository notificationRepository,
                               ObjectProvider<FcmPushService> fcmPushServiceProvider,
                               ObjectProvider<SesEmailService> sesEmailServiceProvider) {
        this.notificationRepository = notificationRepository;
        this.fcmPushServiceProvider = fcmPushServiceProvider;
        this.sesEmailServiceProvider = sesEmailServiceProvider;
    }

    @Transactional
    public void processNotificationSend(GenericRecord envelope) {
        UUID eventId = UUID.fromString(envelope.get("id").toString());
        UUID tenantId = UUID.fromString(envelope.get("tenantid").toString());

        GenericRecord payload = PlatformAvroEvents.decodeNotificationSend(envelope);
        UUID userId = UUID.fromString(payload.get("userId").toString());
        String notificationType = payload.get("notificationType").toString();
        String title = payload.get("title").toString();
        String body = payload.get("body").toString();
        String emailSubject = getOptionalString(payload, "emailSubject");
        String emailHtmlBody = getOptionalString(payload, "emailHtmlBody");

        @SuppressWarnings("unchecked")
        List<Object> rawChannels = (List<Object>) payload.get("channels");

        for (Object ch : rawChannels) {
            NotificationChannel channel = NotificationChannel.valueOf(ch.toString());
            sendToChannel(eventId, userId, tenantId, notificationType,
                channel, title, body, emailSubject, emailHtmlBody, payload);
        }
    }

    private void sendToChannel(UUID eventId, UUID userId, UUID tenantId,
                                String notificationType, NotificationChannel channel,
                                String title, String body,
                                String emailSubject, String emailHtmlBody,
                                GenericRecord payload) {
        // 채널 서비스 빈 없음 = 해당 채널 비활성화. 레코드 생성 없이 skip.
        if (channel == NotificationChannel.FCM
                && fcmPushServiceProvider.getIfAvailable() == null) {
            log.info("FCM channel not configured — skipping for user {}", userId);
            return;
        }
        if (channel == NotificationChannel.EMAIL
                && sesEmailServiceProvider.getIfAvailable() == null) {
            log.info("SES channel not configured — skipping for user {}", userId);
            return;
        }

        Notification notification = notificationRepository
            .findByEventIdAndChannel(eventId, channel)
            .orElseGet(() -> {
                Notification n = Notification.create(
                    eventId, userId, tenantId, notificationType, channel, title, body);
                return notificationRepository.save(n);
            });

        if (notification.isSent()) {
            log.debug("Skip already-sent notification: eventId={} channel={}", eventId, channel);
            return;
        }

        notification.incrementAttempts();
        notificationRepository.save(notification);

        try {
            if (channel == NotificationChannel.FCM) {
                dispatchFcm(userId, title, body, payload);
            } else if (channel == NotificationChannel.EMAIL) {
                dispatchEmail(userId, emailSubject, emailHtmlBody, eventId);
            }
            notification.markSent();
        } catch (Exception e) {
            notification.markFailed(e.getMessage());
            notificationRepository.save(notification);
            throw e; // Kafka container의 exponential backoff 재시도로 위임
        }
        notificationRepository.save(notification);
    }

    private void dispatchFcm(UUID userId, String title, String body, GenericRecord payload) {
        FcmPushService svc = fcmPushServiceProvider.getIfAvailable();
        @SuppressWarnings("unchecked")
        Map<Object, Object> rawData = (Map<Object, Object>) payload.get("data");
        Map<String, String> data = new HashMap<>();
        if (rawData != null) {
            rawData.forEach((k, v) -> data.put(k.toString(), v.toString()));
        }
        svc.sendToUser(userId, title, body, data);
    }

    private void dispatchEmail(UUID userId, String subject, String htmlBody, UUID eventId) {
        SesEmailService svc = sesEmailServiceProvider.getIfAvailable();
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        long todayCount = notificationRepository.countTodayEmailByUserId(userId, dayStart);
        if (todayCount >= DAILY_EMAIL_LIMIT) {
            log.warn("Daily email limit exceeded for user {}: eventId={}", userId, eventId);
            return;
        }
        svc.sendToUser(
            userId,
            subject != null ? subject : "Synapse 알림",
            htmlBody != null ? htmlBody : "");
    }

    private String getOptionalString(GenericRecord record, String field) {
        Object value = record.get(field);
        return value != null ? value.toString() : null;
    }
}
```

---

### 3-18. NotificationKafkaConsumer.java

```java
package com.synapse.platform.notification.consumer;

import com.synapse.platform.notification.service.NotificationService;
import org.apache.avro.generic.GenericRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    public NotificationKafkaConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${app.kafka.topics.notification-send:platform.notification.notification-send-v1}",
        groupId = "notification-consumer-group",
        containerFactory = "notificationKafkaListenerContainerFactory"
    )
    public void consume(GenericRecord envelope) {
        notificationService.processNotificationSend(envelope);
    }
}
```

---

## 4. 테스트 명세

### 4-1. NotificationServiceTest.java (단위 테스트, Mockito)

```java
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock FcmPushService fcmPushService;
    @Mock SesEmailService sesEmailService;
    @InjectMocks NotificationService notificationService;

    // 시나리오
    // ① FCM 채널 이벤트 → fcmPushService.sendToUser 1회 호출, status = SENT
    // ② EMAIL 채널 이벤트 → sesEmailService.sendToUser 1회 호출, status = SENT
    // ③ 이미 SENT 상태 notification 존재 → sendToUser 미호출
    // ④ FCM 발송 중 RuntimeException → markFailed 호출, status = FAILED, 예외 re-throw
    // ⑤ 일일 이메일 10건 초과 → sesEmailService.sendToUser 미호출
}
```

### 4-2. FcmPushServiceTest.java (단위 테스트, Mockito)

```java
// DeviceTokenRepository mock
// FirebaseMessaging static mock (Mockito.mockStatic)
// ① 활성 디바이스 2개 → successCount = 2
// ② 활성 디바이스 0개 → 0 반환, FirebaseMessaging 미호출
// ③ FirebaseMessagingException → RuntimeException throw
```

### 4-3. SesEmailServiceTest.java (단위 테스트, Mockito)

```java
// SesV2Client mock, UserApi mock
// ① 정상 발송 → SesV2Client.sendEmail 1회 호출
// ② UserApi.findById empty → SesEmailException throw
// ③ SesV2Client.sendEmail throws → SesEmailException throw
```

### 4-4. NotificationKafkaConsumerIT.java (통합 테스트)

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"platform.notification.notification-send-v1"}
)
class NotificationKafkaConsumerIT {

    @Autowired KafkaTemplate<String, GenericRecord> kafkaTemplate;
    @Autowired NotificationRepository notificationRepository;
    @MockBean FcmPushService fcmPushService;
    @MockBean SesEmailService sesEmailService;

    // 이벤트 생성: PlatformAvroEvents.notificationSendEnvelope(...) 사용
    // 발행 후 Awaitility로 최대 5초 대기

    // ① FCM 채널만 → fcmPushService.sendToUser verify(times(1)), sesEmailService verify(never())
    //    → notifications 테이블에 FCM 채널 SENT 레코드 1건
    // ② EMAIL 채널만 → sesEmailService.sendToUser verify(times(1)), fcmPushService verify(never())
    //    → notifications 테이블에 EMAIL 채널 SENT 레코드 1건
    // ③ ["FCM","EMAIL"] 채널 → 둘 다 verify(times(1))
    //    → notifications 테이블에 2건
    // ④ 동일 eventId 두 번 발행 → 첫 번째만 처리
    //    → fcmPushService.sendToUser verify(times(1)) (두 번째는 SENT 멱등성 skip)
}
```

> IT 테스트에서 `kafkaTemplate`으로 `GenericRecord`를 발행할 때 Schema Registry mock이 필요하다.
> Step 6 `AuditKafkaIntegrationTest`에서 사용한 `mock://platform-test` URL 설정을 `application-test.yml`에서 확인하고 동일하게 적용한다.

---

## 5. 제약 사항

1. **기존 코드 불변**: `DeviceTokenService`, `AuditLogService`, `defaultErrorHandler` 등 기존 빈 수정 금지.
2. **ObjectProvider 생성자 주입**: `NotificationService`는 `ObjectProvider<FcmPushService>`, `ObjectProvider<SesEmailService>`를 생성자로 받는다. 비활성화 환경에서도 기동돼야 하며, 빈이 없으면 레코드 생성 없이 skip한다.
3. **@ConditionalOnBean**: `FcmPushService`는 `FirebaseApp` 빈 존재 시, `SesEmailService`는 `SesV2Client` 빈 존재 시에만 등록.
4. **멱등성**: `UNIQUE (event_id, channel)` + `isSent()` 이중 보장. 중복 이벤트는 조기 반환.
5. **일일 이메일 한도**: `DAILY_EMAIL_LIMIT = 10` 초과 시 warn 로그만, 예외 없음.
6. **토픽 이름**: `platform.notification.notification-send-v1` (Rule 8.1: `{서비스}.{도메인}.{이벤트}-v{N}`)
7. **DLT suffix**: `.DLT` (기존 `defaultErrorHandler`와 동일)
8. **커밋 단위**:
   - commit 1: V31 migration + Entity/Enum/Repository
   - commit 2: Kafka 인프라 변경 (TopicProperties, ConsumerConfig, ErrorHandlerConfig, PlatformAvroEvents)
   - commit 3: Service/Consumer/Config 구현
   - commit 4: 테스트

---

## 필요한 출력 형식

각 파일의 전체 코드 블록. 파일 앞에 상대 경로 명시 (`src/main/java/...`).
빌드 성공 + 테스트 통과 확인 후 제출.

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-29
