# Stripe Checkout 및 Webhook 기술 조사 보고서 (Step 4)

본 문서는 **synapse-platform-svc**의 결제 모듈 구현을 위한 Stripe의 기본 개념, Java SDK 활용법, 웹훅 보안 및 실무 패턴을 종합적으로 조사한 결과입니다.

---

## 0. Stripe 개요: 전 세계 결제 인프라의 표준

### Stripe란 무엇인가?
Stripe는 인터넷 경제를 위한 금융 인프라 플랫폼입니다. 단순한 결제 대행(PG) 서비스를 넘어, 전 세계 기업이 온라인 결제를 수락하고 구독 모델을 관리하며 글로벌 금융 작업을 수행할 수 있도록 돕는 **'Payments as Code'** 철학의 개발자 친화적 플랫폼입니다.

### 핵심 가치 및 특징 (2025-2026 기준)
*   **개발자 중심 DNA**: 업계 최고 수준의 API 문서와 샌드박스 환경을 제공하여 시스템 통합 시간을 획기적으로 단축합니다.
*   **AI 및 에이전트 커머스**: 2026년 현재 AI 에이전트가 직접 결제할 수 있는 'Agentic Commerce' 인터페이스를 선도하며, 토큰 기반의 사용량 빌링을 완벽하게 지원합니다.
*   **글로벌 확장성**: 135개 이상의 통화와 100개 이상의 현지 결제 수단을 단일 통합으로 처리하며, 전 세계 세금(Stripe Tax) 및 사기 방지(Stripe Radar) 기능을 제공합니다.
*   **신뢰성 및 보안**: PCI Level 1 인증을 획득한 인프라로, 카드 정보를 서버에 직접 저장하지 않고도 안전하게 결제를 처리할 수 있습니다.

---

## 1. Stripe 기본 데이터 구조
Stripe는 상품과 가격, 고객을 분리하여 관리하는 유연한 아키텍처를 가집니다.

### 주요 엔티티 관계
*   **Customer (`cus_...`)**: 결제 주체인 사용자. 이메일, 결제 수단, 구독 정보를 가집니다.
*   **Product (`prod_...`)**: 판매할 서비스나 상품 그 자체 (예: "Synapse Pro Plan").
*   **Price (`price_...`)**: 상품에 귀속된 가격 정보. 통화, 금액, 주기(Monthly/Yearly)를 정의하며, 플랜 코드(PRO, TEAM)와 1:N 관계로 매핑됩니다.

### Checkout Session 동작 흐름
1.  **Session 생성 (Server)**: 서버에서 `StripeClient`를 통해 결제 페이지 생성을 요청합니다.
2.  **Redirect (Client)**: 사용자를 Stripe 호스팅 결제 페이지 URL로 리다이렉트합니다.
3.  **결제 완료**: 사용자가 카드 정보를 입력하고 결제합니다. (직접적인 카드 정보는 Stripe가 처리)
4.  **Success/Cancel URL**: 결제 성공 또는 취소 시 사용자를 다시 플랫폼으로 복귀시킵니다.
5.  **Webhook 처리**: 리다이렉트는 단순 페이지 이동이므로, **실제 데이터 업데이트는 Webhook 이벤트를 통해 비동기로 수행**하는 것이 표준 패턴입니다.

---

## 2. Stripe Java SDK (v32.x)
Spring Boot 4(Jakarta EE) 환경에서의 호환성 정보입니다.

*   **최신 버전**: `32.1.0` (2026년 5월 기준)
*   **Jakarta EE 호환성**: Spring Boot 4.0.0(Jakarta EE 11)과 완전 호환됩니다. SDK 자체는 서블릿 네임스페이스에 직접 의존하지 않아 안정적입니다.
*   **의존성 (Gradle)**:
    ```gradle
    implementation 'com.stripe:stripe-java:32.1.0'
    ```

---

## 3. Stripe Checkout Session 생성 패턴
Java SDK를 사용한 표준 생성 코드 패턴입니다.

