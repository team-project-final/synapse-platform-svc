# PLAT-097 Handoff

## Status
로컬 구현 완료. PR checks 검증 전.

## Branch
- Base: `origin/dev`
- Working branch: `chore/PLAT-097-actions-node24-upgrade`
- Target PR branch: `dev`

## Archived
이전 current 문서는 아래 경로에 보존했다.

- `docs/ai/archive/20260611-plat-073-completed/TASK.md`
- `docs/ai/archive/20260611-plat-073-completed/CONTEXT.md`
- `docs/ai/archive/20260611-plat-073-completed/HANDOFF.md`

## Current Task
platform 이슈 #97을 처리한다. GitHub Actions의 Node 20 deprecation 경고를 제거하기 위해 공식 액션 메이저 버전을 업그레이드한다.

## Implementation Checklist
- [x] `.github/workflows/ci-java.yml`의 `actions/checkout@v4`를 `actions/checkout@v6`로 변경
- [x] `.github/workflows/ci-java.yml`의 `actions/setup-java@v4`를 `actions/setup-java@v5`로 변경
- [x] `.github/workflows/parse-workflow.yml`의 `actions/checkout@v4`를 `actions/checkout@v6`로 변경
- [x] `.github/workflows/parse-workflow.yml`의 `actions/setup-node@v4`를 `actions/setup-node@v6`로 변경
- [x] 기존 `with:` 설정 유지
- [x] `docs/project-management/history/HISTORY_platform.md`에 작업 이력 기록
- [x] `git diff --check` 실행
- [ ] PR 생성 후 GitHub Actions checks 확인

## Exact Change Targets

```text
.github/workflows/ci-java.yml
- actions/checkout@v4 -> actions/checkout@v6
- actions/setup-java@v4 -> actions/setup-java@v5

.github/workflows/parse-workflow.yml
- actions/checkout@v4 -> actions/checkout@v6
- actions/setup-node@v4 -> actions/setup-node@v6
```

## Do Not Change
- Java application code
- Gradle dependency/version
- Docker Compose
- `.env`
- Spring profiles
- GitHub repository settings
- shared/gitops/learning/engagement/frontend repos
- `TASK_platform.md`

## Test Commands
로컬:

```powershell
git diff --check
```

실행 결과:
- `git diff --check`: PASS
- target action `@v4` 잔존 검색: PASS
- workflow YAML parse: PASS
- target `uses:` 버전 구조 검사: PASS
- `actionlint`: 로컬 미설치로 미수행

원격:

```text
PR checks에서 CI - Java (Gradle), Parse Workflow jobs 통과 확인
```

## PR
- Title: `ci(infra): GitHub Actions Node 20 deprecation 업그레이드 (#97)`
- Body related issue: `Closes #97`
- Target: `dev`

## Notes
- 이 작업은 CI 설정 변경이므로 로컬 Gradle build보다 PR checks 결과가 핵심 검증이다.
- README의 열린 이슈 목록은 실제 GitHub open issue 상태와 다르지만, 이번 PLAT-097 범위에서는 제외한다.
