# TASK: @platform-owner

> **담당 서비스**: platform-svc (auth / audit / billing / notification)
> **GitHub Repository**: [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc)
> **주차**: W1 (2026-05-12 ~ 2026-05-15, 4 영업일)
> **관련 문서**: [SCOPE](../scope/SCOPE_platform.md) | [PRD_W1](../prd/PRD_W1.md) | [WORKFLOW](../workflow/WORKFLOW_platform_W1.md) | [HISTORY](../history/HISTORY_platform.md)

---

## Step 1: platform-svc 골격 생성

- **Step Goal**: platform-owner가 Spring Boot 4 + Modulith 기반 platform-svc 프로젝트를 생성하여 4개 모듈 골격이 동작한다.
- **Done When**:
  - [ ] Spring Boot 4 + Modulith 프로젝트 초기화 완료
  - [ ] auth / audit / billing / notification 4개 모듈 패키지 구조 생성
  - [ ] `./gradlew build` 성공
  - [ ] Modulith 구조 검증 테스트 통과 (`ApplicationModulesTest`)
  - [ ] Docker 이미지 빌드 성공
- **Scope**:
  - In Scope:
    - Spring Boot 4 + Modulith 프로젝트 생성
    - 4개 모듈 패키지 구조 (auth, audit, billing, notification)
    - build.gradle 의존성 설정
    - ApplicationModulesTest 작성
    - Dockerfile 작성
  - Out of Scope:
    - 비즈니스 로직 구현
    - DB 마이그레이션
    - 외부 서비스 연동
- **Input**: 03_아키텍처_정의서 §Modulith 구조, Spring Boot 4 + Modulith 설정 가이드
- **Instructions**:
  1. Spring Initializr로 프로젝트 생성 (Spring Boot 4, Java 21, Gradle)
  2. Modulith 의존성 추가 (spring-modulith-starter, spring-modulith-test)
  3. auth / audit / billing / notification 패키지 생성 + package-info.java
  4. 각 모듈에 빈 Controller + Service 클래스 생성
  5. ApplicationModulesTest 작성 및 통과 확인
  6. Dockerfile 작성 (multi-stage build)
  7. docker compose에서 platform-svc 실행 확인
- **Output Format**: `platform-svc/` 프로젝트 디렉토리 + Dockerfile + 테스트 통과 스크린샷
- **Constraints**:
  - Java 21 + Spring Boot 4 + Modulith
  - 모듈 간 순환 의존 금지
  - Gradle 빌드 시간 < 30초
- **Duration**: 1일
- **RULE Reference**: wiki 03_아키텍처_정의서 §Modulith, wiki 18_기술_스택_정의서
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 2: OAuth 회원가입/로그인

- **Step Goal**: 사용자가 Google/GitHub/Apple OAuth를 통해 회원가입하고 로그인할 수 있다.
- **Done When**:
  - [ ] Google OAuth 로그인 → 신규 사용자 자동 회원가입
  - [ ] GitHub OAuth 로그인 → 신규 사용자 자동 회원가입
  - [ ] 기존 사용자 OAuth 로그인 시 기존 계정과 매핑
  - [ ] oauth_identities 테이블에 provider/provider_id 저장 (users 테이블이 아닌 별도 테이블 — ERD 기준)
  - [ ] 통합 테스트 통과
- **Scope**:
  - In Scope:
    - Spring Security OAuth2 Client 설정
    - Google OAuth 연동 (회원가입 + 로그인)
    - GitHub OAuth 연동 (회원가입 + 로그인)
    - Apple OAuth 연동 (회원가입 + 로그인)
    - Microsoft OAuth 확장 TODO 문서화
    - users 테이블 설계 + Flyway 마이그레이션
    - OAuth 콜백 핸들러
    - 통합 테스트
  - Out of Scope:
    - 이메일/비밀번호 로그인
    - 소셜 프로필 동기화
    - 계정 연동 해제
