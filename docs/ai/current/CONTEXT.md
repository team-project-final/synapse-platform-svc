# PLAT-097 Context

## Current State
- Target task: PLAT-097
- Working branch: `chore/PLAT-097-actions-node24-upgrade`
- Base: `origin/dev`
- Target PR branch: `dev`
- 작업 대상 repo: `synapse-platform-svc`
- 현재 상태: 로컬 구현 완료, PR checks 검증 전

## Background
platform 이슈 #97은 GitHub Actions에서 Node.js 20 런타임 기반 액션이 deprecation 예정이라는 CI 정비 이슈다.

이슈 본문 기준 제거 예정일은 2026-09-16이며, 현재 platform-svc 워크플로에는 구 메이저 액션이 남아 있다. 같은 유형의 정비는 synapse-shared PR #56에서 선례가 있다고 기록되어 있다.

## Issue Summary
이슈: platform 이슈 #97 `ci: GitHub Actions Node 20 deprecation 업그레이드`

요구사항:
- 공식 액션 메이저 버전 업그레이드
- 기존 workflow 동작 유지
- Node 20 deprecation 경고 제거

권장 타깃:
- `actions/checkout@v4` -> `actions/checkout@v6`
- `actions/setup-java@v4` -> `actions/setup-java@v5`
- `actions/setup-node@v4` -> `actions/setup-node@v6`

## Files In Scope

### `.github/workflows/ci-java.yml`
현재 구조:
- `build` job
  - checkout
  - Java 21 setup
  - `./gradlew clean build --no-daemon`
  - Modulith verify
- `dev-smoke` job
  - checkout
  - Java 21 setup
  - Docker Hub login
  - docker compose dev services
  - `bootRun --spring.profiles.active=dev`
  - actuator health check

현재 액션:
- `actions/checkout@v4`
- `actions/setup-java@v4`

### `.github/workflows/parse-workflow.yml`
현재 구조:
- platform repo checkout
- `team-project-final/workflow-dashboard` checkout
- Node 22 setup
- workflow/prd parser 실행
- dashboard data push

현재 액션:
- `actions/checkout@v4`
- `actions/setup-node@v4`

## Files Out of Scope
- `.github/workflows/deploy.yml`
- `.github/workflows/flyway-guard.yml`
- `.github/workflows/mirror.yml`
- Java source/test files
- Gradle files
- Docker files
- README issue list
- 다른 레포

## Constraints
- `main`/`dev` 직접 commit 금지.
- PR target은 `dev`.
- branch prefix는 git 규칙상 CI/설정 작업에 맞춰 `chore/PLAT-097-...`를 사용한다.
- PR title은 규칙대로 `<type>(<scope>): <설명> (#이슈번호)` 형식을 사용한다.
- PR body는 한글이 깨지지 않도록 UTF-8 body file로 작성한다.

## Risk Review

### Low Risk
- 액션 버전만 변경하며 애플리케이션 코드에는 영향이 없다.
- 기존 `with:` 파라미터를 유지하면 workflow 의미가 바뀌지 않는다.

### Watch Points
- `actions/checkout@v6`에서 dashboard repo checkout의 `repository`, `token`, `path` 설정이 그대로 동작하는지 PR check로 확인해야 한다.
- `actions/setup-java@v5`에서 `cache: gradle` 동작이 유지되는지 확인해야 한다.
- `actions/setup-node@v6`에서 `node-version: 22` 설정이 유지되는지 확인해야 한다.

## Verification Strategy
로컬에서는 workflow 런타임 자체를 완전히 재현하기 어렵다. 따라서 로컬 검증은 diff 품질 확인 중심으로 두고, 최종 검증은 PR checks에서 한다.

로컬:

```powershell
git diff --check
```

PR checks:
- Java CI build
- Java CI dev-smoke
- workflow-dashboard parser

## Related Note
README에는 예전 열린 이슈 목록이 남아 있으나, 이번 PLAT-097의 직접 범위는 GitHub Actions deprecation 처리다. README 정리는 별도 문서/작업으로 분리하는 것이 안전하다.
