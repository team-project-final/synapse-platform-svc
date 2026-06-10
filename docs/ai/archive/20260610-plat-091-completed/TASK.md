# PLAT-091 OAuth provider 컬럼/Flyway 마이그레이션 정합 확인

## Task ID
PLAT-091

## Title
OAuth provider 컬럼 rename 마이그레이션 필요 여부 확인 및 Flyway 버전 충돌 방지

## Owner
platform

## Status
DONE

## Priority
P1

## Issue
https://github.com/team-project-final/synapse-platform-svc/issues/91

## Step Goal
`oauth_identities.provider_id`와 `OAuthIdentity.providerUserId` 매핑이 현재 `dev` 기준으로 정합한지 확인하고, 미커밋 `V28__rename_oauth_provider_id_column.sql`이 실제로 필요한 변경인지 판단한다.

필요하면 V28 중복 없이 신규 마이그레이션으로 처리하고, 불필요하면 코드 변경 없이 근거를 남겨 이슈를 닫는다.

## Done When
- [x] 현재 `dev` 기준 Flyway migration에 V28 중복이 없는지 확인한다.
- [x] `OAuthIdentity.providerUserId`의 실제 DB 컬럼 매핑을 확인한다.
- [x] `V3__init_users_and_auth.sql`의 `oauth_identities` 스키마와 unique index 컬럼을 확인한다.
- [x] `provider_id` 유지 또는 `provider_user_id` rename 중 하나를 근거와 함께 결정한다.
- [x] rename이 필요한 경우 V28이 아니라 다음 안전한 버전을 써야 함을 문서화한다.
- [x] rename이 불필요함을 확인하고 migration 추가 없이 관련 테스트 결과를 정리한다.
- [x] OAuth 컬럼 정합 테스트가 통과한다.
- [x] 이슈에 언급된 DB 통합 테스트 범위가 통과한다.
- [x] `./gradlew.bat clean build`가 통과한다.
- [x] `docs/project-management/history/HISTORY_platform.md`에 작업 이력을 남긴다.

## Scope

### In Scope
- platform repo 내부 Flyway migration 목록 확인
- `OAuthIdentity` entity와 `oauth_identities` DDL 정합 확인
- OAuth provider 컬럼명 결정
- 필요한 경우 신규 migration 추가
- 필요한 경우 schema/entity 테스트 보강
- #91 이슈 코멘트 또는 close 근거 정리
- PLAT-072 current 문서 archive 보관

### Out of Scope
- shared/gitops/engagement/gateway/frontend 수정
- 팀장님 로컬 checkout의 untracked 파일 직접 수정
- 운영 DB 수동 변경
- Spring profile, `.env`, 포트 설정 변경
- OAuth provider 정책 변경
- 관리자 role, audit DLT, W5 live E2E 후속 이슈 처리
- `TASK_platform.md` 항목 추가

## Initial Findings
- 현재 `dev` 기준 `OAuthIdentity.providerUserId`는 `@Column(name = "provider_id")`로 명시 매핑되어 있다.
- `V3__init_users_and_auth.sql`은 `oauth_identities.provider_id` 컬럼과 `uq_oauth_provider_user(provider, provider_id)` 인덱스를 생성한다.
- `OAuthIdentitySchemaTest`는 `provider_id` 사용과 `provider_user_id` 미사용을 명시적으로 검증한다.
- 현재 repo에는 `V28__allow_multiple_refresh_tokens.sql`이 존재하고, `V28__rename_oauth_provider_id_column.sql`은 tracked 파일로 존재하지 않는다.
- 현재 migration은 숫자 버전 V32 이후 timestamp 버전 migration이 함께 존재한다. 신규 migration이 필요하면 기존 팀 규칙과 Flyway ordering 영향을 함께 확인해야 한다.

## Result
- `provider_id` 유지가 현재 공식 repo 기준 정본이다.
- `OAuthIdentity.providerUserId`는 `@Column(name = "provider_id")`로 DB 컬럼에 명시 매핑되어 있다.
- `V3__init_users_and_auth.sql`과 `OAuthIdentitySchemaTest`도 `provider_id`를 기준으로 정합하다.
- `V28__rename_oauth_provider_id_column.sql`은 tracked 파일이 아니며, repo 기준 Flyway V28 중복은 없다.
- 따라서 `provider_user_id` rename migration은 추가하지 않는다.
- #91은 팀장님 로컬의 untracked migration 파일을 제거하거나 커밋하지 않는 방식으로 정리하면 된다.

## Decision Criteria

### Option A: provider_id 유지, 코드 변경 없음
선택 조건:
- entity, migration, repository query가 모두 `provider_id` 기준으로 정합한다.
- OAuth 관련 테스트가 통과한다.
- V28 rename 파일이 repo에 없고, 공식 schema gap이 없다고 확인된다.

처리:
- migration을 추가하지 않는다.
- #91에 근거를 남기고 이슈를 닫는다.
- HISTORY에 조사 완료 기록을 남긴다.

### Option B: provider_user_id rename migration 추가
선택 조건:
- 현재 공식 schema와 entity 매핑이 실제로 불일치한다.
- 또는 팀 합의로 DB 컬럼명을 Java 필드명에 맞춰 `provider_user_id`로 변경하기로 결정한다.

처리:
- V28 중복은 금지한다.
- 안전한 신규 migration 버전을 사용한다.
- entity `@Column`, repository, schema test, OAuth 통합 테스트를 함께 수정한다.
- 기존 `uq_oauth_provider_user` 인덱스를 새 컬럼 기준으로 재생성한다.

## Test Plan

1. 정합 확인
   - migration 목록에서 V28 중복 여부 확인
   - `OAuthIdentity` 컬럼 매핑 확인
   - `V3__init_users_and_auth.sql` DDL 확인

2. 단일/통합 테스트
   ```powershell
   .\gradlew.bat test --tests "*OAuthIdentitySchemaTest" --tests "*OAuth2LoginIntegrationTest" --tests "*OAuthConnectionControllerTest" --tests "*OAuthConnectionServiceTest"
   ```

3. 이슈에 언급된 DB 영향 테스트
   ```powershell
   .\gradlew.bat test --tests "*AuditLogPostgresSchemaTest" --tests "*RefreshTokenServiceTest" --tests "*BillingRepositoryTest" --tests "*AuthBillingE2ETest" --tests "*DeviceTokenIntegrationTest"
   ```

4. 전체 검증
   ```powershell
   .\gradlew.bat clean build
   ```

## Working Notes
- 현재 브랜치: `fix/PLAT-091-oauth-provider-column`
- 기준 브랜치: `dev`
- PLAT-072 current 문서는 `docs/ai/archive/20260610-plat-072-completed/`에 보관했다.
- `TASK_platform.md`는 최초 개발 목록 문서이므로 수정하지 않는다.
- 검증 통과:
  - `.\gradlew.bat test --rerun-tasks --tests "*OAuthIdentitySchemaTest" --tests "*OAuth2LoginIntegrationTest" --tests "*OAuthConnectionControllerTest" --tests "*OAuthConnectionServiceTest"`
  - `.\gradlew.bat test --rerun-tasks --tests "*AuditLogPostgresSchemaTest" --tests "*RefreshTokenServiceTest" --tests "*BillingRepositoryTest" --tests "*AuthBillingE2ETest" --tests "*DeviceTokenIntegrationTest"`
  - `.\gradlew.bat clean build`
