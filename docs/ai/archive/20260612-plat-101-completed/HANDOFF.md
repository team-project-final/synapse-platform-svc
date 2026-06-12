# PLAT-101 Handoff

## Status
로컬 구현 및 검증 완료. PR 전.

## Branch
- Base: `origin/dev`
- Working branch: `fix/PLAT-101-prometheus-actuator`
- Target PR branch: `dev`

## Archived
이전 current 문서는 아래 경로에 보존했다.

- `docs/ai/archive/20260612-plat-097-completed/TASK.md`
- `docs/ai/archive/20260612-plat-097-completed/CONTEXT.md`
- `docs/ai/archive/20260612-plat-097-completed/HANDOFF.md`

## Current Task
platform 이슈 #101을 처리한다. `/actuator/prometheus`가 404로 떨어지는 문제를 해결하기 위해 Prometheus registry와 actuator exposure 설정을 보강한다.

## Implementation Checklist
- [x] `build.gradle.kts`에 `runtimeOnly("io.micrometer:micrometer-registry-prometheus")` 추가
- [x] `application.yml` actuator exposure에 `prometheus` 추가
- [x] Prometheus endpoint/export enabled 설정 명시
- [x] `/actuator/prometheus` 200 테스트 추가
- [x] `docs/project-management/history/HISTORY_platform.md` 작업 이력 기록
- [x] 단일 테스트 실행
- [x] `clean build` 실행
- [x] `git diff --check` 실행

## Exact Change Targets

```text
build.gradle.kts
src/main/resources/application.yml
src/test/java/com/synapse/platform/...
docs/project-management/history/HISTORY_platform.md
docs/ai/current/*
```

## Do Not Change
- Kafka topic prefix / #102
- GitOps ServiceMonitor
- business API
- `.env`
- Spring profile files
- shared/gitops/learning/engagement/frontend repos
- `TASK_platform.md`

## Test Commands

```powershell
.\gradlew.bat test --tests "*PrometheusActuatorIntegrationTest"
.\gradlew.bat clean build
git diff --check
```

실행 결과:
- `.\gradlew.bat test --tests "*PrometheusActuatorIntegrationTest"`: PASS
- `.\gradlew.bat clean build`: PASS
- `git diff --check`: PASS

참고:
- Windows Kafka 테스트 종료 훅에서 임시 파일 삭제 경고가 출력됐지만 빌드는 성공했다.

## PR
- Title: `fix(infra): actuator prometheus 메트릭 노출 보강 (#101)`
- Body related issue: `Closes #101`
- Target: `dev`
