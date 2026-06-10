# TASK - PLAT-071: Admin 대시보드 분석 API

> 출처: 루트 `docs/BACKEND_GAP_platform.md` A-6. 프론트 `admin/admin_dashboard_screen.dart`가 DAU/MAU, 시스템 사용량, 긴급 처리 항목, 최근 활동을 기대하지만, platform-svc는 현재 관리자 사용자/테넌트/감사로그 CRUD만 제공한다.

## Task Metadata

| 필드 | 내용 |
|---|---|
| Task ID | `PLAT-071` |
| Title | Admin 대시보드 분석 API |
| Owner | platform (김해준) |
| Status | `DONE` |
| Priority | `P2` |
| Step Goal | 관리자가 관리자 대시보드에서 platform-svc가 보유한 사용자/테넌트/감사로그/알림 기준 운영 요약을 조회할 수 있다. |
| Done When | 아래 `Done When` 체크리스트 기준 |
| Scope | 아래 `Scope` 기준 |
| Dependencies | `BACKEND_GAP_platform.md` A-6, `users`, `tenants`, `audit_logs`, `notifications`, `subscriptions`, 기존 admin security |
| Due Date | 2026-06-12 |

## Step Goal

관리자가 관리자 대시보드에서 platform-svc가 보유한 사용자/테넌트/감사로그/알림 기준 운영 요약을 조회할 수 있다.

## Done When

- [x] 기존 PLAT-070 current 문서를 archive한다.
- [x] PLAT-071 작업 브랜치를 `dev`에서 생성한다.
- [x] PLAT-071 작업문서를 작성한다.
- [x] `GET /api/v1/admin/analytics/summary` endpoint를 추가한다.
- [x] 응답에 사용자 총계, 상태별 카운트, 신규 가입 수, DAU/MAU 후보 지표를 포함한다.
- [x] 응답에 테넌트 총계, 상태별 카운트, 플랜별 카운트를 포함한다.
- [x] 응답에 platform-local 시스템 사용량 요약을 포함한다.
- [x] platform-svc 단독으로 산출할 수 없는 AI token/storage 등은 mock 값 대신 `NOT_CONNECTED` source 상태로 반환한다.
- [x] 응답에 최근 활동을 `audit_logs` 기준으로 포함한다.
- [x] `/api/v1/admin/analytics/**`는 관리자 권한만 접근할 수 있다.
- [x] controller/service/repository 테스트가 통과한다.
- [x] admin security integration test가 통과한다.
- [x] `PlatformModuleStructureTest`와 `clean build`가 통과한다.
- [x] `TASK_platform.md`, env/profile, gitops/shared 프로젝트는 수정하지 않는다.

## Scope

### In Scope

- Admin analytics summary 조회 API
- 사용자/테넌트/구독/알림/감사로그 기준 platform-local 집계
- DAU/MAU 후보 지표
  - 기준: `users.last_login_at`
  - DAU: `generatedAt` 날짜의 00:00 이후 로그인 사용자 수
  - MAU: 최근 30일 로그인 사용자 수
- 최근 활동 목록
  - 기준: `audit_logs.created_at DESC`
  - action, userId, resourceType, resourceId, createdAt 노출
- 시스템 사용량 카드
  - platform-local: notification sent count, active subscription count 등 실제 산출 가능한 값
  - cross-service: AI token, storage 등은 `NOT_CONNECTED`
- DTO/service/repository/controller 테스트
- 관리자 권한 보호 테스트
- current 작업 문서 및 HISTORY 로그 갱신

### Out of Scope

- Admin 시스템 설정/피처 플래그 저장 API
- Admin GDPR/data request API
- 신고/모더레이션, 그룹, 게이미피케이션, 콘텐츠 관리 API
- learning/knowledge/engagement 정본 데이터 직접 조회
- gateway 라우팅 변경
- frontend 코드 수정
- `TASK_platform.md` 수정
- env/profile/gitops/shared 수정

## API Contract

### 관리자 대시보드 요약

`GET /api/v1/admin/analytics/summary`

인증:

- `ROLE_ADMIN` 필요
- 기존 `/api/v1/admin/**` 보안 정책을 따른다.

응답 후보:

```json
{
  "generatedAt": "2026-06-10T12:00:00+09:00",
  "users": {
    "total": 120,
    "active": 116,
    "suspended": 3,
    "deleted": 1,
    "newToday": 5,
    "dau": 42,
    "mau": 98,
    "activitySource": "USERS_LAST_LOGIN_AT"
  },
  "tenants": {
    "total": 40,
    "active": 38,
    "suspended": 2,
    "plans": {
      "free": 30,
      "pro": 8,
      "team": 2
    }
  },
  "usage": [
    {
      "key": "notifications.sent.today",
      "label": "오늘 발송 알림",
      "value": 12,
      "unit": "count",
      "status": "OK",
      "source": "notifications"
    },
    {
      "key": "ai.tokens.monthly",
      "label": "AI 토큰",
      "value": null,
      "unit": "tokens",
      "status": "NOT_CONNECTED",
      "source": "learning-ai"
    }
  ],
  "pendingItems": [
    {
      "key": "data-requests",
      "label": "GDPR 요청",
      "count": null,
      "severity": "INFO",
      "status": "NOT_IMPLEMENTED"
    }
  ],
  "recentActivities": [
    {
      "id": "f3ee2e5a-1c76-4a2b-b7f1-6fd7b030ce1e",
      "action": "USER_LOGIN",
      "userId": "8ad7f1f4-6d2a-46dd-87d2-2a1f9e040e5d",
      "resourceType": "USER",
      "resourceId": "8ad7f1f4-6d2a-46dd-87d2-2a1f9e040e5d",
      "createdAt": "2026-06-10T11:50:00+09:00"
    }
  ]
}
```

