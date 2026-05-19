# WORKFLOW: @platform-owner — Week 2

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-05-18 ~ 2026-05-22, 5 영업일
> **PRD**: [PRD_W2.md](../prd/PRD_W2.md)

---

## Step 4: billing 모듈 — Stripe Checkout + Webhook + 플랜 관리

### 4.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W2 해당 요구사항 확인 (billing 모듈)
- [x] Duration 산정 확인

### 4.2 요구사항 분석
- [x] Stripe Checkout Session 생성 플로우 분석
- [x] 구독 플랜 종류 정의 (Free, Pro, Team 등)
- [x] Webhook 이벤트 처리 목록 확정 (checkout.session.completed, invoice.paid, customer.subscription.deleted)
- [x] 결제 이력 조회 요건 정의
- [x] Instructions 초안 → TASK 문서 반영

### 4.3 Security 1차 검토
- [x] 인증 필요 여부: Yes (JWT 인증 필요)
- [x] 권한 종류: 로그인 사용자 (본인 구독만)
- [x] Webhook 검증: Stripe Signature 검증 필수
- [x] Stripe API Key 관리: 환경변수 관리, 코드 내 하드코딩 금지
- [x] 결과 → TASK Constraints 반영

### 4.4 ERD 설계
- [x] subscriptions 테이블 설계 (id, tenant_id FK, plan_code, stripe_subscription_id, status, current_period_start, current_period_end, created_at, updated_at)
- [x] payment_history 테이블 설계 (id, tenant_id, subscription_id, stripe_payment_intent_id, amount, currency, status, paid_at, created_at)
- [x] 인덱스 설계 (subscriptions.tenant_id, payment_history.tenant_id, payment_history.subscription_id)
- [x] 관계 정의 (payment_history.subscription_id → subscriptions.id FK)
- [x] Duration(final) 갱신

### 4.5 Security 2차 검토
- [x] 결제 정보 PCI DSS 준수 확인 (카드 정보 직접 저장 금지, Stripe 위임)
- [x] Webhook endpoint 서명 검증 구현 확인
- [x] Soft Delete 정책: 논리삭제 (구독 이력 보관)
- [x] 행 단위 접근 제어: 필요 (userId 기반)
- [x] 결과 → TASK Constraints 반영

### 4.6 DTO / Entity 설계 (API First)
- [x] CheckoutSessionRequest 정의 (plan_code, successUrl, cancelUrl)
- [x] SubscriptionResponse 정의 (id, plan_code, status, current_period_end)
- [x] PaymentHistoryResponse 정의 (id, amount, currency, status, paidAt)
- [x] Subscription Entity 작성
- [x] PaymentHistory Entity 작성
- [x] PlanCode Enum 작성 (FREE, PRO, TEAM)
- [x] MapStruct 매퍼 작성
- [x] Output Format → TASK 반영

### 4.7 Repository 구현
- [x] SubscriptionRepository 인터페이스 작성
- [x] PaymentHistoryRepository 인터페이스 작성
- [x] findByUserIdAndStatusActive 커스텀 쿼리
- [x] findByUserIdOrderByPaidAtDesc 커스텀 쿼리
- [x] Flyway 마이그레이션 스크립트 작성

### 4.8 Service + Test
- [x] BillingService 구현 (createCheckoutSession, handleWebhook, getSubscription, getPaymentHistory)
- [x] Stripe Checkout Session 생성 로직 구현
- [x] Stripe Webhook 핸들러 구현 (이벤트별 분기 처리)
- [x] 구독 상태 관리 로직 (active, canceled, past_due)
- [x] Bean Validation 적용
- [x] 단위 테스트 작성 (Mockito — Stripe API mock)
- [x] 테스트 통과 확인

### 4.9 Controller + Test
- [x] POST /billing/checkout 엔드포인트 구현
- [x] POST /billing/webhooks 엔드포인트 구현 (Stripe Signature 검증)
- [x] GET /billing/subscription 엔드포인트 구현
- [x] 슬라이스 테스트 (@WebMvcTest)
- [x] Webhook 서명 검증 테스트
- [x] 401/403 응답 테스트
- [x] 통합 테스트
- [x] 테스트 통과 확인

