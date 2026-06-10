# PLAT-091 Context

## Current State
- #84 OpenAPI/SpringDoc PR #92는 `dev`에 merge 완료됐다.
- #84 이슈는 닫혔다.
- 현재 작업 브랜치: `fix/PLAT-091-oauth-provider-column`
- 기준 브랜치: `dev`
- #91 조사는 완료됐고, 코드/마이그레이션 수정은 불필요하다고 판단했다.

## Issue Summary
#91은 팀장님 로컬 checkout에서 발견된 untracked migration 파일 때문에 등록된 이슈다.

발견된 파일명:

```text
src/main/resources/db/migration/V28__rename_oauth_provider_id_column.sql
```

파일 의도:

```sql
ALTER TABLE oauth_identities RENAME COLUMN provider_id TO provider_user_id;
DROP INDEX IF EXISTS uq_oauth_provider_user;
CREATE UNIQUE INDEX uq_oauth_provider_user ON oauth_identities(provider, provider_user_id);
```

이슈의 핵심은 두 가지다.

1. `V28__allow_multiple_refresh_tokens.sql`이 이미 있으므로 rename 파일도 V28이면 Flyway version conflict가 발생한다.
2. 공식 schema가 `provider_id`인데 entity가 `provider_user_id`를 기대한다면 OAuth 경로가 깨질 수 있다.

## Verified Local Evidence

현재 `dev` 기준 repo에서 확인한 내용:

| 항목 | 확인 결과 |
|---|---|
| OAuth entity | `OAuthIdentity.providerUserId`는 `@Column(name = "provider_id")` |
| 초기 DDL | `V3__init_users_and_auth.sql`이 `provider_id` 생성 |
| unique index | `uq_oauth_provider_user ON oauth_identities(provider, provider_id)` |
| schema test | `OAuthIdentitySchemaTest`가 `provider_id` 사용, `provider_user_id` 미사용을 검증 |
| tracked rename migration | 없음 |
| tracked V28 | `V28__allow_multiple_refresh_tokens.sql`만 존재 |

초기 판단:
- 현재 공식 코드 기준으로는 `provider_id` 유지가 의도된 상태일 가능성이 높다.
- 따라서 rename migration을 바로 추가하면 기존 테스트 의도와 충돌할 수 있다.
- 먼저 테스트와 git tracked 파일 기준으로 "실제 schema gap 없음"을 확정하는 것이 우선이다.

최종 판단:
- 현재 공식 코드 기준 `provider_id` 유지가 맞다.
- `provider_user_id` rename migration은 추가하지 않는다.
- repo에는 V28 중복이 없고, DB 통합 테스트와 전체 빌드가 통과했다.

## Relevant Files

Entity:
- `src/main/java/com/synapse/platform/auth/entity/OAuthIdentity.java`

Repository:
- `src/main/java/com/synapse/platform/auth/repository/OAuthIdentityRepository.java`

Migrations:
- `src/main/resources/db/migration/V3__init_users_and_auth.sql`
- `src/main/resources/db/migration/V20__add_oauth_identity_access_token.sql`
- `src/main/resources/db/migration/V28__allow_multiple_refresh_tokens.sql`

Tests:
- `src/test/java/com/synapse/platform/auth/entity/OAuthIdentitySchemaTest.java`
- `src/test/java/com/synapse/platform/auth/controller/OAuth2LoginIntegrationTest.java`
- `src/test/java/com/synapse/platform/auth/controller/OAuthConnectionControllerTest.java`
- `src/test/java/com/synapse/platform/auth/service/OAuthConnectionServiceTest.java`
- `src/test/java/com/synapse/platform/audit/AuditLogPostgresSchemaTest.java`
- `src/test/java/com/synapse/platform/auth/service/RefreshTokenServiceTest.java`
- `src/test/java/com/synapse/platform/billing/BillingRepositoryTest.java`
- `src/test/java/com/synapse/platform/e2e/AuthBillingE2ETest.java`
- `src/test/java/com/synapse/platform/notification/DeviceTokenIntegrationTest.java`

## Risk Notes
- Flyway version conflict는 service boot와 DB integration test를 한 번에 막는 고위험 이슈다.
- 반대로 불필요한 rename migration을 추가하면 현재 entity/test 의도와 충돌하고 OAuth 기존 DB와도 충돌할 수 있다.
- 공식 repo에 없는 untracked 파일은 그대로 따라가지 않는다. 현재 repo와 팀장님 관리 문서/이슈 근거를 기준으로 판단한다.
- 운영 DB에 이미 수동 rename이 적용된 경우는 repo migration만으로 단정하면 안 된다. 해당 경우는 운영 DB 상태 확인이 별도 필요하다.

## Verification
통과한 검증:

```powershell
.\gradlew.bat test --rerun-tasks --tests "*OAuthIdentitySchemaTest" --tests "*OAuth2LoginIntegrationTest" --tests "*OAuthConnectionControllerTest" --tests "*OAuthConnectionServiceTest"
.\gradlew.bat test --rerun-tasks --tests "*AuditLogPostgresSchemaTest" --tests "*RefreshTokenServiceTest" --tests "*BillingRepositoryTest" --tests "*AuthBillingE2ETest" --tests "*DeviceTokenIntegrationTest"
.\gradlew.bat clean build
```

`clean build` 중 Windows Kafka 테스트 임시파일 삭제 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.

## Resolution Direction
1. code migration은 추가하지 않는다.
2. HISTORY와 작업 문서에 조사 완료 근거를 남긴다.
3. #91에는 "tracked repo 기준 schema gap 없음, V28 중복 없음, rename migration 불필요"로 코멘트를 남긴 뒤 close하면 된다.

## Do Not Touch
- shared/gitops/engagement/gateway/frontend repo
- `.env`
- profile 설정
- 실행 포트 설정
- `TASK_platform.md`
- 팀장님 로컬의 untracked 파일
