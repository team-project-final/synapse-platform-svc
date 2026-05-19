# Stripe Checkout 및 Webhook 추가 기술 조사 보고서 (Step 4 보완)

본 문서는 1차 조사(`STRIPE_RESEARCH_SUMMARY.md`)를 보완하기 위한 추가 조사 결과입니다.

---

## 1. StripeClient 초기화 및 API 버전 고정 코드 패턴

최신 Stripe Java SDK(v23 이후, 현재 v32.x)에서는 구형 `Stripe.apiKey` 방식의 전역(Static) 설정 대신 **`StripeClient` 객체를 Bean으로 등록**하여 사용하는 것이 표준입니다.

*   **API 버전 고정:** Java SDK는 강타입(Strongly-typed) 언어 특성상 SDK 버전에 모델(Customer 등) 구조가 종속됩니다. 따라서 **`build.gradle`에 정의한 SDK 버전이 곧 API 버전을 의미**합니다. `StripeClientOptions`에 별도의 전역 `setStripeVersion()` 메서드는 존재하지 않으며, 특정 요청에 한해 `RequestOptions`를 통해 우회할 수 있으나 권장되지 않습니다.

### Spring Bean 등록 패턴 예시
```java
import com.stripe.StripeClient;
import com.stripe.net.StripeClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String apiKey;

    @Value("${stripe.max.retries:3}")
    private int maxRetries;

    @Bean
    public StripeClient stripeClient() {
        // Options 패턴을 사용하면 타임아웃, 재시도 횟수 등을 유연하게 관리할 수 있습니다.
        StripeClientOptions options = StripeClientOptions.builder()
            .setApiKey(apiKey)
            .setMaxNetworkRetries(maxRetries)
            .setConnectTimeout(30000)
            .setReadTimeout(60000)
            .build();

        return new StripeClient(options);
    }
}
```

---

## 2. ContentCachingRequestWrapper와 Spring Security 충돌 해결

Webhook 서명 검증을 위해 `ContentCachingRequestWrapper`를 사용할 때, Spring Security 필터 체인(특히 `CsrfFilter` 등)이나 Controller의 `@RequestBody`보다 먼저 Body를 읽어버리면 캐시가 비어있는 현상(Empty Body)이 발생할 수 있습니다.

### 충돌 해결 패턴 (Filter Order)
`ContentCachingRequestWrapper`는 단순히 래핑만 할 뿐, `InputStream`을 누군가 소비(Consume)할 때 캐싱을 시작합니다. 
따라서 이 필터는 **Spring Security의 인증 필터보다 앞단에 배치**되어야 하며, 컨트롤러에 도달하기 전 필터 내부에서 서명 검증을 해야 한다면 명시적으로 Body를 한 번 읽어(Consume) 주어야 합니다.

### 설정 예시
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, StripeWebhookFilter stripeWebhookFilter) throws Exception {
        // Stripe Webhook URL은 CSRF 예외 처리 및 인증 면제 처리 필수
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/billing/webhooks"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/billing/webhooks").permitAll()
                .anyRequest().authenticated()
            );

        // Security Filter 이전에 Request Wrapper를 적용하여 Security가 Body를 소비하기 전 캐싱 준비
        http.addFilterBefore(stripeWebhookFilter, CsrfFilter.class);

        return http.build();
    }
}
```

*   **대안:** 만약 필터 설정이 복잡하여 충돌이 잦다면, Controller에서 `@RequestBody byte[] payload` 또는 `String payload` 형식으로 Body 전체를 통으로 받은 뒤, 이 원본 데이터를 사용하여 서명을 검증하는 것이 가장 안전한 방법입니다.

---

## 3. processed_events 테이블 DDL (멱등성 보장)

Stripe Webhook의 멱등성 보장을 위한 핵심은 DB의 Unique 제약 조건을 활용하여 **"Insert-First"** 패턴을 구현하는 것입니다. (ON CONFLICT DO NOTHING)

### PostgreSQL 테이블 DDL
```sql
CREATE TABLE processed_events (
    event_id VARCHAR(255) PRIMARY KEY,       -- Stripe Webhook 이벤트 ID (evt_...)
    event_type VARCHAR(100) NOT NULL,        -- 이벤트 타입 (예: checkout.session.completed)
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수신 시간
    processed_at TIMESTAMP WITH TIME ZONE,   -- 비즈니스 로직 처리 완료 시간
    payload JSONB                            -- (선택) 원본 Payload 전문 로깅용
);

-- 보존 기간(예: 30일)이 지난 이벤트를 주기적으로 삭제(Cleanup)하기 위한 인덱스
CREATE INDEX idx_processed_events_received_at ON processed_events (received_at);
```

### 처리 전략
1.  이벤트 수신 시, `event_id`를 키로 하여 `processed_events` 테이블에 `INSERT` 시도.
2.  Unique 제약 위반(Duplicate Key)이 발생하거나 `ON CONFLICT` 시 아무 작업도 일어나지 않으면, 이미 처리 중/처리 완료된 이벤트이므로 **즉시 200 OK 응답** 반환.
3.  성공적으로 `INSERT` 된 경우에만 비즈니스 로직(결제 상태 업데이트 등) 처리.

---

## 4. Stripe Java SDK v32.x Webhook.constructEvent 패키지 정보

v32.x 버전에서 Webhook 이벤트 객체 생성 및 서명 검증 실패 시 발생하는 Exception의 패키지 경로는 다음과 같습니다. `StripeClient`를 사용하는 방식에서도 Webhook 검증 클래스는 정적 메서드로 동일하게 사용됩니다.

### 필요 Import 문
```java
import com.stripe.model.Event;
import com.stripe.net.Webhook; // 서명 검증 클래스
import com.stripe.exception.SignatureVerificationException; // 검증 실패 Exception
```

### 코드 예시
```java
try {
    // StripeClient 객체와 무관하게 Webhook 클래스의 static 메서드 사용
    Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
} catch (SignatureVerificationException e) {
    // 서명이 유효하지 않음 (Replay Attack 또는 Secret Key 불일치)
    log.error("Invalid Stripe signature", e);
    throw new InvalidWebhookSignatureException("Invalid signature");
}
```
*   `payload` 파라미터는 변형되지 않은 원시(Raw) 문자열이어야 합니다.
