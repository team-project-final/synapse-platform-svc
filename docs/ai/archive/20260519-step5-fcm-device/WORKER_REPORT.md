# WORKER REPORT — Step 5: FCM 디바이스 등록

> FROM: Worker (Codex)
> TO: Director (Claude)
> DATE: 2026-05-19
> BRANCH: feature/PLAT-005-fcm-device

---

## Director 보고 필요 사항

작업 중 발견해 수정한 문제와 검증 결과를 아래에 기록한다.

### 1. SecurityFilterChain 순서 적용 위치 오류

- 발견 시점: `DeviceTokenIntegrationTest` 첫 실행
- 증상: `UnreachableFilterChainException` 발생
- 원인: notification 전용 `SecurityFilterChain`보다 기존 default 체인이 먼저 모든 요청을 매칭해 `/api/v1/notifications/**` 체인이 도달 불가능 상태가 됐다.
- 수정: `SecurityConfig.filterChain()`과 `NotificationSecurityConfig.notificationFilterChain()`의 `@Bean` 메서드에 각각 `@Order(2)`, `@Order(1)`을 직접 추가했다.
- 검증: `DeviceTokenIntegrationTest`, `PlatformModuleStructureTest`, `clean build` 통과.
- Director 확인 요청: HANDOFF의 "SecurityConfig @Order 추가" 지시는 클래스 레벨 예시였으나, 실제 동작상 `SecurityFilterChain` bean 메서드 레벨 지정이 필요했다. 이 구현 방향을 확정 처리해도 되는지 확인 필요.
- **Director 결정**: 확정. `@Bean` 메서드 레벨 `@Order`가 Spring Security에서 올바른 방식이다.

### 2. `jwt()` 테스트 헬퍼 의존성 문제

- 발견 시점: `DeviceTokenIntegrationTest` 두 번째 실행
- 증상: `NoClassDefFoundError: JwtGrantedAuthoritiesConverter`
- 원인: `SecurityMockMvcRequestPostProcessors.jwt()`가 현재 프로젝트에 없는 `spring-security-oauth2-resource-server` 클래스를 요구했다.
- 수정: 신규 의존성을 추가하지 않고 `user(userId.toString())` post-processor로 인증 컨텍스트를 주입하도록 테스트를 변경했다.
- 검증: JWT 없음 케이스는 별도 `register_withoutJwt_shouldReturn401`로 검증했고, 인증된 요청 경로는 통합 테스트 8개 시나리오로 검증했다.
- **Director 결정**: 확정. Step 5 범위에서 신규 OAuth2 resource-server 의존성 없이 `user()` post-processor로 유지한다.

### 3. 잘못된 platform 요청이 500으로 응답

- 발견 시점: `register_invalidPlatform_shouldReturn400` 실행
- 증상: `"platform": "MOBILE"` 요청이 400이 아니라 500으로 응답했다.
- 원인: Jackson 역직렬화 실패가 `GlobalExceptionHandler`의 catch-all `Exception` 핸들러로 떨어졌다.
- 수정: `HttpMessageNotReadableException` 핸들러를 추가해 잘못된 JSON 요청을 `PLAT-001`, 400으로 매핑했다.
- 검증: `register_invalidPlatform_shouldReturn400` 통과.
- **Director 결정**: 확정. `PLAT-001`을 invalid JSON/body parse 오류의 공통 코드로 사용한다. `MethodArgumentNotValidException`도 동일 코드 사용 중 — 일관성 있음.

### 4. Modulith 모듈 경계 위반

- 발견 시점: `PlatformModuleStructureTest`
- 증상: notification 모듈이 auth 모듈의 non-exposed type `JwtAuthenticationFilter`에 의존한다는 위반 발생
- 원인: `NotificationSecurityConfig`가 `com.synapse.platform.auth.config.JwtAuthenticationFilter`를 직접 import했다.
- 수정: `@Qualifier("jwtAuthenticationFilter") Filter`로 주입받도록 변경해 auth 내부 구현 타입 직접 의존을 제거했다.
- 검증: `PlatformModuleStructureTest` 통과.
- **Director 결정**: 확정. `jakarta.servlet.Filter` bean 주입 방식이 Modulith 경계 준수의 올바른 패턴이다.

### 5. 검증 중 확인한 비차단 사항

- `@Enumerated` 검색 결과는 기존 billing entity에서만 발견됐고 notification 구현에는 사용하지 않았다.
- `firebase-admin`, `notification_preferences`, `NotificationPlaceholder`, 구 패키지 참조는 최종 검색 결과 없음.
- `DeviceTokenIntegrationTest`는 실제 JWT 서명 검증 대신 Spring Security 인증 컨텍스트를 사용한다. 실제 JWT 파싱/검증은 기존 `JwtAuthenticationFilter`/`JwtTokenProvider` 테스트 범위에 의존한다.
- **Director 결정**: Step 5에서 실제 서명된 JWT 기반 E2E 테스트는 불필요. auth 테스트와 notification 통합 테스트 분리 검증으로 충분하다.

---

## Worker 판단

- 위 1, 3, 4번은 구현/검증 중 실제 실패로 발견했고, 모두 코드 수정 후 회귀 테스트를 통과했다.
- 2번은 테스트 환경 의존성 문제로, Step 5 금지 사항인 불필요한 의존성 추가를 피하는 방향으로 수정했다.
- 5번은 현재 완료를 막는 이슈는 아니지만, director가 테스트 범위와 에러 코드 정책을 확정해야 하는 확인 사항이다.

## 최종 검증 결과

- `./gradlew test`: BUILD SUCCESSFUL
- JaCoCo 라인 커버리지: 92.38% (기준 80% 충족)
- 통합 테스트: 9개 시나리오 전체 통과
