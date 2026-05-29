# WORKFLOW: @platform-owner — Week 3

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-05-26 ~ 2026-05-29, 4 영업일
> **PRD**: [PRD_W3.md](../prd/PRD_W3.md)

---

## Step 6: audit 모듈 — Kafka 이벤트 소비 → audit_logs 적재

### 1.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W3 해당 요구사항 확인 (감사 로그)
- [x] Duration 산정 확인

### 1.2 요구사항 분석
- [x] 소비 대상 Kafka 토픽 목록 정의 (`platform.auth.user-registered-v1`)
- [x] audit_logs 테이블 스키마 요건 분석
- [x] 90일 보존 정책 구현 방안 분석 (@Scheduled 스케줄러 삭제)
- [x] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [x] 인증 필요 여부: No (내부 Kafka 소비, API 미노출)
- [x] 권한 종류: 시스템 내부 처리
- [x] audit_logs 접근 권한: 관리자 전용 (ROLE_ADMIN)
- [x] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [x] audit_logs 테이블 설계 (id, event_id, action, user_id, resource_type, resource_id, old_value jsonb, new_value jsonb, ip_address, user_agent, created_at)
- [x] 인덱스 설계 (event_id UNIQUE, action, user_id, created_at)
- [x] outbox_events 테이블 설계 (Producer 유실 방지)
- [x] Duration(final) 갱신

### 1.5 Security 2차 검토
- [x] 90일 이후 자동 삭제 검증 방안
- [x] audit_logs 위변조 방지 (append-only, 삭제 API 미제공)
- [x] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [x] AuditLog Entity 작성 (audit_logs 매핑, JSONB @JdbcTypeCode)
- [x] AuditLogResponse DTO 정의 (oldValue, userAgent 포함 전체 컬럼)
- [x] OutboxEvent Entity 작성 (outbox_events 매핑)
- [x] Output Format → TASK 반영

### 1.7 Repository 구현
- [x] AuditLogRepository 인터페이스 작성
- [x] findByAction, findByUserId 커스텀 쿼리
- [x] 90일 이전 데이터 삭제 쿼리 (deleteByCreatedAtBefore)
- [x] OutboxEventRepository (claimPending, resetTimedOutPublishing JPQL)

### 1.8 Service + Test
- [x] AuditKafkaConsumer 구현 (consumer group: audit-consumer-group, ErrorHandlingDeserializer)
- [x] AuditLogService 구현 (이벤트 파싱 → audit_logs INSERT, DataIntegrityViolationException 멱등)
- [x] 90일 보존 스케줄러 구현 (@Scheduled cron 매일 03:00)
- [x] UserEventPublisher 구현 (outbox 저장, tenantId null 방어)
- [x] OutboxEventPublisher 구현 (PUBLISHING lease, async 실패 기록)
- [x] 단위 테스트 작성 (AuditLogServiceTest, OutboxEventPublisherTest 등)
- [x] 테스트 통과 확인

### 1.9 Controller + Test
- [x] GET /api/v1/admin/audit-logs 엔드포인트 구현 (관리자 전용)
- [x] 페이징 + 필터링 (action, userId)
- [x] @PreAuthorize("hasRole('ADMIN')") + @EnableMethodSecurity
- [x] AuditLogControllerTest (@WebMvcTest)
- [x] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [x] Flutter 화면 연동: 해당 없음 (관리자 화면은 frontend 담당)
- [x] AuditKafkaIntegrationTest (EmbeddedKafka + mock Schema Registry)
- [x] `./gradlew test` + `./gradlew check` 전체 통과

**Step 6 Status**: [x] Done (2026-05-28)

---

## Step 7: notification Kafka 연동 — 이벤트 소비 → FCM 푸시 + SES 이메일 발송

### 1.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W3 해당 요구사항 확인 (알림 발송)
- [x] Duration 산정 확인

### 1.2 요구사항 분석
- [x] 소비 대상 토픽 정의 (`notification.send` 토픽)
- [x] 알림 채널별 발송 조건 정의 (FCM: 모바일, SES: 이메일)
- [x] 사용자 알림 설정 (opt-in/opt-out) 반영 로직 분석
- [x] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [x] 인증 필요 여부: No (내부 Kafka 소비)
- [x] FCM 서비스 계정 키 관리 방안 (External Secrets)
- [x] SES IAM Role 최소 권한 설정
- [x] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [x] notifications 테이블 설계 (V31 마이그레이션)
- [x] 인덱스 설계 (user_id, created_at)
- [x] Duration(final) 갱신

