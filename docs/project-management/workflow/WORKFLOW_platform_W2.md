# WORKFLOW: @platform-owner — Week 2

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)  
> **기간**: 2026-05-19 ~ 2026-05-23  
> **PRD**: [PRD_W2.md](../prd/PRD_W2.md)

---

## Step 4: billing 모듈 — Stripe Checkout + Webhook + 플랜 관리

### 4.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W2 해당 요구사항 확인 (billing 모듈)
- [ ] Duration 산정 확인

### 4.2 요구사항 분석
- [ ] Stripe Checkout Session 생성 플로우 분석
- [ ] 구독 플랜 종류 정의 (Free, Pro, Team 등)
- [ ] Webhook 이벤트 처리 목록 확정 (checkout.session.completed, invoice.paid, customer.subscription.deleted)
- [ ] 결제 이력 조회 요건 정의
- [ ] Instructions 초안 → TASK 문서 반영

### 4.3 Security 1차 검토
- [ ] 인증 필요 여부: Yes (JWT 인증 필요)
- [ ] 권한 종류: 로그인 사용자 (본인 구독만)
- [ ] Webhook 검증: Stripe Signature 검증 필수
- [ ] Stripe API Key 관리: 환경변수 관리, 코드 내 하드코딩 금지
- [ ] 결과 → TASK Constraints 반영

### 4.4 ERD 설계
- [ ] subscriptions 테이블 설계 (id, userId, planType, stripeSubscriptionId, status, currentPeriodStart, currentPeriodEnd, createdAt, updatedAt)
- [ ] payment_history 테이블 설계 (id, userId, subscriptionId, stripePaymentIntentId, amount, currency, status, paidAt, createdAt)
- [ ] 인덱스 설계 (subscriptions.userId, payment_history.userId, payment_history.subscriptionId)
- [ ] 관계 정의 (payment_history.subscriptionId → subscriptions.id FK)
- [ ] Duration(final) 갱신

### 4.5 Security 2차 검토
- [ ] 결제 정보 PCI DSS 준수 확인 (카드 정보 직접 저장 금지, Stripe 위임)
- [ ] Webhook endpoint 서명 검증 구현 확인
- [ ] Soft Delete 정책: 논리삭제 (구독 이력 보관)
- [ ] 행 단위 접근 제어: 필요 (userId 기반)
- [ ] 결과 → TASK Constraints 반영

### 4.6 DTO / Entity 설계 (API First)
- [ ] CheckoutSessionRequest 정의 (planType, successUrl, cancelUrl)
- [ ] SubscriptionResponse 정의 (id, planType, status, currentPeriodEnd)
- [ ] PaymentHistoryResponse 정의 (id, amount, currency, status, paidAt)
- [ ] Subscription Entity 작성
- [ ] PaymentHistory Entity 작성
- [ ] PlanType Enum 작성 (FREE, PRO, TEAM)
- [ ] MapStruct 매퍼 작성
- [ ] Output Format → TASK 반영

### 4.7 Repository 구현
- [ ] SubscriptionRepository 인터페이스 작성
- [ ] PaymentHistoryRepository 인터페이스 작성
- [ ] findByUserIdAndStatusActive 커스텀 쿼리
- [ ] findByUserIdOrderByPaidAtDesc 커스텀 쿼리
- [ ] Flyway 마이그레이션 스크립트 작성

### 4.8 Service + Test
- [ ] BillingService 구현 (createCheckoutSession, handleWebhook, getSubscription, getPaymentHistory)
- [ ] Stripe Checkout Session 생성 로직 구현
- [ ] Stripe Webhook 핸들러 구현 (이벤트별 분기 처리)
- [ ] 구독 상태 관리 로직 (active, canceled, past_due)
- [ ] Bean Validation 적용
- [ ] 단위 테스트 작성 (Mockito — Stripe API mock)
- [ ] 테스트 통과 확인