- **Input**: Google/GitHub/Apple OAuth Client ID/Secret, Spring Security OAuth2 문서
- **Instructions**:
  1. application.yml에 OAuth2 Client 설정 (Google, GitHub, Apple)
  2. users 테이블 DDL 작성 + Flyway V1 마이그레이션
     - provider/provider_id는 users 테이블에 넣지 않음 (ERD 기준: oauth_identities 별도 테이블로 분리)
  3. oauth_identities 테이블 DDL 작성 (id, user_id, provider, provider_id, created_at) + Flyway 마이그레이션
  4. OAuth2UserService 구현 (사용자 조회/생성 로직)
  5. OAuth2 성공 핸들러 구현 (JWT 발급 연계)
  6. 신규 사용자 자동 회원가입 로직 구현 (아키텍처: 가입 시 자동 테넌트 생성 — OAuth 신규 가입 시 테넌트 자동 생성 필수)
  7. 기존 사용자 매핑 로직 구현 (email 기준)
  8. 통합 테스트 작성 (MockOAuth2User)
- **Output Format**: auth 모듈 코드 + Flyway 마이그레이션 + 테스트 코드
- **Constraints**:
  - OAuth2 Authorization Code Grant만 사용
  - 사용자 정보 최소 수집 (email, name, avatar)
  - OAuth 상태값(state) CSRF 방어 필수
- **Duration**: 2일
- **RULE Reference**: wiki 03_아키텍처_정의서 §인증, wiki 18_기술_스택_정의서 §Spring Security
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 3: JWT + MFA 기초

- **Step Goal**: 인증된 사용자에게 JWT Access/Refresh Token을 발급하고, MFA(TOTP) 등록 기초가 동작한다.
- **Done When**:
  - [ ] OAuth 로그인 성공 시 JWT Access Token (15분) 발급
  - [ ] Refresh Token (7일) 발급 + Redis 저장
  - [ ] Access Token 만료 시 Refresh Token으로 갱신
  - [ ] MFA(TOTP) 시크릿 생성 + QR 코드 URL 반환
  - [ ] TOTP 코드 검증 API 동작
  - [ ] 단위/통합 테스트 통과
- **Scope**:
  - In Scope:
    - JWT Access/Refresh Token 발급 로직
    - Refresh Token Redis 저장/조회/삭제
    - Token 갱신 API (`POST /auth/refresh`)
    - TOTP 시크릿 생성 (`POST /auth/mfa/setup`)
    - TOTP 검증 API (`POST /auth/mfa/verify`)
    - 단위/통합 테스트
  - Out of Scope:
    - MFA 강제 적용 정책
    - Token 블랙리스트 (W2)
    - SMS/이메일 MFA
- **Input**: JWT 라이브러리 (jjwt), TOTP 라이브러리 (GoogleAuth), Redis 접속 정보
- **Instructions**:
  1. JWT 유틸리티 클래스 구현 (생성, 파싱, 검증)
  2. Access Token 발급 로직 (claims: userId, roles, exp=15min)
  3. Refresh Token 발급 + Redis 저장 (key: userId, TTL: 7d)
  4. Token 갱신 엔드포인트 구현 (`POST /auth/refresh`)
  5. TOTP 시크릿 생성 + QR 코드 URL 생성 API
  6. TOTP 코드 검증 API 구현
  7. Security Filter에 JWT 검증 추가
  8. 단위 테스트 + 통합 테스트 작성
- **Output Format**: auth 모듈 JWT/MFA 코드 + Redis 설정 + 테스트 코드
- **Constraints**:
  - Access Token: 15분, Refresh Token: 7일
  - Refresh Token 이중 저장 전략: Redis (활성 세션 관리, TTL 7일) + DB refresh_tokens 테이블 (영속성/감사용)
  - TOTP는 RFC 6238 준수
  - JWT 서명: RS256
- **Duration**: 2일
- **RULE Reference**: wiki 03_아키텍처_정의서 §인증, wiki 10_환경_설정_템플릿 §Redis
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## W2 (2026-05-18 ~ 2026-05-22, 5 영업일)

---

## Step 4: Stripe Checkout 결제 및 Webhook 플랜 활성화

- **Step Name**: Stripe 결제/Webhook
- **Step Goal**: 사용자가 Stripe Checkout으로 유료 플랜을 결제하고, Webhook으로 플랜이 활성화된다.
- **Done When**:
  - [ ] Stripe Checkout 세션 생성 API 동작
  - [ ] 사용자가 Checkout 페이지에서 결제 완료
  - [ ] Webhook(checkout.session.completed) 수신 시 플랜 활성화
  - [ ] subscriptions 테이블에 구독 정보 저장
  - [ ] payment_history 테이블에 결제 이력 저장
  - [ ] 통합 테스트 통과
