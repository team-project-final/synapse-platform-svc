# SAMPLING — OAuth 회원가입/로그인

> **목적**: Step 2 설계 확정 전, 동일 환경의 별도 프로젝트에서 OAuth 구현을 검증한다.
> 샘플링 결과를 바탕으로 본 프로젝트 설계 및 구현 방향을 확정한다.

---

## 샘플링 환경

| 항목 | 내용 |
|------|------|
| 기반 프로젝트 | synapse-platform-svc 복사본 |
| 기술 스택 | Spring Boot 4.0.0 + Java 21 + Spring Modulith 1.3.0 |
| 빌드 | Gradle (Kotlin DSL) |
| Agent 구성 | Director / Worker / Researcher (본 프로젝트와 동일 역할) |
| 목표 기간 | 1일 |

---

## Agent 역할

| Agent | 역할 |
|-------|------|
| Director | 설계 방향 결정, 코드 리뷰, 결과 취합 |
| Worker | 코드 작성, 테스트 실행 |
| Researcher | Spring Security OAuth2 공식 문서 조사, 라이브러리 옵션 비교 |

---

## 샘플링 목표

아래 항목을 샘플 프로젝트에서 실제로 동작시켜 검증한다.

### 1. 의존성 검증
- [x] `spring-boot-starter-security` + `spring-boot-starter-oauth2-client` 조합이 Spring Boot 4.0.0에서 정상 동작하는지 확인
- [x] `spring-boot-starter-webmvc` 유지 상태에서 OAuth2와의 호환성 확인
- [x] 기존 잘못된 테스트 의존성 3개(`actuator-test`, `validation-test`, `webmvc-test`) 제거 후 빌드 성공 확인

### 2. OAuth 플로우 검증
- [x] Google OAuth Authorization Code Grant 플로우 테스트 패턴 확인
- [x] GitHub OAuth Authorization Code Grant 플로우 테스트 패턴 확인
- [x] `CustomOAuth2UserService.loadUser()` — provider별 attribute 구조 확인 (Google: `sub`, GitHub: `id`)
- [x] `OAuth2AuthenticationSuccessHandler` redirect 동작 확인

### 3. Spring Security 설정 검증
- [x] `SecurityFilterChain` — stateless 세션 + OAuth2 로그인 동시 설정 가능한지 확인
- [x] `CSRF disable` + OAuth2 state 파라미터 방어가 함께 동작하는지 확인
- [x] `/actuator/**` permitAll 설정이 OAuth2 필터와 충돌하지 않는지 확인

### 4. Modulith 호환성 검증
- [x] Security 관련 Bean이 추가된 후 `ApplicationModules.verify()` 통과 여부 확인
- [x] `SecurityConfig`를 auth 모듈 내부에 두었을 때 모듈 경계 위반 없는지 확인

### 5. 테스트 검증
- [x] `@SpringBootTest` + `MockMvc` + `oauth2Login()` 조합으로 통합 테스트 작성 가능한지 확인
- [x] H2 in-memory DB로 테스트 환경 구성 가능한지 확인 (Flyway disable)

### 6. Step 2 / Step 3 경계 검증 (핵심)
- [x] **A안**: success handler에서 userId만 redirect → Step 3에서 JWT 추가
- [x] **B안**: success handler에서 JWT까지 발급 (stub 토큰)
- [x] 두 안 모두 구현해보고 테스트 가능성 및 코드 구조 비교

---

## 보고 항목

샘플링 완료 후 Director가 본 프로젝트에 아래 내용을 보고한다.

```
1. 의존성 최종 목록 (버전 포함)
2. application.yml 최종 설정 구조
3. SecurityConfig 최종 구조
4. CustomOAuth2UserService 구현 패턴
5. success handler A안 vs B안 — 채택 결정 및 근거
6. Modulith verify() 결과
7. 통합 테스트 패턴 (동작 확인된 코드)
8. 발견된 문제점 및 해결 방법
```

---

## 성공 기준

- [x] `./gradlew build` 성공
- [x] Google/GitHub OAuth 플로우 테스트 검증
- [x] 통합 테스트 1건 이상 통과
- [x] `ApplicationModules.verify()` 통과
- [x] Step 2 / Step 3 경계 결정 완료

---

## 샘플링 결과

> 완료 후 여기에 작성

### 채택 결정

