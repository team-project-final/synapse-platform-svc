# WORKFLOW: @platform-owner — Week 3

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-05-26 ~ 2026-05-29, 4 영업일
> **PRD**: [PRD_W3.md](../prd/PRD_W3.md)

---

## Step 6: audit 모듈 — Kafka 이벤트 소비 → audit_logs 적재

### 1.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W3 해당 요구사항 확인 (감사 로그)
- [ ] Duration 산정 확인

### 1.2 요구사항 분석
- [ ] 소비 대상 Kafka 토픽 목록 정의 (audit.event, user.registered, billing.subscription.changed — 아키텍처 표준. gamification.*, community.*, card.* 토픽은 audit 모듈 소비 대상 아님)
- [ ] audit_logs 테이블 스키마 요건 분석
- [ ] 90일 보존 정책 구현 방안 분석 (pg_partman / 스케줄러 삭제)
- [ ] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [ ] 인증 필요 여부: No (내부 Kafka 소비, API 미노출)
- [ ] 권한 종류: 시스템 내부 처리
- [ ] audit_logs 접근 권한: 관리자 전용
- [ ] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] audit_logs 테이블 설계 (id, action, user_id, resource_type, resource_id, old_value jsonb, new_value jsonb, ip_address inet, user_agent, created_at)
- [ ] 인덱스 설계 (action, user_id, created_at)
- [ ] 파티셔닝 전략 설계 (월별 파티션, 90일 보존)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 민감 정보 암호화: payload 내 PII 마스킹 정책 정의
- [ ] 90일 이후 자동 삭제 검증 방안
- [ ] audit_logs 위변조 방지 (append-only, 삭제 API 미제공)
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] AuditLog Entity 작성 (audit_logs 매핑)
- [ ] AuditEventMessage DTO 정의 (Kafka 소비용)
- [ ] AuditLogResponse DTO 정의 (관리자 조회용)
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] AuditLogRepository 인터페이스 작성
- [ ] findByAction, findByUserId 커스텀 쿼리
- [ ] 90일 이전 데이터 삭제 쿼리 (deleteByCreatedAtBefore)

### 1.8 Service + Test
- [ ] AuditKafkaConsumer 구현 (consumer group: audit-consumer-group)
- [ ] AuditLogService 구현 (이벤트 파싱 → audit_logs INSERT)
- [ ] 90일 보존 스케줄러 구현 (@Scheduled, 매일 실행)
- [ ] 단위 테스트 작성 (이벤트 소비 → 적재 검증)
- [ ] 테스트 통과 확인

### 1.9 Controller + Test
- [ ] GET /admin/audit-logs 엔드포인트 구현 (관리자 전용)
- [ ] 페이징 + 필터링 (action, user_id, 기간)
- [ ] 슬라이스 테스트 (@WebMvcTest)
- [ ] 403 Forbidden 테스트 (비관리자 접근)
- [ ] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음 (관리자 화면은 frontend 담당)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 6 Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 7: notification Kafka 연동 — 이벤트 소비 → FCM 푸시 + SES 이메일 발송

### 1.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W3 해당 요구사항 확인 (알림 발송)
- [ ] Duration 산정 확인

### 1.2 요구사항 분석
- [ ] 소비 대상 토픽 정의 (`notification.send` 토픽 — 아키텍처 표준 패턴. 각 서비스(gamification, community, card 등)는 직접 notification 모듈을 호출하는 대신 `notification.send` 토픽에 이벤트를 발행하며, notification 모듈은 이 단일 토픽만 소비하여 채널별 발송을 처리함)
- [ ] 알림 채널별 발송 조건 정의 (FCM: 모바일, SES: 이메일)
- [ ] 사용자 알림 설정 (opt-in/opt-out) 반영 로직 분석
- [ ] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [ ] 인증 필요 여부: No (내부 Kafka 소비)
- [ ] FCM 서비스 계정 키 관리 방안 (External Secrets)
- [ ] SES IAM Role 최소 권한 설정
- [ ] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] notifications 테이블 설계 (id, user_id, channel, title, body, category, data_json, template_code, is_read, created_at)
- [ ] 참고: notification_settings 테이블 → notification_preferences 테이블로 통합 (컬럼 기반 구조: push_enabled, email_enabled, in_app_enabled, quiet_hours_start, quiet_hours_end)
- [ ] 인덱스 설계 (user_id + is_read, created_at)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] FCM 토큰 암호화 저장
- [ ] SES 발송 도메인 SPF/DKIM 설정 확인
- [ ] 알림 페이로드 민감정보 제외 확인
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] Notification Entity 작성 (channel, category, data_json, template_code 컬럼 포함)
- [ ] NotificationEventMessage DTO 정의 (Kafka 소비용)
- [ ] NotificationResponse DTO 정의 (사용자 조회용)
- [ ] NotificationPreferenceRequest/Response DTO 정의 (notification_preferences 기반)
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] NotificationRepository 인터페이스 작성
- [ ] NotificationPreferenceRepository 인터페이스 작성
- [ ] findByUserIdAndIsReadFalse 커스텀 쿼리