- **Scope**:
  - In Scope:
    - Stripe Checkout 세션 생성 API
    - Webhook 핸들러 (checkout.session.completed, invoice.paid, customer.subscription.deleted)
    - subscriptions 테이블 설계 + Flyway 마이그레이션
    - payment_history 테이블 설계 + Flyway 마이그레이션
    - 플랜 활성화/비활성화 로직
    - Webhook 서명 검증
  - Out of Scope:
    - 환불 처리
    - 플랜 업그레이드/다운그레이드
    - 청구서 PDF 생성
- **Input**: Stripe API Key, Stripe Webhook Secret, 플랜/가격 정보
- **Instructions**:
  1. subscriptions 테이블 DDL 작성 + Flyway 마이그레이션
  2. payment_history 테이블 DDL 작성 + Flyway 마이그레이션
  3. Stripe Checkout 세션 생성 API 구현 (`POST /billing/checkout` — 구 `/payments/checkout`)
  4. Webhook 엔드포인트 구현 (`POST /billing/webhooks` — 구 `/webhooks/stripe`)
  5. Webhook 서명 검증 로직 구현
  6. checkout.session.completed → 구독 활성화 로직
  7. 구독 상태 조회 API (`GET /billing/subscription` — 구 `/subscriptions/me`)
  8. 통합 테스트 (Stripe Test Mode)
- **Output Format**: billing 모듈 결제 코드 + Flyway 마이그레이션 + 테스트 코드
- **Constraints**:
  - Stripe Test Mode 사용 (dev/staging)
  - Webhook 서명 검증 필수 (replay attack 방지)
  - 멱등성 보장 (동일 이벤트 중복 처리 방지)
- **Duration**: 2.5일
- **RULE Reference**: wiki 03_아키텍처_정의서 §결제, wiki 18_기술_스택_정의서 §Stripe
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 5: FCM 디바이스 등록

- **Step Name**: FCM 디바이스 등록
- **Step Goal**: 사용자가 FCM 푸시 알림을 받기 위해 디바이스를 등록할 수 있다.
- **Done When**:
  - [ ] `POST /notifications/devices` → FCM 토큰 등록 (구 `/devices`)
  - [ ] `DELETE /notifications/devices/{id}` → 디바이스 해제 (구 `/devices/{id}`)
  - [ ] ~~`GET /devices/me`~~ → Wiki에 정의되지 않은 엔드포인트 (디바이스 관리는 POST/DELETE만 지원)
  - [ ] devices 테이블 Flyway 마이그레이션 완료
  - [ ] 통합 테스트 통과
- **Scope**:
  - In Scope:
    - device_tokens 테이블 설계 + Flyway 마이그레이션 (구 `devices` → ERD 기준 `device_tokens`)
    - FCM 토큰 등록 API (`POST /notifications/devices`)
    - 디바이스 해제 API (`DELETE /notifications/devices/{id}`)
    - 중복 토큰 방지 로직
    - (내 디바이스 목록 조회 API 제외 — Wiki 미정의)
  - Out of Scope:
    - 실제 푸시 발송 (W3 Step 7)
    - 토큰 갱신 자동화
    - APNs (iOS) 직접 연동
- **Input**: FCM 프로젝트 설정, JWT 인증 토큰
- **Instructions**:
  1. device_tokens 테이블 DDL 작성 (id, user_id, token, platform, is_active, created_at)
     - 구 `devices` → ERD 기준 `device_tokens`
     - 구 `fcm_token` → `token`, 구 `device_type` → `platform`
     - `platform` 값: `ios`, `android`, `web` (소문자, 구 ANDROID/IOS/WEB → 소문자)
     - `is_active`: 토큰 활성 여부
  2. Flyway 마이그레이션 파일 생성
  3. DeviceToken 엔티티 + Repository 작성
  4. DeviceTokenService 구현 (register, unregister)
  5. DeviceTokenController REST API 구현 (`POST /notifications/devices`, `DELETE /notifications/devices/{id}`)
  6. 중복 토큰 방지 (UNIQUE 제약)
  7. 통합 테스트 작성
- **Output Format**: notification 모듈 device_tokens 코드 + Flyway 마이그레이션 + 테스트 코드
- **Constraints**:
  - 한 사용자 최대 5개 디바이스 등록
  - token UNIQUE 제약
  - platform: `ios`, `android`, `web` (소문자, ERD 기준)
- **Duration**: 0.5일
- **RULE Reference**: wiki 03_아키텍처_정의서 §알림, wiki 18_기술_스택_정의서 §FCM
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## W3 (2026-05-26 ~ 2026-05-29, 5/25 부처님오신날 제외 — W2 잔무 + 발행 준비)

