# TASK - PLAT-066: Notification 인박스 조회 API

> 출처: 루트 `docs/BACKEND_GAP_platform.md` A-3. 프론트 `notifications/notification_screens.dart`가 목록, 읽음, 안읽음 카운트, 설정 API를 기대하지만 platform-svc는 현재 디바이스 토큰 등록/해제와 이벤트 기반 발송만 제공한다.

## 상태

- Phase: 구현 및 검증 완료
- 담당 Agent: Codex
- 시작일: 2026-06-09
- 작업 브랜치: `feature/PLAT-066-notification-inbox`
- PR base: `dev`

---

## 목표

platform-svc의 기존 `notifications` 테이블을 사용자 알림센터 조회용으로 확장한다.
프론트는 로그인한 사용자의 알림 목록, 안읽음 카운트, 단건 읽음, 전체 읽음 처리를 백엔드 API로 연동할 수 있어야 한다.

## 범위

In Scope:

- `notifications` 읽음 상태 추가: `read_at TIMESTAMPTZ NULL`
- 로그인 사용자 기준 알림 목록 조회 API 추가
- 로그인 사용자 기준 안읽음 카운트 API 추가
- 본인 알림 단건 읽음 처리 API 추가
- 본인 알림 전체 읽음 처리 API 추가
- 알림 설정 API 포함 여부 판단 및 계약 정리
- notification 인박스 단위/슬라이스 테스트 추가

Conditional Scope:

- `GET /api/v1/notifications/settings`
- `PUT /api/v1/notifications/settings`
- 기존 `user_settings.notification_prefs`를 정본으로 사용한다.
- notification 모듈에서 user 내부 repository를 직접 접근하지 않는다.
- 같은 PR에 포함하려면 `UserApi`에 notification prefs 조회/저장 계약을 추가한다.
- 계약 확장이 커지면 settings API는 별도 PLAT 작업으로 분리한다.

Out of Scope:

- FCM/SES 발송 안정화 또는 재시도 정책 변경
- 실시간 WebSocket/SSE 알림
- 프론트 화면 수정
- `TASK_platform.md` 항목 추가 또는 수정
- 다른 서비스 DB/권한 체계 변경

## API 계약

### 알림 목록

`GET /api/v1/notifications?page=0&size=20`

- 인증 필요
- 로그인 사용자 본인 알림만 반환
- 최신순 정렬: `createdAt DESC`
- 인박스 노출 대상은 우선 `channel = FCM`, `status = SENT` 행으로 제한한다.
- 이유: 현재 `notifications`는 `event_id + channel` 단위라 EMAIL 행까지 노출하면 같은 이벤트가 중복 표시될 수 있다. EMAIL 행은 발송 감사/이력 성격으로 유지한다.

응답 예시:

```json
{
  "items": [
    {
      "id": "d6f4d47d-4cb9-4c55-b5c2-7a855fc2e8c4",
      "type": "ACHIEVEMENT_UNLOCKED",
      "title": "새 업적을 획득했습니다",
      "body": "연속 학습 목표를 달성했습니다.",
      "read": false,
      "readAt": null,
      "createdAt": "2026-06-09T14:30:00+09:00",
      "sentAt": "2026-06-09T14:30:01+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 안읽음 카운트

`GET /api/v1/notifications/unread-count`

```json
{
  "count": 3
}
```

### 단건 읽음

`PUT /api/v1/notifications/{id}/read`

- 본인 알림만 처리
- 이미 읽은 알림이면 idempotent 성공
- 타인 알림이거나 존재하지 않으면 404로 처리해 존재 여부를 노출하지 않는다.

### 전체 읽음

`POST /api/v1/notifications/read-all`

```json
{
  "updatedCount": 3
}
```

## 데이터 모델

신규 Flyway migration:

- 파일명 형식: `VyyyyMMddHHmmss__add_notification_read_state.sql`
- `notifications.read_at TIMESTAMPTZ NULL` 추가
- 추천 인덱스:
  - `idx_notifications_inbox_user_created_at` on `(user_id, channel, status, created_at DESC)`
  - `idx_notifications_inbox_unread` on `(user_id, channel, status, read_at)` 또는 PostgreSQL partial index

읽음 판정:

- `read_at IS NULL`이면 unread
- `read_at IS NOT NULL`이면 read

## 결정 사항

- 사용자 식별은 기존 JWT `Authentication.getName()`의 UUID subject를 사용한다.
- 숫자 userId 변환을 새로 만들지 않는다. platform의 `notifications.user_id`는 UUID다.
- inbox 조회는 `NotificationService`의 발송 책임과 분리해 별도 query/service 계층으로 둔다.
- `channel = FCM`, `status = SENT`만 프론트 인박스에 노출한다.
- settings API는 `user_settings.notification_prefs`를 정본 후보로 보되, user 모듈 API 계약 없이는 직접 접근하지 않는다.

- [x] 기존 PLAT-064 current 문서 아카이브 완료
- [x] PLAT-066 작업문서 작성 완료
- [x] `notifications.read_at` migration 추가
- [x] `Notification` entity 읽음 상태 추가
- [x] repository inbox query 추가
- [x] inbox service 추가
- [x] inbox controller 추가
- [x] request/response DTO 추가
- [x] settings API 포함 여부 결정
- [x] 본인 알림만 조회/수정되는 테스트 추가
- [x] 안읽음 카운트 테스트 추가
- [x] 단건/전체 읽음 테스트 추가
- [x] settings 기본값 merge 및 잘못된 타입 검증 보강
- [x] 인박스 HTTP + Spring Security + DB 통합 테스트 추가
- [x] targeted test 통과
- [x] `clean build` 통과

## 검증 예정

```powershell
.\gradlew.bat test --tests "*NotificationInbox*"
.\gradlew.bat test --tests "*NotificationRepositoryTest"
.\gradlew.bat test --tests "*ModuleStructureTest"
.\gradlew.bat clean build
```

검증 결과(2026-06-09):

- `.\gradlew.bat test --tests "*NotificationInbox*" --tests "*NotificationSettings*" --tests "*NotificationRepositoryTest" --tests "*UserServiceTest"`: PASS
- `.\gradlew.bat test --tests "*ModuleStructureTest"`: PASS
- `.\gradlew.bat test --tests "*NotificationSettingsServiceTest" --tests "*NotificationInboxIntegrationTest"`: PASS
- `.\gradlew.bat spotbugsMain`: PASS
- `.\gradlew.bat clean build`: PASS

> Windows Embedded Kafka 종료 중 임시 디렉터리 삭제 실패 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.
