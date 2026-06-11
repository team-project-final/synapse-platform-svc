# PLAT-097 GitHub Actions Node 20 deprecation 업그레이드

## Task ID
PLAT-097

## Title
GitHub Actions 액션 메이저 버전 업그레이드

## Owner
platform

## Status
IMPLEMENTED_LOCAL

## Priority
P2

## Branch
`chore/PLAT-097-actions-node24-upgrade`

## Base
`origin/dev`

## Issue
platform 이슈 #97: `ci: GitHub Actions Node 20 deprecation 업그레이드`

## Step Goal
GitHub Actions에서 Node.js 20 런타임 기반 구버전 액션 deprecation 경고가 발생하지 않도록 platform-svc 워크플로의 공식 액션 메이저 버전을 업그레이드한다.

이번 작업은 CI 설정 정리 작업이다. 애플리케이션 코드, Spring profile, env, Docker Compose, 다른 서비스 레포는 수정하지 않는다.

## Done When
- [x] `.github/workflows/ci-java.yml`의 `actions/checkout`이 권장 최신 메이저로 업그레이드된다.
- [x] `.github/workflows/ci-java.yml`의 `actions/setup-java`가 권장 최신 메이저로 업그레이드된다.
- [x] `.github/workflows/parse-workflow.yml`의 `actions/checkout`이 권장 최신 메이저로 업그레이드된다.
- [x] `.github/workflows/parse-workflow.yml`의 `actions/setup-node`가 권장 최신 메이저로 업그레이드된다.
- [x] 기존 `with:` 설정은 유지된다.
- [ ] PR checks에서 기존 Java CI와 workflow parser job이 정상 동작한다.
- [ ] Node 20 deprecation 경고가 사라진다.
- [x] `git diff --check`를 통과한다.

## Scope

### In Scope
- `.github/workflows/ci-java.yml`
- `.github/workflows/parse-workflow.yml`
- GitHub Actions 공식 액션 메이저 버전 업그레이드
- 작업 이력 문서 갱신

### Out of Scope
- Java/Kotlin 애플리케이션 코드 수정
- Gradle 의존성 수정
- Docker/Docker Compose 수정
- `.env` 또는 Spring profile 수정
- shared/gitops/learning/engagement/frontend 등 다른 레포 수정
- GitHub repository settings 변경
- README의 열린 이슈 목록 정리

## Current Evidence

현재 워크플로 액션 사용 현황:

```text
.github/workflows/ci-java.yml
- actions/checkout@v4
- actions/setup-java@v4

.github/workflows/parse-workflow.yml
- actions/checkout@v4
- actions/setup-node@v4
```

platform 이슈 #97 권장 타깃:

| Action | Current | Target |
|---|---:|---:|
| `actions/checkout` | `v4` | `v6` |
| `actions/setup-java` | `v4` | `v5` |
| `actions/setup-node` | `v4` | `v6` |

## Compatibility Notes
- `actions/setup-java`의 기존 `distribution`, `java-version`, `cache` 설정은 유지한다.
- `actions/setup-node`의 기존 `node-version: 22` 설정은 유지한다.
- `actions/checkout`의 dashboard checkout에서 사용하는 `repository`, `token`, `path` 설정은 유지한다.
- shared PR #56에서 같은 방향의 액션 업그레이드 선례가 있다.

## Implementation Plan
1. workflow 파일 2개에서 공식 액션 메이저 버전만 교체한다.
2. 불필요한 formatting 변경은 넣지 않는다.
3. `git diff --check`로 공백 오류를 확인한다.
4. PR 생성 후 GitHub Actions checks에서 실제 동작을 확인한다.

## Test Plan
로컬:

```powershell
git diff --check
```

원격 PR checks:

```text
CI - Java (Gradle) / build
CI - Java (Gradle) / dev-smoke
Parse Workflow -> Dashboard / parse
```

## Local Test Results
- `git diff --check`: PASS
- `rg -n "actions/(checkout|setup-java|setup-node)@v4" .github/workflows`: PASS, 대상 v4 액션 없음
- PyYAML workflow parse: PASS
- 구조 검사: `actions/checkout@v6`, `actions/setup-java@v5`, `actions/setup-node@v6`만 확인
- `actionlint`: 로컬 미설치로 미수행

## PR Plan
- Target branch: `dev`
- PR title: `ci(infra): GitHub Actions Node 20 deprecation 업그레이드 (#97)`
- Related issue: `Closes #97`

## Do Not Touch
- `src/**`
- `build.gradle.kts`
- `settings.gradle.kts`
- `docker-compose*.yml`
- `.env`
- Spring profiles
- 다른 서비스 레포
- `TASK_platform.md`