---

## Step 6: Kafka 이벤트 기반 Audit Log 자동 기록

- **Step Name**: Kafka Audit Log
- **Step Goal**: 시스템이 Kafka 이벤트를 소비하여 audit_logs에 자동 기록한다.
- **Done When**:
  - [ ] Kafka Consumer가 지정 토픽 이벤트 수신
  - [ ] audit_logs 테이블에 이벤트 자동 저장
  - [ ] 관리자가 audit_logs 조회 API로 이력 확인 가능
  - [ ] 이벤트 유실 없이 at-least-once 보장
  - [ ] 통합 테스트 통과
- **Scope**:
  - In Scope:
    - audit_logs 테이블 설계 + Flyway 마이그레이션
    - Kafka Consumer 구현 (다중 토픽 구독)
    - 이벤트 → audit_logs 변환/저장 로직
    - Audit Log 조회 API (관리자 전용)
    - Consumer 오류 처리 (DLQ)
  - Out of Scope:
    - 실시간 스트리밍 대시보드
    - 로그 보존 정책 자동화
    - 외부 SIEM 연동
- **Input**: Kafka 토픽 목록, 이벤트 스키마, 관리자 권한 정보
- **Instructions**:
  1. audit_logs 테이블 DDL 작성 (id, action, user_id, old_value, new_value, ip_address, user_agent, created_at)
     - 구 `event_type` → `action` (ERD 기준)
     - 구 `actor_id` → `user_id` (ERD 기준)
     - 구 `payload` → `old_value`/`new_value` 분리 (ERD 기준)
     - 구 `timestamp` → `created_at` (ERD 기준)
     - 추가: `ip_address`, `user_agent`
  2. Flyway 마이그레이션 파일 생성
  3. Kafka Consumer 구현 (KafkaListener, 다중 토픽)
  4. 이벤트 → AuditLog 변환 로직 구현
  5. AuditLogRepository 저장 로직
  6. 조회 API 구현 (`GET /admin/audit-logs` + 필터/페이징)
  7. Consumer 오류 시 DLQ 전송 설정
  8. 통합 테스트 (Embedded Kafka)
- **Output Format**: audit 모듈 Audit Log 코드 + Kafka Consumer + 테스트 코드 (audit/는 독립 모듈 — admin 모듈의 일부가 아님)
- **Constraints**:
  - at-least-once 보장 (멱등 저장)
  - audit_logs 보존: 90일
  - 조회 API: 관리자 권한 필수
- **Duration**: 1.5일
- **RULE Reference**: wiki 03_아키텍처_정의서 §이벤트 설계, wiki 09_Git_규칙_정의서 §커밋 컨벤션
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 7: FCM 푸시 및 SES 이메일 알림 발송

- **Step Name**: FCM 푸시/SES 이메일 알림
- **Step Goal**: 사용자에게 notification.send 토픽 이벤트 기반 FCM 푸시와 SES 이메일 알림이 발송된다.
- **Done When**:
  - [ ] notification.send 토픽 이벤트 수신 → FCM 푸시 발송 (gamification/community 토픽을 직접 소비하지 않고 notification.send 토픽을 통해 수신)
  - [ ] notification.send 토픽 이벤트 수신 → SES 이메일 발송
  - [ ] card.review.due 이벤트 → notification.send 토픽 경유 → FCM 푸시 + SES 이메일 발송
  - [ ] 알림 발송 이력 저장
  - [ ] 발송 실패 시 재시도 로직 동작
  - [ ] 통합 테스트 통과
- **Scope**:
  - In Scope:
    - Kafka Consumer (notification.send 토픽 — gamification/community 토픽 직접 소비 아님)
    - FCM 푸시 발송 로직 (Firebase Admin SDK)
    - SES 이메일 발송 로직 (AWS SES SDK)
    - 알림 발송 이력 테이블 + 저장
    - 발송 실패 재시도 (최대 3회)
    - 알림 템플릿 관리
  - Out of Scope:
    - 알림 설정 (사용자 선호도) 관리
    - SMS 알림
    - 실시간 웹소켓 알림
