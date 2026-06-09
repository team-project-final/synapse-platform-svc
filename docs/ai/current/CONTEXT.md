# CONTEXT - PLAT-066: Notification 인박스 조회 API

## 배경

루트 `docs/BACKEND_GAP_platform.md`의 A-3 항목은 platform-svc에 알림 인박스 조회 API가 없다고 정리한다.
프론트 `notifications/notification_screens.dart`는 다음 동작을 기대한다.

- 알림 목록 조회
- 단건 읽음 처리
- 전체 읽음 처리
- 안읽음 카운트 조회
- 알림 설정 조회/저장

현재 백엔드는 다음만 제공한다.

- `/api/v1/notifications/devices` 디바이스 토큰 등록/해제
- Kafka `notification.send` 이벤트 소비
- FCM/EMAIL 채널 발송
- `notifications` 테이블에 발송 상태 저장

## 현재 코드 상태

기존 테이블 `notifications`는 `V31__create_notifications.sql`에서 생성된다.

핵심 컬럼:

- `id UUID`
- `event_id UUID`
- `user_id UUID`
- `tenant_id UUID`
- `notification_type VARCHAR(100)`
- `channel VARCHAR(20)` - `FCM`, `EMAIL`
- `title VARCHAR(500)`
- `body TEXT`
- `status VARCHAR(20)` - `PENDING`, `SENT`, `FAILED`
- `attempts INT`
- `error_message TEXT`
- `sent_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ`

현재 제약:

- `uq_notifications_event_channel UNIQUE (event_id, channel)`
- 같은 이벤트가 FCM과 EMAIL로 각각 저장될 수 있다.
- `read_at` 또는 읽음 상태 컬럼이 없다.
- `NotificationRepository`에는 발송 중복 확인과 이메일 일일 제한 count만 있다.
- `NotificationService`는 발송/상태 갱신 책임만 가진다.
- `DeviceTokenController`만 있고 인박스 controller는 없다.

## 설계 방향

인박스 노출 대상은 우선 `channel = FCM`, `status = SENT`로 제한한다.

이유:

- 현재 저장 단위가 이벤트별 1건이 아니라 `event + channel`별 1건이다.
- EMAIL 행까지 목록에 포함하면 같은 이벤트가 프론트 알림센터에 중복 표시될 수 있다.
- EMAIL 행은 발송 이력과 감사 성격으로 남겨둔다.

읽음 상태는 `notifications.read_at`으로 처리한다.

- `NULL`: 안읽음
- 값 있음: 읽음
- 단건 읽음과 전체 읽음은 idempotent하게 처리한다.

## 모듈 경계

`notification` 모듈은 `user` 모듈 내부 repository/entity에 직접 접근하지 않는다.

알림 인박스 본체는 `notifications` 테이블만 사용하므로 notification 모듈 내부에서 처리 가능하다.

알림 설정은 이미 `user_settings.notification_prefs` 필드가 있으므로 해당 필드를 정본으로 사용한다.
notification 모듈은 user 내부 repository/entity에 직접 접근하지 않고, `UserApi`의 notification prefs 조회/저장 계약만 호출한다.

권장 순서:

1. PLAT-066에서 인박스 목록/읽음/count를 완성한다.
2. settings API는 `GET`/`PUT /api/v1/notifications/settings`로 함께 제공한다.
3. request/response는 JSON object를 주고받되 기본값과 merge해 `categories`, `quietHours` 구조를 유지한다.
4. 잘못된 타입의 settings payload는 400으로 거절한다.

## 인증/보안 기준

- `/api/v1/notifications/**`는 기존 `NotificationSecurityConfig`에서 인증 필요로 보호된다.
- 현재 사용자 ID는 JWT subject 기반 `Authentication.getName()`을 UUID로 파싱한다.
- 본인 `user_id`에 해당하는 알림만 조회/수정한다.
- 타인 알림 ID 접근은 403 대신 404로 처리해 존재 여부를 숨긴다.

## 테스트 포인트

- 목록은 본인 알림만 반환한다.
- 목록은 `FCM`, `SENT`만 반환한다.
- 목록은 최신순으로 반환한다.
- unread count는 본인, FCM, SENT, `read_at IS NULL`만 센다.
- 단건 읽음은 본인 알림만 변경한다.
- 단건 읽음은 이미 읽은 상태에서도 성공한다.
- 전체 읽음은 본인 unread 알림만 변경하고 변경 개수를 반환한다.
- 설정 API를 포함하면 `UserApi` 경유 여부를 modulith 테스트로 확인한다.
- 인박스 HTTP API는 실제 Spring Security, Flyway migration, PostgreSQL DB를 사용하는 통합 테스트로 확인한다.

## 주의 사항

- 새 migration 이름은 기존 순번식 `V33__...`가 아니라 timestamp 형식을 사용한다.
- `TASK_platform.md`는 최초 개발 목록이므로 수정하지 않는다.
- profile/env 설정은 건드리지 않는다.
- FCM/SES provider mock이나 발송 테스트를 이번 인박스 작업의 성공 조건으로 삼지 않는다.
