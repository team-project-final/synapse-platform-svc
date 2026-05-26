# TASK — Step 5: FCM 디바이스 등록 (검증)

> 출처: TASK_platform.md Step 5

## 상태

- Phase: 리뷰
- 담당 Agent: Worker
- 시작일: 2026-05-21
- 목표 완료일: 2026-05-21

---

## Step Goal

사용자가 FCM 푸시 알림을 받기 위해 디바이스를 등록할 수 있다.

## Done When

- [ ] `POST /notifications/devices` → FCM 토큰 등록
- [ ] `DELETE /notifications/devices/{id}` → 디바이스 해제
- [ ] `device_tokens` 테이블 Flyway 마이그레이션 완료
- [ ] 통합 테스트 통과

## Scope

- In Scope:
  - device_tokens 테이블 DDL + Flyway 마이그레이션 검증
  - POST /notifications/devices 엔드포인트 검증
  - DELETE /notifications/devices/{id} 엔드포인트 검증
  - 중복 토큰 방지 (UNIQUE 제약) 검증
  - 통합 테스트 실행 및 결과 확인
- Out of Scope:
  - 신규 코드 작성
  - 실제 FCM 발송 (Step 7)

## Input

- `src/main/java/com/synapse/platform/notification/` 전체
- `src/main/resources/db/migration/V27__create_device_tokens.sql`
- `src/test/java/com/synapse/platform/notification/DeviceTokenIntegrationTest.java`
- `src/test/java/com/synapse/platform/notification/DeviceTokenServiceTest.java`

## Instructions

1. `V27__create_device_tokens.sql` 읽기 → 컬럼 확인 (id, user_id, token, platform, is_active, created_at + UNIQUE 제약)
2. `DeviceToken.java` 읽기 → 엔티티 필드가 DDL과 일치하는지 확인
3. `DeviceTokenController.java` 읽기 → `POST /notifications/devices`, `DELETE /notifications/devices/{id}` 매핑 확인
4. `DeviceTokenService.java` 읽기 → register/unregister 로직 + 5개 디바이스 한도 제한 확인
5. `DeviceTokenIntegrationTest.java` 읽기 → 401/403 케이스 포함 여부 확인
6. `./gradlew test --tests "*.notification.*"` 실행 → 결과 확인
7. 각 Done When 항목에 대해 PASS/FAIL/PARTIAL 판정 후 HANDOFF.md에 결과 기록

## Output Format

HANDOFF.md에 Done When 항목별 PASS/FAIL/PARTIAL + 근거 (파일명:라인) 기록

## Constraints

- 한 사용자 최대 5개 디바이스 등록
- token UNIQUE 제약
- platform: `ios`, `android`, `web` (소문자)
- JWT 인증 필수 (401 미인증 차단)

## Assignee / Reviewer

- Assignee: Worker (Codex)
- Reviewer: Director (Claude)