### 코드 예시 (StripeClient 권장)
```java
StripeClient client = new StripeClient(stripeSecretKey);

SessionCreateParams params = SessionCreateParams.builder()
    .setMode(SessionCreateParams.Mode.SUBSCRIPTION) // 구독 모드
    .setSuccessUrl("https://synapse.com/billing/success?session_id={CHECKOUT_SESSION_ID}")
    .setCancelUrl("https://synapse.com/billing/cancel")
    .setCustomer(customerId) // 기존 고객 ID가 있을 경우
    .addLineItem(
        SessionCreateParams.LineItem.builder()
            .setPrice(priceId) // PRO/TEAM 플랜의 Price ID
            .setQuantity(1L)
            .build()
    )
    // 핵심: Webhook에서 우리 DB와 매핑하기 위한 메타데이터
    .putMetadata("tenant_id", tenantId.toString())
    .putMetadata("plan_code", "PRO")
    .build();

Session session = client.checkout().sessions().create(params);
String checkoutUrl = session.getUrl();
```

### PlanCode별 매핑 전략
`application.yml`에 환경별 Price ID를 정의하고, `PlanCode` Enum과 매핑하는 방식을 권장합니다.
```yaml
stripe:
  plans:
    pro:
      monthly: price_123_test
      yearly: price_456_test
```

---

## 4. Stripe Webhook 서명 검증 및 Raw Body 처리
Spring Boot에서 Webhook 서명 검증 시 가장 흔히 발생하는 문제는 **Request Body 재사용 불가** 현상입니다.

*   **문제 원인**: `HttpServletRequest`의 InputStream은 한 번만 읽을 수 있습니다. Jackson(`@RequestBody`)이 먼저 읽으면 SDK 서명 검증(`Webhook.constructEvent`) 시 바디가 비어있게 됩니다.
*   **해결 방법**: `ContentCachingRequestWrapper`를 사용하여 바디를 캐싱하는 Filter를 적용합니다.

### Jakarta EE 기반 Filter 예시
```java
@Component
public class StripeWebhookFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/billing/webhooks")) {
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
```

---

## 5. 주요 Webhook 이벤트 페이로드 구조
*   **`checkout.session.completed`**: 결제 완료 시점. `data.object.subscription` (구독 ID) 및 `metadata` 추출이 핵심입니다.
*   **`invoice.paid`**: 정기 결제 성공 시점. 구독 연장 처리에 사용됩니다.
*   **`customer.subscription.deleted`**: 구독이 완전히 종료/해지된 시점. 서비스 접근 권한을 즉시 회수해야 합니다.

---

## 6. 멱등성 (Idempotency) 보장 패턴
네트워크 문제로 동일한 Webhook이 중복 수신될 수 있으므로 반드시 멱등성을 보장해야 합니다.

1.  **Event ID 기록**: Stripe가 제공하는 `event.id` (예: `evt_...`)를 DB의 `processed_events` 테이블에 저장합니다.
2.  **Unique 제약 조건**: `event_id`에 Unique 제약을 걸어 중복 삽입 시 무시하도록 처리합니다.
3.  **상태 체크**: 비즈니스 로직 수행 전, 대상 리소스(예: Subscription)의 상태가 이미 'ACTIVE'인지 확인하는 로직을 병행합니다.
4.  **200 OK 응답**: 중복 수신이더라도 Stripe에는 반드시 `200 OK`를 보내야 재시도를 멈춥니다.

---

## 7. Stripe CLI 및 테스트 가이드
로컬 환경에서 Webhook을 테스트하기 위한 필수 도구입니다.

*   **로컬 포워딩**: `stripe listen --forward-to localhost:8080/billing/webhooks`
    *   실행 시 출력되는 `whsec_...` (Webhook Secret)을 환경 변수에 설정해야 서명 검증이 통과됩니다.
*   **이벤트 트리거**: `stripe trigger checkout.session.completed`
*   **테스트 카드 번호**
    *   성공: `4242 4242 4242 4242` (Visa)
    *   잔액 부족: `4000 0000 0000 0002`
    *   카드 만료: `4000 0000 0000 0069`

---

## 참고 문서
- [Stripe Java SDK GitHub](https://github.com/stripe/stripe-java)
- [Stripe Checkout 가이드](https://stripe.com/docs/checkout/quickstart)
- [Stripe Webhook 서명 검증 공식 문서](https://stripe.com/docs/webhooks/signatures)

## 주의 사항
- **PCI 준수**: 절대 서버 로그나 DB에 카드 번호(PAN)나 CVC를 저장하지 마세요. (Stripe Token/ID만 저장)
- **API 버전**: SDK 초기화 시 `StripeClientOptions`를 통해 API 버전을 고정하여 운영 환경의 급격한 변화를 방지하는 것이 좋습니다.
