# HANDOFF - PLAT-071: Admin 대시보드 분석 API

## 상태

- Branch: `feature/PLAT-071-admin-analytics`
- Base: `dev`
- Current task: PLAT-071 구현 완료
- Implementation: DONE
- Verification: PASS

## 구현 완료 내용

- `GET /api/v1/admin/analytics/summary` 추가.
- `AdminAnalyticsController`, `AdminAnalyticsService`, `AdminAnalyticsSummaryResponse` 추가.
- admin 모듈은 각 도메인의 named interface API만 사용하도록 유지.
- 사용자 지표:
  - total, active, suspended, deleted
  - newToday
  - DAU/MAU 후보 지표
  - source: `USERS_LAST_LOGIN_AT`
  - total/deleted는 soft-delete 행 포함
  - newToday/DAU는 `generatedAt` 날짜 00:00 이후 기준
- 테넌트 지표:
  - total, active, suspended
  - plan별 count
- platform-local usage:
  - 오늘 발송/실패 알림
  - 활성 구독 수
  - 오늘 결제 성공 수/금액
  - 오늘 audit activity 수
  - `*.today` 지표는 최근 24시간이 아니라 `generatedAt` 날짜 00:00 이후 기준
- cross-service usage:
  - AI token, storage는 fake value 없이 `NOT_CONNECTED`
- pending item:
  - GDPR data request는 `NOT_IMPLEMENTED`
  - report는 `NOT_CONNECTED`
- recent activity:
  - `audit_logs.created_at DESC` 기준 최근 5개

## 추가된 공개 API

- `user::api`
  - `UserAnalyticsApi`
  - `UserAnalyticsSnapshot`
- `auth::tenant-api`
  - `TenantAnalyticsApi`
  - `TenantAnalyticsSnapshot`
- `billing::api`
  - `BillingAnalyticsApi`
  - `BillingAnalyticsSnapshot`
- `notification::api`
  - `NotificationAnalyticsApi`
  - `NotificationAnalyticsSnapshot`
- `audit::api`
  - `AuditAnalyticsApi`
  - `AuditAnalyticsSnapshot`
  - `RecentAuditActivity`

## 검증 결과

```powershell
.\gradlew.bat test --tests "*AdminAnalytics*" --tests "*UserAnalyticsServiceTest" --tests "*TenantAnalyticsServiceTest" --tests "*BillingAnalyticsServiceTest" --tests "*NotificationAnalyticsServiceTest" --tests "*AuditAnalyticsServiceTest" --tests "*AdminSecurityIntegrationTest"
.\gradlew.bat test --tests "*UserRepositoryAnalyticsTest" --tests "*TenantRepositoryAnalyticsTest" --tests "*BillingRepositoryTest" --tests "*NotificationRepositoryTest" --tests "*AuditLogPostgresSchemaTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat spotbugsMain
.\gradlew.bat clean build
```

결과:

- PASS
- Windows Kafka temp directory deletion warning은 shutdown 시점 경고이며 Gradle 결과는 `BUILD SUCCESSFUL`.

## 다음 단계

1. 작업 내용 리뷰.
2. 필요 시 리뷰 피드백 반영.
3. PR 준비.
4. 후속 작업 후보:
   - PLAT-072 Admin 시스템 설정/피처 플래그 API
   - PLAT-073 Admin GDPR/data request API

## 주의사항

- `TASK_platform.md` 수정하지 않음.
- env/profile/gitops/shared 수정하지 않음.
- frontend 수정은 이번 작업 범위가 아님.
- DAU/MAU는 정식 analytics event가 아니라 `users.last_login_at` 기준 후보 지표.
- AI token/storage 등 타 서비스 정본이 필요한 값은 `NOT_CONNECTED`로 내려준다.