- success handler 방식: A안
- 근거:
  - Step 2의 책임은 OAuth 사용자 식별과 회원가입/로그인 매핑까지로 제한한다.
  - A안은 `/auth/callback?userId={userId}`만 반환하므로 Step 3의 RS256 JWT 발급 책임과 분리된다.
  - B안은 stub 토큰이라도 성공 핸들러가 토큰 형식을 소유하게 되어 Step 3 구현 전 경계가 흐려진다.
  - 두 안 모두 테스트 가능하지만, A안이 샘플링 목적과 HANDOFF의 Step 2/3 경계에 더 적합하다.

### 최종 의존성

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator") // 4.0.0
implementation("org.springframework.boot:spring-boot-starter-validation") // 4.0.0
implementation("org.springframework.boot:spring-boot-starter-webmvc") // 4.0.0
implementation("org.springframework.boot:spring-boot-starter-security") // 4.0.0
implementation("org.springframework.boot:spring-boot-starter-oauth2-client") // 4.0.0
implementation("org.springframework.boot:spring-boot-starter-data-jpa") // 4.0.0
implementation("org.flywaydb:flyway-core") // 11.14.1
runtimeOnly("com.h2database:h2") // 2.4.240
implementation("org.springframework.modulith:spring-modulith-starter-core") // 1.3.0
testImplementation("org.springframework.boot:spring-boot-starter-test") // 4.0.0
testImplementation("org.springframework.security:spring-security-test") // 7.0.0
testImplementation("org.springframework.modulith:spring-modulith-starter-test") // 1.3.0
```

### 발견된 문제점

- Spring Boot 4.0.0에서 `AutoConfigureMockMvc`는 기존 `org.springframework.boot.test.autoconfigure.web.servlet` 패키지가 아니라 `spring-boot-webmvc-test` 계열로 분리되어 있었다.
  - HANDOFF가 `spring-boot-starter-webmvc-test` 제거를 지시했으므로 해당 의존성을 되살리지 않았다.
  - 통합 테스트는 `@SpringBootTest`와 `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())` 방식으로 동일 검증을 수행했다.
- `CustomOAuth2UserService`에 테스트용 보조 생성자를 추가하자 Spring이 생성자를 자동 선택하지 못했다.
  - 운영 생성자에 `@Autowired`를 명시하여 컨텍스트 생성 문제를 해결했다.
- `DefaultOAuth2User` 재생성 시 세 번째 인자는 name 값이 아니라 name attribute key여야 했다.
  - Google은 `sub`, GitHub는 `id`를 유지하도록 수정했다.

**[Director 코드 리뷰 추가 발견]**

- `HttpCookieOAuth2AuthorizationRequestRepository`에서 Java 직렬화(`ObjectOutputStream` / `ObjectInputStream`) 를 사용한다.
  - 샘플링에서는 동작하지만 역직렬화 공격(deserialization attack) 가능성이 있으므로 본 프로젝트에서는 JSON 직렬화로 교체해야 한다.
- 쿠키에 `Secure` 플래그와 `SameSite` 설정이 없다.
  - 샘플링 환경(HTTP)에서는 무관하나 본 프로젝트 HTTPS 환경에서는 `Secure=true`, `SameSite=Lax` 적용이 필수다.
- `User.prePersist()`에서 `if (id == null)` 체크는 생성자에서 이미 UUID를 할당하므로 항상 false다.
  - 방어 로직 의도라면 생성자의 할당을 제거하고 `@PrePersist`에서만 처리하거나, 반대로 체크를 제거해야 한다.

### 본 프로젝트 반영 사항

- `SecurityConfig`는 `com.synapse.platform.auth.config`에 두어도 `ApplicationModules.verify()`가 통과한다.
- Step 2는 A안으로 구현하고, Step 3에서 RS256 JWT 발급과 refresh token Redis 저장 정책을 별도 구현한다.
- OAuth state 저장은 서버 세션 대신 `oauth2_auth_request` HttpOnly 쿠키를 사용한다.
- Google/GitHub attribute 매핑은 provider별로 분리한다.
  - Google: `sub`, `email`, `name`, `picture`
  - GitHub: `id`, `email`, `login`, `avatar_url`
- 테스트에서는 `@SpringBootTest` + 수동 `MockMvc` + `oauth2Login()` 조합을 사용한다.
- **쿠키 직렬화 방식 교체 필수**: Java 직렬화 → Jackson 기반 JSON 직렬화.
- **쿠키 보안 속성 추가 필수**: `Secure=true`, `SameSite=Lax`, `HttpOnly=true` (HttpOnly는 샘플링에서도 적용됨).
- `CustomOAuth2UserService` 생성자에서 `@Autowired` 제거 가능: 운영 생성자를 단일로 유지하면 자동 주입된다.