- **Input**: Kafka 이벤트, FCM 서비스 계정, AWS SES 설정, 디바이스 토큰 목록
- **Instructions**:
  1. notifications 테이블 DDL + Flyway 마이그레이션 (구 `notification_history` → ERD 기준 `notifications`)
  2. Kafka Consumer 구현 (notification.send 토픽 구독 — gamification/community 토픽 직접 구독 아님)
  3. FCM 푸시 발송 서비스 구현 (Firebase Admin SDK)
  4. SES 이메일 발송 서비스 구현 (AWS SES SDK)
  5. 이벤트 타입별 알림 라우팅 로직 (푸시/이메일/둘 다)
  6. 발송 실패 재시도 로직 (exponential backoff, 최대 3회)
  7. 발송 이력 저장 로직
  8. 통합 테스트 (Mock FCM/SES)
- **Output Format**: notification 모듈 발송 코드 + Kafka Consumer + 테스트 코드
- **Constraints**:
  - FCM 발송 지연: 이벤트 수신 후 10초 이내
  - SES 발송: 이벤트 수신 후 30초 이내
  - 재시도: exponential backoff (1s, 2s, 4s)
  - 일일 이메일 발송 한도: 사용자당 10건
- **Duration**: 2일
- **RULE Reference**: wiki 03_아키텍처_정의서 §알림, wiki 18_기술_스택_정의서 §FCM/SES
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 8: 관리자 테넌트/사용자 관리

- **Step Name**: 관리자 테넌트/사용자 관리
- **Step Goal**: 관리자가 테넌트와 사용자를 관리(목록/검색/정지/삭제)할 수 있다.
- **Done When**:
  - [ ] `GET /admin/users` → 사용자 목록 조회 (페이징 + 검색, Wiki 추가 예정 `/admin/users/*`)
  - [ ] `PUT /admin/users/{id}/status` → 사용자 상태 변경 (suspend/activate)
  - [ ] `DELETE /admin/users/{id}` → 사용자 삭제 (soft delete, Wiki 추가 예정)
  - [ ] `GET /admin/tenants` → 테넌트 목록 조회
  - [ ] `PUT /admin/tenants/{id}/status` → 테넌트 상태 변경 (body: `{ "status": "suspended" }`) (구 `/suspend` → Wiki 기준 `/status`)
  - [ ] 관리자 권한 검증 동작
  - [ ] 통합 테스트 통과
- **Scope**:
  - In Scope:
    - 관리자 사용자 목록/검색 API
    - 사용자 정지/삭제 API
    - 테넌트 목록/검색 API
    - 테넌트 정지 API
    - 관리자 권한 검증 (ADMIN role)
    - 통합 테스트
  - Out of Scope:
    - 테넌트 생성 (자동 생성 정책)
    - 사용자 데이터 내보내기 (GDPR)
    - 관리자 활동 로그 (audit_logs에서 처리)
- **Input**: users/tenants 테이블, 관리자 JWT, PRD 관리자 기능 요구사항
- **Instructions**:
  1. 관리자 권한 검증 어노테이션/AOP 구현 (@AdminOnly)
  2. 사용자 목록 조회 API (페이징 + 이름/이메일 검색)
  3. 사용자 정지 API (status → SUSPENDED, JWT 블랙리스트)
  4. 사용자 삭제 API (soft delete, 개인정보 마스킹)
  5. 테넌트 목록 조회 API (페이징)
  6. 테넌트 상태 변경 API: `PUT /admin/tenants/{id}/status` (body: `{ "status": "suspended" }`) — 구 `/suspend`
  7. 통합 테스트 작성 (관리자/비관리자 시나리오)
- **Output Format**: 관리자 기능 코드 + 테스트 코드 (관리자 기능은 독립 admin 모듈이 아닌 각 담당 모듈에 분산: 사용자 관리 → auth 모듈, 테넌트 관리 → billing 모듈, 감사 조회 → audit 모듈)
- **Constraints**:
  - 관리자 권한(ADMIN role) 필수
  - 사용자 삭제 시 개인정보 마스킹 (GDPR 준수)
  - 정지된 사용자 로그인 차단
- **Duration**: 1.5일
- **RULE Reference**: wiki 03_아키텍처_정의서 §REST API 규칙, wiki 09_Git_규칙_정의서 §커밋 컨벤션
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## W4 (2026-06-01 ~ 2026-06-05, 6/3 지방선거 제외 — notification 소비 + audit + admin)

---

## Step 9: 인증/결제 전체 E2E 테스트

