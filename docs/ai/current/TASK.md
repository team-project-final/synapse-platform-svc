# PLAT-101 Actuator Prometheus 메트릭 노출

## Task ID
PLAT-101

## Title
`/actuator/prometheus` 엔드포인트 노출 보강

## Owner
platform

## Status
IMPLEMENTED_LOCAL

## Priority
P1

## Branch
`fix/PLAT-101-prometheus-actuator`

## Base
`origin/dev`

## Issue
platform 이슈 #101: `observability: /actuator/prometheus 미노출 -> Prometheus 스크랩 실패(메트릭 누락)`

## Step Goal
EKS staging에서 ServiceMonitor가 platform-svc의 `/actuator/prometheus`를 스크랩할 수 있도록 Prometheus registry 의존성과 actuator 노출 설정을 보강한다.

현재 `/actuator/prometheus` 요청은 actuator endpoint가 노출되지 않아 Spring MVC 정적 리소스 조회로 흘러가고 404가 발생한다. 이번 작업은 observability 설정 보강이며, 애플리케이션 비즈니스 로직은 변경하지 않는다.

## Done When
- [x] `micrometer-registry-prometheus` 런타임 의존성이 추가된다.
- [x] actuator web exposure에 `prometheus`가 포함된다.
- [x] Prometheus metrics export가 활성화된다.
- [x] `/actuator/prometheus`가 보안 필터에서 차단되지 않는다.
- [x] 테스트에서 `/actuator/prometheus` 200 및 Prometheus 텍스트 응답을 확인한다.
- [x] `git diff --check`를 통과한다.
- [x] `clean build` 또는 관련 테스트가 통과한다.

## Scope

### In Scope
- `build.gradle.kts`
- `src/main/resources/application.yml`
- actuator/prometheus 노출 테스트
- 작업 이력 문서 갱신

### Out of Scope
- Prometheus/Grafana/GitOps 설정 수정
- ServiceMonitor 수정
- 비즈니스 API 수정
- Kafka topic prefix 이슈 #102
- W5 E2E umbrella #62
- `.env` 또는 Spring profile 수정
- 다른 서비스 레포 수정
- `TASK_platform.md` 수정

## Current Evidence
- `build.gradle.kts`에는 `spring-boot-starter-actuator`는 있으나 `io.micrometer:micrometer-registry-prometheus`가 없다.
- `application.yml`의 actuator exposure는 `health,info`만 포함한다.
- `SecurityConfig`는 `/actuator/**`를 permitAll로 열고 있어 인증 차단이 주 원인은 아니다.

관련 위치:
- `build.gradle.kts`
- `src/main/resources/application.yml`
- `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`

## Implementation Plan
1. `runtimeOnly("io.micrometer:micrometer-registry-prometheus")`를 추가한다.
2. `management.endpoints.web.exposure.include`에 `prometheus`를 추가한다.
3. `management.endpoint.prometheus.enabled=true`와 `management.prometheus.metrics.export.enabled=true`를 명시한다.
4. `/actuator/prometheus` 공개 테스트를 추가한다.
5. history/current 문서를 갱신한다.

## Test Plan
우선 관련 테스트:

```powershell
.\gradlew.bat test --tests "*PrometheusActuatorIntegrationTest"
```

최종 검증:

```powershell
.\gradlew.bat clean build
git diff --check
```

## Test Results
- `.\gradlew.bat test --tests "*PrometheusActuatorIntegrationTest"`: PASS
- `.\gradlew.bat clean build`: PASS
- `git diff --check`: PASS

Notes:
- Windows 환경에서 Kafka 테스트 종료 시 임시 파일 삭제 경고 로그가 출력됐지만 Gradle 결과는 `BUILD SUCCESSFUL`이다.

## PR Plan
- Target branch: `dev`
- PR title: `fix(infra): actuator prometheus 메트릭 노출 보강 (#101)`
- Related issue: `Closes #101`

## Do Not Touch
- `src/main/java` 비즈니스 로직
- Kafka 설정/토픽 프리픽스
- GitOps/ServiceMonitor
- 다른 서비스 레포
- `.env`
- `TASK_platform.md`
