# HANDOFF - PLAT-066: Notification 인박스 조회 API

## 한줄 요약

루트 `docs/BACKEND_GAP_platform.md` A-3을 처리한다. 기존 notification 발송 이력 테이블에 읽음 상태를 추가하고, 프론트 알림센터가 사용할 목록/count/read API를 만든다.

## 작업 위치

- Repo: `synapse-platform-svc`
- Branch: `feature/PLAT-066-notification-inbox`
- Base: `dev`
- 작업문서: `docs/ai/current`

## 구현 순서

1. Flyway migration 추가
   - 파일명: `VyyyyMMddHHmmss__add_notification_read_state.sql`
   - `notifications.read_at TIMESTAMPTZ NULL`
   - inbox 조회용 index 추가

2. Entity 보강
   - `Notification.readAt`
   - `markRead(OffsetDateTime now)`
   - `isRead()`

3. Repository query 추가
   - 본인 알림 목록: `userId`, `FCM`, `SENT`, pageable, `createdAt DESC`
   - unread count: `userId`, `FCM`, `SENT`, `readAt IS NULL`
   - 단건 조회: `id`, `userId`, `FCM`, `SENT`
   - 전체 읽음 update 또는 service loop

4. Service 추가
   - 발송 책임의 `NotificationService`와 분리 권장
   - 후보명: `NotificationInboxService`
   - 타인 알림 접근은 404 처리

5. Controller/DTO 추가
   - `GET /api/v1/notifications`
   - `GET /api/v1/notifications/unread-count`
   - `PUT /api/v1/notifications/{id}/read`
   - `POST /api/v1/notifications/read-all`
   - 현재 사용자 UUID는 `Authentication.getName()`에서 파싱

6. Settings API
   - 루트 문서 A-3의 settings API를 같은 작업에 포함한다.
   - 저장소는 기존 `user_settings.notification_prefs`.
   - notification 모듈은 `UserApi`의 prefs 조회/저장 메서드만 호출한다.
   - 요청/응답은 JSON object를 주고받되 기본 `categories`, `quietHours` 구조와 merge한다.
   - 잘못된 타입의 settings payload는 400으로 거절한다.

## API 응답 후보

목록:

```json
{
  "items": [
    {
      "id": "uuid",
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

Unread count:

```json
{
  "count": 3
}
```

Read all:

```json
{
  "updatedCount": 3
}
```

## 검증 명령

```powershell
.\gradlew.bat test --tests "*NotificationInbox*"
.\gradlew.bat test --tests "*NotificationRepositoryTest"
.\gradlew.bat test --tests "*ModuleStructureTest"
.\gradlew.bat test --tests "*NotificationInboxIntegrationTest"
.\gradlew.bat clean build
```

## 금지/주의

- `TASK_platform.md` 수정 금지
- env/profile 수정 금지
- 다른 서비스 DB 기준 변경 금지
- EMAIL 행을 사용자 인박스에 바로 노출하지 말 것
- PR 본문 작성 시 UTF-8 body file 사용
