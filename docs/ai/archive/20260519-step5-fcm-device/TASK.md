# TASK — Step 5: FCM 디바이스 등록

> 출처: TASK_platform.md Step 5

## 상태

- Phase: 구현
- 담당 Agent: Worker
- 시작일: 2026-05-19
- 목표 완료일: 2026-05-19

---

## Step Goal

사용자가 FCM 푸시 알림을 받기 위해 디바이스를 등록할 수 있다.

## Done When

- [x] `POST /api/v1/notifications/devices` — FCM 토큰 등록 (JWT 필수)
- [x] `DELETE /api/v1/notifications/devices/{id}` — 디바이스 해제 (JWT 필수, 소유권 검증)
- [x] `V27__create_device_tokens.sql` Flyway 마이그레이션 완료
- [x] 통합 테스트 통과 (`./gradlew test`)
- [x] 신규 코드 JaCoCo 라인 커버리지 80% 이상

## Scope

- In Scope:
  - `device_tokens` 테이블 DDL + Flyway V27
  - FCM 토큰 등록 API (`POST /api/v1/notifications/devices`)
  - 디바이스 해제 API (`DELETE /api/v1/notifications/devices/{id}`)
  - 중복 토큰 UPSERT (user_id 재할당)
  - 사용자당 최대 5개 제한 (신규 토큰만 검사)
- Out of Scope:
  - 실제 FCM 푸시 발송 (Step 7)
  - 내 디바이스 목록 조회 (Wiki 미정의)
  - `firebase-admin` SDK 추가 (Step 7)

## Input

- JWT 인증 (RS256)
- 샘플링 결과: `docs/spike/notification/SAMPLING_STEP5_FCM_DEVICE.md`

## Instructions

1. `V27__create_device_tokens.sql` 작성 — CHECK 제약 포함
2. `Platform` enum + `PlatformConverter(AttributeConverter)` + `@JsonCreator/@JsonValue`
3. `DeviceToken` 엔티티 + `DeviceTokenRepository` (native UPSERT + `@Modifying`)
4. `DeviceRegistrationLimitExceededException` — `BusinessException` 상속, 에러 코드 `PLAT-NOTIFICATION-001`
5. `DeviceTokenService` 구현 (register, unregister)
6. `DeviceTokenController` REST API 구현
7. `NotificationSecurityConfig` — `/api/v1/notifications/**` 전용 `@Order(1)` FilterChain
8. `GlobalExceptionHandler` — `EntityNotFoundException → 404` 핸들러 확인 후 없으면 추가
9. 통합 테스트 작성 (Testcontainers PostgreSQL)

## Output Format

`com.synapse.platform.notification` 패키지 하위 코드 + Flyway V27 + 테스트

## Constraints

- 패키지 루트: `com.synapse.platform.notification.*`
- 최대 5개 디바이스 (신규 token만 count 검사, user 단위 cross-tenant)
- `token` UNIQUE + UPSERT `ON CONFLICT (token) DO UPDATE SET tenant_id, user_id, updated_at`
- `tenant_id` 필수 — ERD 표준 컬럼, `UserApi.findById(userId).defaultTenantId()`로 resolve
- `is_active` 필수 — DEFAULT TRUE
- `platform`: `ios`, `android`, `web` (소문자, DB CHECK 제약)
- `@Modifying(clearAutomatically = true, flushAutomatically = true)` 필수
- `firebase-admin` 의존성 추가 금지
- 테스트 커버리지 80% 이상
- 통합 테스트: tenant 선행 생성 필수 (`device_tokens.tenant_id` FK)

## Duration

0.5일

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead

## Worker Report

- Director 보고 필요 사항: `docs/ai/current/WORKER_REPORT.md`