### 4.9 Controller + Test
- [ ] POST /api/v1/billing/checkout 엔드포인트 구현
- [ ] POST /api/v1/billing/webhook 엔드포인트 구현 (Stripe Signature 검증)
- [ ] GET /api/v1/billing/subscription 엔드포인트 구현
- [ ] GET /api/v1/billing/payments 엔드포인트 구현
- [ ] 슬라이스 테스트 (@WebMvcTest)
- [ ] Webhook 서명 검증 테스트
- [ ] 401/403 응답 테스트
- [ ] 통합 테스트
- [ ] 테스트 통과 확인

### 4.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 4 Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 5: notification 모듈 기초 — FCM 설정 + device_tokens

### 5.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W2 해당 요구사항 확인 (notification 모듈)
- [ ] Duration 산정 확인

### 5.2 요구사항 분석
- [ ] FCM (Firebase Cloud Messaging) 연동 요건 분석
- [ ] 디바이스 토큰 등록/갱신/삭제 플로우 정의
- [ ] 알림 선호도 (notification_preferences) 항목 정의
- [ ] Instructions 초안 → TASK 문서 반영

### 5.3 Security 1차 검토
- [ ] 인증 필요 여부: Yes (JWT 인증 필요)
- [ ] 권한 종류: 로그인 사용자 (본인 디바이스만)
- [ ] FCM 서버 키 관리: 환경변수 관리, 코드 내 하드코딩 금지
- [ ] 결과 → TASK Constraints 반영

### 5.4 ERD 설계
- [ ] device_tokens 테이블 설계 (id, userId, token, platform, deviceName, createdAt, updatedAt)
- [ ] notification_preferences 테이블 설계 (id, userId, type, enabled, createdAt, updatedAt)
- [ ] 인덱스 설계 (device_tokens.userId, device_tokens.token UNIQUE, notification_preferences.userId)
- [ ] Duration(final) 갱신

### 5.5 Security 2차 검토
- [ ] 디바이스 토큰 암호화 저장 검토
- [ ] Soft Delete 정책: 물리삭제 (토큰 만료 시 삭제)
- [ ] 행 단위 접근 제어: 필요 (userId 기반)
- [ ] 결과 → TASK Constraints 반영

### 5.6 DTO / Entity 설계 (API First)
- [ ] DeviceTokenRequest 정의 (token, platform, deviceName)
- [ ] DeviceTokenResponse 정의 (id, platform, deviceName, createdAt)
- [ ] NotificationPreferenceRequest 정의 (type, enabled)
- [ ] NotificationPreferenceResponse 정의 (type, enabled)
- [ ] DeviceToken Entity 작성
- [ ] NotificationPreference Entity 작성
- [ ] Platform Enum 작성 (ANDROID, IOS, WEB)
- [ ] MapStruct 매퍼 작성
- [ ] Output Format → TASK 반영

### 5.7 Repository 구현
- [ ] DeviceTokenRepository 인터페이스 작성
- [ ] NotificationPreferenceRepository 인터페이스 작성
- [ ] findByUserId 커스텀 쿼리
- [ ] Flyway 마이그레이션 스크립트 작성

### 5.8 Service + Test
- [ ] DeviceTokenService 구현 (register, update, delete, findByUserId)
- [ ] NotificationPreferenceService 구현 (getPreferences, updatePreference)
- [ ] FCM 초기화 설정 (FirebaseMessaging bean)
- [ ] Bean Validation 적용
- [ ] 단위 테스트 작성 (Mockito)
- [ ] 테스트 통과 확인

### 5.9 Controller + Test
- [ ] POST /api/v1/notifications/devices 엔드포인트 구현 (토큰 등록)
- [ ] DELETE /api/v1/notifications/devices/{id} 엔드포인트 구현
- [ ] GET /api/v1/notifications/preferences 엔드포인트 구현
- [ ] PUT /api/v1/notifications/preferences 엔드포인트 구현
- [ ] 슬라이스 테스트 (@WebMvcTest)
- [ ] 401/403 응답 테스트
- [ ] 통합 테스트
- [ ] 테스트 통과 확인

### 5.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 5 Status**: [ ] Not Started / [ ] In Progress / [ ] Done