### 4.10 View + Test (해당 시)
- [x] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [x] Swagger API 문서 확인
- [x] RULE Reference → TASK 반영

**Step 4 Status**: [ ] Not Started / [ ] In Progress / [x] Done

---

## Step 5: notification 모듈 기초 — FCM 설정 + device_tokens

### 5.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W2 해당 요구사항 확인 (notification 모듈)
- [x] Duration 산정 확인

### 5.2 요구사항 분석
- [x] FCM (Firebase Cloud Messaging) 연동 요건 분석
- [x] 디바이스 토큰 등록/갱신/삭제 플로우 정의
- [x] 알림 선호도 (notification_preferences) 항목 정의 — Step 7로 이관 결정 (ERD 충돌)
- [x] Instructions 초안 → TASK 문서 반영

### 5.3 Security 1차 검토
- [x] 인증 필요 여부: Yes (JWT 인증 필요)
- [x] 권한 종류: 로그인 사용자 (본인 디바이스만)
- [x] FCM 서버 키 관리: 환경변수 관리, 코드 내 하드코딩 금지
- [x] 결과 → TASK Constraints 반영

### 5.4 ERD 설계
- [x] device_tokens 테이블 설계 (id, tenant_id, user_id, token, platform: ios|android|web, is_active, created_at, updated_at)
- [x] 참고: deviceName 컬럼은 ERD에 없음 — 제거
- [x] notification_preferences — Step 7로 이관 (ERD JSONB vs WORKFLOW 컬럼 기반 충돌 존재)
- [x] 인덱스 설계 (idx_device_tokens_tenant_user ON device_tokens(tenant_id, user_id), UNIQUE(token))
- [x] Duration(final) 갱신

### 5.5 Security 2차 검토
- [x] 디바이스 토큰 암호화 저장 검토 — 불필요 (FCM 토큰은 공개 식별자)
- [x] Soft Delete 정책: 물리삭제 (토큰 만료 시 삭제)
- [x] 행 단위 접근 제어: 필요 (userId 기반)
- [x] 결과 → TASK Constraints 반영

### 5.6 DTO / Entity 설계 (API First)
- [x] DeviceTokenRequest 정의 (token, platform: ios|android|web)
- [x] DeviceTokenResponse 정의 (id, platform, is_active, createdAt)
- [x] DeviceToken Entity 작성 (tenant_id, is_active 컬럼 포함)
- [x] Platform Enum 작성 (ios, android, web — 소문자, @JsonCreator/@JsonValue)
- [x] PlatformConverter 작성 (AttributeConverter<Platform, String>)
- [x] Output Format → TASK 반영

### 5.7 Repository 구현
- [x] DeviceTokenRepository 인터페이스 작성
- [x] findByToken, countByUserId, findByUserId 커스텀 쿼리
- [x] native UPSERT (@Modifying clearAutomatically=true, flushAutomatically=true)
- [x] V27__create_device_tokens.sql Flyway 마이그레이션 스크립트 작성

### 5.8 Service + Test
- [x] DeviceTokenService 구현 (register, unregister)
- [x] tenantId resolve — UserApi.findById(userId).defaultTenantId() 패턴
- [x] DeviceRegistrationLimitExceededException — BusinessException 상속, PLAT-NOTIFICATION-001
- [x] Bean Validation 적용
- [x] 통합 테스트 통과 확인

### 5.9 Controller + Test
- [x] POST /api/v1/notifications/devices 엔드포인트 구현 (201)
- [x] DELETE /api/v1/notifications/devices/{id} 엔드포인트 구현 (204/403/404)
- [x] NotificationSecurityConfig @Order(1) FilterChain 구현
- [x] GlobalExceptionHandler EntityNotFoundException→404, HttpMessageNotReadableException→400 추가
- [x] 401/403 응답 테스트
- [x] 통합 테스트 9개 시나리오 통과
- [x] JaCoCo 라인 커버리지 92.38% (기준 80% 충족)

### 5.10 View + Test (해당 시)
- [x] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [x] Swagger API 문서 확인
- [x] RULE Reference → TASK 반영

**Step 5 Status**: [ ] Not Started / [ ] In Progress / [x] Done