### 1.5 Security 2차 검토
- [x] FCM 토큰 암호화 저장
- [x] SES 발송 도메인 SPF/DKIM 설정 확인
- [x] 알림 페이로드 민감정보 제외 확인
- [x] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [x] Notification Entity 작성
- [x] NotificationChannel / NotificationStatus enum 정의
- [x] Output Format → TASK 반영

### 1.7 Repository 구현
- [x] NotificationRepository 인터페이스 작성

### 1.8 Service + Test
- [x] NotificationKafkaConsumer 구현 (`notification.send` 토픽 소비)
- [x] NotificationService 구현 (알림 생성 + 채널별 발송 분기)
- [x] FcmPushService 구현 (Firebase Admin SDK)
- [x] SesEmailService 구현 (AWS SES SDK v2)
- [x] 단위 테스트 작성 (FcmPushServiceTest, SesEmailServiceTest, NotificationServiceTest 등)
- [x] 테스트 통과 확인

### 1.9 Controller + Test
- [x] NotificationKafkaConsumerIT 통합 테스트 작성
- [x] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [x] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [x] RULE Reference → TASK 반영

**Step 7 Status**: [x] Done (2026-05-28)

---

## Step 8: 테넌트/사용자 관리 API — 관리자 사용자 목록/검색/정지/삭제

### 1.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W3 해당 요구사항 확인 (관리자 사용자 관리)
- [x] Duration 산정 확인

### 1.2 요구사항 분석
- [x] 관리자 사용자 목록 조회 (페이징, 정렬) 요건 분석
- [x] 사용자 검색 (이름, 이메일) 요건 분석
- [x] 사용자 정지/삭제 비즈니스 로직 분석 (정지: status 변경, 삭제: soft delete)
- [x] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [x] 인증 필요 여부: Yes (관리자 전용)
- [x] 권한 종류: ROLE_ADMIN
- [x] 공개 API 여부: No
- [x] 사용자 정지/삭제 시 감사 로그 기록 필수
- [x] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [x] users 테이블 확장: status 컬럼 확인 (active|suspended|deleted — 소문자)
- [x] suspended_at, deleted_at 컬럼 추가
- [x] 인덱스 설계 (status, email LIKE 검색용)
- [x] Duration(final) 갱신

### 1.5 Security 2차 검토
- [x] 관리자 본인 삭제/정지 방지 로직
- [x] 정지/삭제 시 활성 세션 즉시 무효화 (Redis 토큰 삭제)
- [x] 행 단위 접근 제어: 관리자만 조회/수정 가능
- [x] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [x] AdminUserListResponse DTO 정의 (id, email, display_name, status: active|suspended|deleted, created_at)
- [x] AdminUserSearchRequest DTO 정의 (query, status, page, size)
- [x] UserSuspendRequest DTO 정의 (reason)
- [x] User Entity 수정 (status 필드 추가)
- [x] Output Format → TASK 반영

### 1.7 Repository 구현
- [x] UserRepository 확장: findByStatusAndNameContaining 커스텀 쿼리
- [x] Specification 기반 동적 검색 쿼리 구현
- [x] 페이징/정렬 지원

### 1.8 Service + Test
- [x] AdminUserService 구현 (목록 조회, 검색, 정지, 삭제)
- [x] 사용자 정지 로직 (status → suspended, 세션 무효화, UserSessionsRevocationRequested 이벤트 발행)
- [x] 사용자 삭제 로직 (status → deleted, soft delete, 세션 무효화, UserSessionsRevocationRequested 이벤트 발행)
- [x] 단위 테스트 작성 (Mockito)
- [x] 테스트 통과 확인

### 1.9 Controller + Test
- [x] GET /api/v1/admin/users 엔드포인트 구현 (목록 + 페이징)
- [x] GET /api/v1/admin/users?q=검색어 엔드포인트 구현 (쿼리 파라미터 방식 검색)
- [x] PUT /api/v1/admin/users/{id}/status 엔드포인트 구현 (suspend/activate)
- [x] DELETE /api/v1/admin/users/{id} 엔드포인트 구현 (soft delete)
- [x] 슬라이스 테스트 (@WebMvcTest)
- [x] 403 Forbidden 테스트 (비관리자 접근)
- [x] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [x] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [x] Swagger API 문서 확인
- [x] RULE Reference → TASK 반영

**Step 8 Status**: [x] Done (2026-05-28)
