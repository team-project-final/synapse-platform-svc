# PLAT-091 Handoff

## Status
조사 및 검증 완료. 코드/마이그레이션 수정은 불필요하다.

## Branch
- Base: `dev`
- Working branch: `fix/PLAT-091-oauth-provider-column`

## Archived
PLAT-072 current 문서는 아래 경로에 보관했다.

- `docs/ai/archive/20260610-plat-072-completed/TASK.md`
- `docs/ai/archive/20260610-plat-072-completed/CONTEXT.md`
- `docs/ai/archive/20260610-plat-072-completed/HANDOFF.md`

## Current Task
#91의 OAuth provider 컬럼 rename migration 필요 여부를 확인했다.

핵심 질문:
- 공식 schema는 `provider_id`가 맞는가?
- entity는 이미 `provider_id`에 맞춰져 있는가?
- `provider_user_id` rename migration은 불필요한 로컬 파일인가?
- V28 중복으로 인한 Flyway 충돌 가능성은 repo에 남아 있는가?

## Finding
현재 `dev` 기준으로는 `provider_id` 유지가 맞다.

근거:
- `OAuthIdentity.providerUserId`에 `@Column(name = "provider_id")`가 있다.
- `V3__init_users_and_auth.sql`도 `provider_id`를 생성한다.
- `OAuthIdentitySchemaTest`가 `provider_user_id` 미사용을 검증한다.
- repo에는 `V28__rename_oauth_provider_id_column.sql`이 없다.
- OAuth/schema 테스트, 이슈 언급 DB 통합 테스트, `clean build`가 모두 통과했다.

## Next Steps
1. 작업 내용 리뷰.
2. #91에 조사 결과 코멘트 작성.
3. 이슈 close 여부 결정.
4. 문서 변경을 남길지, 이슈 코멘트만으로 정리할지 결정.

## Test Commands
```powershell
.\gradlew.bat test --tests "*OAuthIdentitySchemaTest" --tests "*OAuth2LoginIntegrationTest" --tests "*OAuthConnectionControllerTest" --tests "*OAuthConnectionServiceTest"
.\gradlew.bat test --tests "*AuditLogPostgresSchemaTest" --tests "*RefreshTokenServiceTest" --tests "*BillingRepositoryTest" --tests "*AuthBillingE2ETest" --tests "*DeviceTokenIntegrationTest"
.\gradlew.bat clean build
```

## PR Direction
코드 변경은 없다. PR을 올린다면 문서/HISTORY 정리 PR이다.

PR 없이 정리한다면 #91에 아래 요지를 남기고 닫으면 된다.
- `OAuthIdentity.providerUserId`는 `provider_id`에 명시 매핑되어 있다.
- `V3` migration과 schema test도 `provider_id` 기준이다.
- tracked repo에는 rename migration이 없고 V28 중복도 없다.
- 검증 테스트와 `clean build`가 통과했다.

## Out of Scope Reminder
- 다른 repo 수정 금지
- 실행 profile/env 수정 금지
- `TASK_platform.md` 수정 금지
- #86, #87, #62는 이번 작업 범위 아님
