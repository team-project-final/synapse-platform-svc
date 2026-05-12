# 9. 관측성 RULE — Observability

> **참조**: [전체 Rule 목록](../rules/) | [준수 체크리스트](appendix-c-checklist.md)

---

## 9.1 로깅 \[SHOULD\]

로그는 **구조화 JSON** 포맷으로 출력해. `traceId`와 `spanId`는 MDC에서 자동 주입되게 설정해야 해.
사람이 읽기 좋은 텍스트 로그는 로컬 개발에서만 써.

### logback-spring.xml JSON 포맷 예시

```xml
<!-- ✅ Good — 구조화 JSON 로깅 -->
<configuration>
  <springProfile name="!local">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON" />
    </root>
  </springProfile>
</configuration>
```

```java
// ❌ Bad — 비구조화 텍스트 로깅 + 수동 컨텍스트
log.info("User " + userId + " created card " + cardId + " at " + timestamp);
```

```java
// ✅ Good — 구조화 로깅 + 자동 traceId 주입
log.info("카드 생성 완료", kv("userId", userId), kv("cardId", cardId));
```

> **이유**: JSON 구조화 로그여야 Loki/ELK에서 필드 기반 검색이 가능해. traceId가 없으면 분산 환경에서 요청 추적이 불가능해.

---

## 9.2 메트릭 \[SHOULD\]

Spring Boot는 **Actuator + Micrometer**로 메트릭을 노출해. 인프라 메트릭(CPU, 메모리)은 자동 수집되니까,
팀이 직접 정의해야 하는 건 **비즈니스 메트릭**이야.

### 핵심 비즈니스 메트릭

| 메트릭 이름 | 타입 | 설명 |
|---|---|---|
| `card_review_total` | Counter | 카드 리뷰 완료 횟수 |
| `note_created_total` | Counter | 노트 생성 횟수 |
| `study_session_duration_seconds` | Timer | 학습 세션 지속 시간 |

### Counter 등록 예시

```java
// ✅ Good — Micrometer Counter 등록
@Component
@RequiredArgsConstructor
public class CardMetrics {
    private final MeterRegistry registry;

    public void recordReview(String result) {
        registry.counter("card_review_total",
            "result", result   // "correct" or "incorrect"
        ).increment();
    }
}
```

```java
// ❌ Bad — 메트릭 없이 로그로만 추적
log.info("Card reviewed: result={}", result);
```

> **이유**: 로그는 개별 이벤트 추적용이고, 메트릭은 추세와 알림용이야. "지난 5분간 리뷰 실패율 30% 초과" 같은 조건은 메트릭이 있어야 알림을 걸 수 있어.

---

## 9.3 트레이싱 \[SHOULD\]

분산 트레이싱은 **OpenTelemetry** 표준을 따라. Spring Boot 3.x는 Micrometer Tracing + OTLP exporter 조합을 써.

### 설정 예시

```yaml
# ✅ Good — application.yml OTLP 트레이싱 설정
management:
  tracing:
    sampling:
      probability: 1.0   # dev: 100%, prod: 0.1 권장
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

### Kafka Consumer 체인 전파

Kafka를 통한 비동기 처리에서도 traceId가 끊기지 않게 **헤더로 전파**해야 해.

```java
// ✅ Good — spring-kafka + micrometer-tracing 조합 시 traceId 자동 전파
@KafkaListener(topics = "card-events")
public void handle(@Payload CardEvent event,
                   @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    // traceId는 Kafka 헤더에서 자동 추출됨
    log.info("카드 이벤트 처리", kv("cardId", event.cardId()));
}
```

```java
// ❌ Bad — Kafka consumer에서 새 trace 시작 (체인 끊김)
@KafkaListener(topics = "card-events")
public void handle(CardEvent event) {
    Span span = tracer.nextSpan().name("process-card").start(); // 새 trace!
    // 이전 요청과 연결 안 됨
}
```

> **이유**: Kafka 같은 비동기 경계에서 traceId가 끊기면 "이 이벤트가 어떤 API 요청에서 시작된 건지" 추적이 불가능해져.
