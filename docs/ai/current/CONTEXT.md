# PLAT-101 Context

## Current State
- Target task: PLAT-101
- Working branch: `fix/PLAT-101-prometheus-actuator`
- Base: `origin/dev`
- Target PR branch: `dev`
- 작업 대상 repo: `synapse-platform-svc`
- 현재 상태: 로컬 구현 및 검증 완료, PR 전

## Background
platform 이슈 #101은 EKS staging에서 Prometheus ServiceMonitor가 `/actuator/prometheus`를 스크랩하지만 404로 실패하는 문제다.

이슈 본문에 기록된 예외:

```text
No static resource actuator/prometheus for request '/actuator/prometheus'
```

이 패턴은 Spring Security 401/403이 아니라 actuator endpoint가 등록/노출되지 않았을 때 나타나는 404다.

## Current Code Evidence

### Dependency
현재 `build.gradle.kts`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

Prometheus endpoint에 필요한 registry 의존성은 아직 없다.

```kotlin
runtimeOnly("io.micrometer:micrometer-registry-prometheus")
```

### Actuator Exposure
현재 `src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`prometheus`가 노출 대상에 포함되어 있지 않다.

### Security
현재 `SecurityConfig`는 `/actuator/**`를 permitAll로 허용한다. 따라서 이번 이슈의 핵심은 security rule 추가가 아니라 actuator endpoint 등록/노출이다.

## Proposed Change
- Prometheus registry runtime dependency 추가
- actuator exposure에 `prometheus` 추가
- prometheus endpoint/export enabled 명시
- MockMvc/SpringBootTest 계열 테스트에서 `/actuator/prometheus` 200 확인

## Risk Review

### Low Risk
- actuator/observability 설정 변경이며 비즈니스 로직에 직접 영향이 없다.
- `/actuator/**`는 이미 permitAll이므로 보안 정책의 실질 변경은 없다.

### Watch Points
- Spring Boot 4 환경에서 Prometheus registry 의존성 이름이 맞는지 Gradle resolve로 검증해야 한다.
- 테스트 환경에서도 actuator endpoint가 동일하게 노출되는지 확인해야 한다.
- response content type은 Prometheus text format 계열이므로 정확한 charset까지 과도하게 고정하지 않는다.

## Verification Strategy
1. 단일 테스트로 `/actuator/prometheus` endpoint 200 확인
2. `clean build`로 전체 회귀 확인
3. PR checks에서 staging과 동일한 boot/dev-smoke 경로 확인

## Related Issues
- #101: 이번 작업
- #102: Kafka topic prefix. 범위가 크므로 별도 작업으로 분리
- #62: W5 E2E umbrella. #101/#102 이후 검증성으로 정리