- **Step Name**: 인증/결제 E2E 테스트
- **Step Goal**: 인증/결제 전체 E2E 시나리오(회원가입→로그인→JWT→MFA→결제→로그아웃)가 통과한다.
- **Done When**:
  - [ ] 회원가입 → OAuth 로그인 → JWT 발급 시나리오 통과
  - [ ] MFA 등록 → TOTP 검증 시나리오 통과
  - [ ] Stripe Checkout → Webhook → 플랜 활성화 시나리오 통과
  - [ ] Token 갱신 → 로그아웃 시나리오 통과
  - [ ] 전체 E2E 시나리오 연속 실행 통과
  - [ ] 실패 케이스 식별 및 이슈 등록
- **Scope**:
  - In Scope:
    - OAuth 회원가입/로그인 E2E
    - JWT 발급/갱신/만료 E2E
    - MFA 등록/검증 E2E
    - Stripe 결제/Webhook E2E
    - 로그아웃/토큰 무효화 E2E
    - 실패 케이스 이슈 등록
  - Out of Scope:
    - 부하/성능 테스트
    - 보안 침투 테스트
    - 다른 서비스 연동 E2E
- **Input**: staging 환경, Stripe Test Mode, OAuth Test 계정
- **Instructions**:
  1. E2E 테스트 환경 설정 (staging + Stripe Test Mode)
  2. 회원가입 → 로그인 시나리오 작성 및 실행
  3. JWT 발급 → 갱신 → 만료 시나리오 작성 및 실행
  4. MFA 등록 → 검증 시나리오 작성 및 실행
  5. Stripe Checkout → Webhook → 플랜 활성화 시나리오 실행
  6. 전체 시나리오 연속 실행 (Happy Path)
  7. 실패 케이스 식별 및 이슈 등록
- **Output Format**: E2E 테스트 코드 + 테스트 결과 리포트
- **Constraints**:
  - Happy Path 100% 통과 필수
  - 테스트 실행 시간 < 3분
  - 테스트 데이터 자동 정리 (teardown)
- **Duration**: 1.5일
- **RULE Reference**: wiki 03_아키텍처_정의서 §테스트 전략, wiki 09_Git_규칙_정의서 §이슈 관리
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 10: P0 버그 수정 및 알림 안정화

- **Step Name**: P0 버그 수정/알림 안정화
- **Step Goal**: platform-svc의 P0 버그가 모두 수정되고 알림 발송이 안정화된다.
- **Done When**:
  - [ ] P0 버그 목록 확인 및 전수 수정 완료
  - [ ] 수정된 버그 재현 테스트 통과
  - [ ] FCM 푸시 발송 성공률 > 99%
  - [ ] SES 이메일 발송 성공률 > 99%
  - [ ] 알림 발송 지연 SLA 충족 (FCM < 10s, SES < 30s)
  - [ ] 회귀 테스트 전체 통과
- **Scope**:
  - In Scope:
    - P0 버그 전수 수정
    - 버그 수정 후 재현 테스트
    - FCM/SES 발송 안정화 (재시도 로직 보강)
    - 알림 발송 모니터링 메트릭 추가
    - 회귀 테스트 실행
  - Out of Scope:
    - P1/P2 버그 수정
    - 새 기능 추가
    - 성능 최적화 (별도 태스크)
- **Input**: P0 버그 목록 (GitHub Issues), 알림 발송 로그, 모니터링 메트릭
- **Instructions**:
  1. P0 버그 목록 확인 (GitHub Issues 필터)
  2. 각 버그 재현 → 원인 분석 → 수정
  3. 수정 후 재현 테스트 작성 및 통과 확인
  4. FCM 발송 실패 원인 분석 및 재시도 로직 보강
  5. SES 발송 실패 원인 분석 및 안정화
  6. 알림 발송 메트릭 추가 (성공/실패/지연)
  7. 전체 회귀 테스트 실행 및 통과 확인
- **Output Format**: 버그 수정 PR 목록 + 회귀 테스트 결과 + 안정화 리포트
- **Constraints**:
  - P0 버그 0건 달성 필수
  - 수정 시 회귀 방지 (테스트 추가 필수)
  - 알림 발송 성공률 > 99%
- **Duration**: 1.5일
- **RULE Reference**: wiki 09_Git_규칙_정의서 §이슈 관리, wiki 03_아키텍처_정의서 §알림
- **Assignee**: @platform-owner
- **Reviewer**: @team-lead

**Status**: [ ] Not Started / [ ] In Progress / [ ] Done