### 1.8 Service + Test
- [ ] NotificationKafkaConsumer 구현 (`notification.send` 토픽 소비 — 아키텍처 표준 패턴. gamification/community/card 등 각 서비스는 `notification.send` 토픽에 발행하며, 이 Consumer가 라우팅 처리)
- [ ] NotificationService 구현 (알림 생성 + 채널별 발송 분기)
- [ ] FcmPushService 구현 (Firebase Admin SDK → FCM 발송)
- [ ] SesEmailService 구현 (AWS SES SDK → 이메일 발송)
- [ ] 사용자 알림 설정 확인 로직 (notification_preferences — push_enabled/email_enabled/in_app_enabled 기반 opt-out 시 발송 스킵)
- [ ] 단위 테스트 작성 (각 서비스별 Mockito)
- [ ] 테스트 통과 확인

### 1.9 Controller + Test
- [ ] GET /notifications 엔드포인트 구현 (사용자별 알림 목록)
- [ ] PATCH /notifications/{id}/read 엔드포인트 구현 (읽음 처리)
- [ ] GET /notifications/preferences 엔드포인트 구현
- [ ] PUT /notifications/preferences 엔드포인트 구현
- [ ] 슬라이스 테스트 (@WebMvcTest)
- [ ] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 7 Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 8: 테넌트/사용자 관리 API — 관리자 사용자 목록/검색/정지/삭제

### 1.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W3 해당 요구사항 확인 (관리자 사용자 관리)
- [ ] Duration 산정 확인

### 1.2 요구사항 분석
- [ ] 관리자 사용자 목록 조회 (페이징, 정렬) 요건 분석
- [ ] 사용자 검색 (이름, 이메일) 요건 분석
- [ ] 사용자 정지/삭제 비즈니스 로직 분석 (정지: status 변경, 삭제: soft delete)
- [ ] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [ ] 인증 필요 여부: Yes (관리자 전용)
- [ ] 권한 종류: ROLE_ADMIN
- [ ] 공개 API 여부: No
- [ ] 사용자 정지/삭제 시 감사 로그 기록 필수
- [ ] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] users 테이블 확장: status 컬럼 확인 (active|suspended|deleted — 소문자)
- [ ] suspended_at, deleted_at 컬럼 추가
- [ ] 인덱스 설계 (status, email LIKE 검색용)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 관리자 본인 삭제/정지 방지 로직
- [ ] 정지/삭제 시 활성 세션 즉시 무효화 (Redis 토큰 삭제)
- [ ] 행 단위 접근 제어: 관리자만 조회/수정 가능
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] AdminUserListResponse DTO 정의 (id, email, display_name, status: active|suspended|deleted, created_at)
- [ ] AdminUserSearchRequest DTO 정의 (query, status, page, size)
- [ ] UserSuspendRequest DTO 정의 (reason)
- [ ] User Entity 수정 (status 필드 추가)
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] UserRepository 확장: findByStatusAndNameContaining 커스텀 쿼리
- [ ] Specification 기반 동적 검색 쿼리 구현
- [ ] 페이징/정렬 지원

### 1.8 Service + Test
- [ ] AdminUserService 구현 (목록 조회, 검색, 정지, 삭제)
- [ ] 사용자 정지 로직 (status → suspended, 세션 무효화, audit 이벤트 발행)
- [ ] 사용자 삭제 로직 (status → deleted, soft delete, 세션 무효화, audit 이벤트 발행)
- [ ] 단위 테스트 작성 (Mockito)
- [ ] 테스트 통과 확인

### 1.9 Controller + Test
- [ ] GET /admin/users 엔드포인트 구현 (목록 + 페이징)
- [ ] GET /admin/users?q=검색어 엔드포인트 구현 (쿼리 파라미터 방식 검색)
- [ ] PUT /admin/users/{id}/status 엔드포인트 구현 (suspend/activate)
- [ ] DELETE /admin/users/{id} 엔드포인트 구현 (soft delete)
- [ ] 슬라이스 테스트 (@WebMvcTest)
- [ ] 403 Forbidden 테스트 (비관리자 접근)
- [ ] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 8 Status**: [ ] Not Started / [ ] In Progress / [ ] Done