## Design Notes

- 이번 작업은 `admin dashboard`의 mock KPI를 제거하기 위한 읽기 전용 API부터 처리한다.
- 프론트가 이미 사용자/테넌트 total은 목록 API의 `totalElements`로 가져오고 있으나, DAU/MAU·사용량·최근 활동은 별도 summary API가 필요하다.
- DAU/MAU는 analytics 전용 이벤트 테이블이 없으므로 `users.last_login_at` 기준 후보 지표로 정의한다.
- `users.total`과 `users.deleted`는 soft-delete 행까지 포함하는 native query를 사용한다. `active`/`suspended`/`newToday`/`dau`는 `@SQLRestriction("deleted_at IS NULL")` 적용 범위의 삭제되지 않은 row 기준이다.
- `*.today` 지표는 최근 24시간이 아니라 `generatedAt` 날짜의 00:00 이후 데이터로 산출한다. 현재 `OffsetDateTime` offset을 유지해 프론트 표시 날짜와 집계 기준을 맞춘다.
- cross-service 정본이 필요한 값은 임의 mock을 반환하지 않는다. `NOT_CONNECTED` 상태를 응답에 포함해 프론트가 "연동 대기" 상태를 표시할 수 있게 한다.
- `admin` 패키지에는 현재 placeholder만 있으므로, analytics API는 `com.synapse.platform.admin` 하위에 controller/service/dto를 두는 방향을 우선 검토한다.
- 기존 admin controller들은 각 도메인 패키지에 흩어져 있다. 이번 API는 여러 도메인 집계이므로 별도 admin application service가 자연스럽다.

## Implementation Checklist

- [x] PLAT-070 current 문서 archive 확인
- [x] PLAT-071 current 작업문서 작성
- [x] admin analytics DTO 추가
- [x] admin analytics controller 추가
- [x] admin analytics service 추가
- [x] user count query 추가
- [x] tenant count query 추가
- [x] notification/audit/subscription 집계 query 추가
- [x] recent audit activities 조회 추가
- [x] cross-service `NOT_CONNECTED` usage item 정의
- [x] controller test 추가
- [x] service/repository test 추가
- [x] admin security integration test 추가 또는 기존 테스트 보강
- [x] Modulith 구조 테스트 확인
- [x] `clean build` 확인
- [x] HISTORY_platform.md 갱신

## Verification Plan

```powershell
.\gradlew.bat test --tests "*AdminAnalytics*"
.\gradlew.bat test --tests "*UserRepositoryAnalyticsTest" --tests "*TenantRepositoryAnalyticsTest" --tests "*BillingRepositoryTest" --tests "*NotificationRepositoryTest" --tests "*AuditLogPostgresSchemaTest"
.\gradlew.bat test --tests "*AdminSecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

## Implementation Result

- `AdminAnalyticsController`
  - `GET /api/v1/admin/analytics/summary`
  - `ROLE_ADMIN` 보호
- `AdminAnalyticsService`
  - user/auth/billing/notification/audit 공개 API를 조합해 summary 응답 생성
  - cross-service 정본이 필요한 AI token/storage는 `NOT_CONNECTED`로 반환
  - GDPR data request와 report pending item은 fake count 없이 `null` count와 상태값으로 반환
- `user::api`
  - `UserAnalyticsApi`
  - `UserAnalyticsSnapshot`
  - total/deleted는 soft-delete 포함 native count 기준
  - newToday/DAU는 `generatedAt` 날짜 00:00 이후, MAU는 최근 30일 `users.last_login_at` 기준
- `auth::tenant-api`
  - `TenantAnalyticsApi`
  - `TenantAnalyticsSnapshot`
  - tenant status/plan count 제공
- `billing::api`
  - `BillingAnalyticsApi`
  - `BillingAnalyticsSnapshot`
  - active subscription, 오늘 00:00 이후 paid payment/revenue count 제공
- `notification::api`
  - `NotificationAnalyticsApi`
  - `NotificationAnalyticsSnapshot`
  - 오늘 00:00 이후 sent/failed notification count 제공
- `audit::api`
  - `AuditAnalyticsApi`
  - `AuditAnalyticsSnapshot`
  - recent audit activities 제공
- `admin/package-info.java`
  - `allowedDependencies`를 named interface 기준으로 명시해 Modulith 경계 유지

## Verification Result

- `.\gradlew.bat test --tests "*AdminAnalytics*" --tests "*UserAnalyticsServiceTest" --tests "*TenantAnalyticsServiceTest" --tests "*BillingAnalyticsServiceTest" --tests "*NotificationAnalyticsServiceTest" --tests "*AuditAnalyticsServiceTest" --tests "*AdminSecurityIntegrationTest"`: PASS
- `.\gradlew.bat test --tests "*PlatformModuleStructureTest"`: PASS
- `.\gradlew.bat spotbugsMain`: PASS
- `.\gradlew.bat clean build`: PASS
- Windows Kafka temp directory deletion warning appears during shutdown, but Gradle result is `BUILD SUCCESSFUL`.

## Known Follow-up

- Admin 시스템 설정/피처 플래그 API는 PLAT-072로 분리한다.
- Admin GDPR/data request API는 PLAT-073으로 분리한다.
- AI token/storage 등 cross-service 사용량 정본은 learning/knowledge 서비스 계약이 확정된 뒤 연결한다.
