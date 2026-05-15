# Step 2 OAuth 회원가입/로그인 구현 계획

> **작업자 지침:** 이 계획은 `docs/ai/current/HANDOFF.md`, `CONTEXT.md`, `TASK.md`를 기준으로 작성했다. 구현 시 설계 결정을 새로 하지 말고, 체크박스 순서대로 진행한다.

**목표:** Google/GitHub OAuth를 통한 회원가입과 로그인을 auth 모듈에 구현한다.

**구조:** `users`에는 사용자 프로필만 저장하고, provider/provider_user_id는 `oauth_identities` 분리 테이블에 저장한다. 신규 OAuth 사용자는 개인 tenant, user, oauth identity, tenant member, user settings를 한 트랜잭션에서 생성한다. JWT 발급은 Step 3 범위이므로 Step 2에서는 `/auth/callback?userId=...` redirect까지만 구현한다.

**기술 스택:** Java 21, Spring Boot 4.0.0, Spring Security OAuth2 Client, Spring Data JPA, Flyway, Hibernate JSON 매핑, Spring Modulith 1.3.0, JUnit 5, Mockito, MockMvc.

---

## 파일 작업 범위

### 수정

- `build.gradle.kts`
  - Spring Security, OAuth2 Client, JPA, Flyway, PostgreSQL, H2, `uuid-creator`, Spring Security Test 의존성 추가
- `src/main/resources/application.yml`
  - JPA, Flyway, OAuth2 client registration 설정 추가

### 생성

- `src/main/resources/application-local.yml`
- `src/test/resources/application.yml`
- `src/main/resources/db/migration/V1__init_extensions.sql`
- `src/main/resources/db/migration/V2__init_tenants_and_plans.sql`
- `src/main/resources/db/migration/V3__init_users_and_auth.sql`
- `src/main/resources/db/migration/V16__enable_rls_policies.sql`
- `src/main/resources/db/migration/V17__create_triggers.sql`
- `src/main/resources/db/migration/V18__seed_plan_quotas.sql`
- `src/main/java/com/synapse/platform/auth/domain/Tenant.java`
- `src/main/java/com/synapse/platform/auth/domain/User.java`
- `src/main/java/com/synapse/platform/auth/domain/OAuthIdentity.java`
- `src/main/java/com/synapse/platform/auth/domain/TenantMemberId.java`
- `src/main/java/com/synapse/platform/auth/domain/TenantMember.java`
- `src/main/java/com/synapse/platform/auth/domain/UserSettings.java`
- `src/main/java/com/synapse/platform/auth/repository/UserRepository.java`
- `src/main/java/com/synapse/platform/auth/repository/OAuthIdentityRepository.java`
- `src/main/java/com/synapse/platform/auth/repository/TenantRepository.java`
- `src/main/java/com/synapse/platform/auth/repository/TenantMemberRepository.java`
- `src/main/java/com/synapse/platform/auth/repository/UserSettingsRepository.java`
- `src/main/java/com/synapse/platform/auth/util/SlugGenerator.java`
- `src/main/java/com/synapse/platform/auth/oauth/OAuthAttributes.java`
- `src/main/java/com/synapse/platform/auth/oauth/CustomOAuth2UserService.java`
- `src/main/java/com/synapse/platform/auth/oauth/OAuth2SuccessHandler.java`
- `src/main/java/com/synapse/platform/auth/config/OAuth2AuthorizationRequestDto.java`
- `src/main/java/com/synapse/platform/auth/config/HttpCookieOAuth2AuthorizationRequestRepository.java`
- `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`
- `src/main/java/com/synapse/platform/auth/AuthCallbackController.java`
- `src/test/java/com/synapse/platform/auth/OAuthAttributesTest.java`
- `src/test/java/com/synapse/platform/auth/SlugGeneratorTest.java`
- `src/test/java/com/synapse/platform/auth/CustomOAuth2UserServiceTest.java`
- `src/test/java/com/synapse/platform/auth/HttpCookieOAuth2AuthorizationRequestRepositoryTest.java`
- `src/test/java/com/synapse/platform/auth/OAuth2SuccessHandlerTest.java`
- `src/test/java/com/synapse/platform/auth/OAuth2LoginIntegrationTest.java`
- `src/test/java/com/synapse/platform/auth/OAuthSignupRollbackIntegrationTest.java`

### 삭제

- `src/main/java/com/synapse/platform/auth/AuthPlaceholder.java`
- `src/main/java/com/synapse/platform/auth/AuthController.java`
- `src/main/java/com/synapse/platform/auth/AuthService.java`

### 수정하지 않음

- `src/test/java/com/synapse/platform/ModuleStructureTest.java`
  - 수정하지 않고 검증만 수행
- `src/main/java/com/synapse/platform/auth/package-info.java`
  - 현재 `allowedDependencies = {"shared"}` 유지

---

## Task 1. 기준 상태 확인 및 의존성 추가

**파일**
- 수정: `build.gradle.kts`

- [x] 현재 브랜치와 변경 상태를 확인한다.

```powershell
git status --short --branch
rg --files src/main/java/com/synapse/platform/auth src/test/java/com/synapse/platform
```

기대 결과:
- 브랜치가 `feature/PLAT-004-oauth`
- auth 모듈에는 placeholder 파일과 `package-info.java`만 존재

- [x] `build.gradle.kts`에 HANDOFF 지정 의존성을 추가한다.

추가 대상:
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-client`
- `spring-boot-starter-data-jpa`
- `flyway-core`
- `uuid-creator:5.3.3`
- PostgreSQL runtime
- Flyway PostgreSQL database runtime
- H2 test runtime
- Spring Security test

- [x] 의존성 해석을 확인한다.

```powershell
.\gradlew.bat dependencies
```

기대 결과:
- 의존성 해석 성공

- [x] 변경 범위를 확인한다.

```powershell
git diff -- build.gradle.kts
```

기대 결과:
- 필요한 의존성만 추가됨

---

## Task 2. 애플리케이션 설정 작성

**파일**
- 수정: `src/main/resources/application.yml`
- 생성: `src/main/resources/application-local.yml`
- 생성: `src/test/resources/application.yml`

- [x] `application.yml`을 HANDOFF 기준으로 수정한다.

포함 항목:
- `server.port: 8081`
- `spring.application.name: synapse-platform-svc`
- `spring.jpa.hibernate.ddl-auto: validate`
- `spring.jpa.open-in-view: false`
- `spring.flyway.enabled: true`
- Google OAuth scope: `openid,email,profile`
- GitHub OAuth scope: `read:user,user:email`
- OAuth client id/secret은 환경변수 placeholder만 사용

- [x] `application-local.yml`을 생성한다.

내용:
- PostgreSQL URL: `jdbc:postgresql://localhost:5432/synapsedb`
- username: `synapse`
- password: `synapse123`
- driver: `org.postgresql.Driver`

- [x] 테스트용 `src/test/resources/application.yml`을 생성한다.

내용:
- H2 PostgreSQL mode
- `ddl-auto: create-drop`
- `spring.flyway.enabled: false`
- Google/GitHub test OAuth placeholder 값

- [x] 실제 secret이 들어가지 않았는지 확인한다.

```powershell
rg "client-secret: [^$]" src/main/resources src/test/resources
```

기대 결과:
- 실제 운영 secret 없음
- 테스트 placeholder만 허용

---

## Task 3. Flyway 마이그레이션 작성

**파일**
- 생성: `src/main/resources/db/migration/V1__init_extensions.sql`
- 생성: `src/main/resources/db/migration/V2__init_tenants_and_plans.sql`
- 생성: `src/main/resources/db/migration/V3__init_users_and_auth.sql`
- 생성: `src/main/resources/db/migration/V16__enable_rls_policies.sql`
- 생성: `src/main/resources/db/migration/V17__create_triggers.sql`
- 생성: `src/main/resources/db/migration/V18__seed_plan_quotas.sql`

- [x] 마이그레이션 디렉터리를 생성한다.

```powershell
New-Item -ItemType Directory -Force -Path src\main\resources\db\migration
```

- [x] V1에 PostgreSQL extension을 작성한다.

포함:
- `uuid-ossp`
- `pg_trgm`
- `pgcrypto`
- `vector`
- `btree_gin`

- [x] V2에 plan, tenant, tenant member 스키마를 작성한다.

포함:
- `plan_quotas`
- `free`, `pro`, `team`, `enterprise` seed
- `enterprise.price_usd_monthly = 0.00`
- `tenants`
- `tenant_members`
- tenant slug partial unique index

- [x] V3에 user/auth 스키마를 작성한다.

포함:
- `users`
- `oauth_identities`
- `user_settings`
- `tenant_members.user_id` FK 후행 추가

주의:
- `users`에 provider/provider_id 컬럼을 만들지 않는다.
- provider/provider_user_id는 `oauth_identities`에 저장한다.

- [x] V16에 RLS policy를 작성한다.

필수:

```sql
current_setting('app.tenant_id', TRUE)
```

- [x] V17에 `updated_at` trigger를 작성한다.

대상:
- `users`
- `tenants`

- [x] V18은 빈 seed marker로 유지한다.

- [x] 파일명을 확인한다.

```powershell
rg --files src/main/resources/db/migration
```

기대 결과:
- V1, V2, V3, V16, V17, V18 존재

---

## Task 4. Domain Entity 작성

**파일**
- 생성: `Tenant.java`
- 생성: `User.java`
- 생성: `OAuthIdentity.java`
- 생성: `TenantMemberId.java`
- 생성: `TenantMember.java`
- 생성: `UserSettings.java`

경로:
- `src/main/java/com/synapse/platform/auth/domain/`

- [x] HANDOFF의 Entity 명세대로 파일을 작성한다.

필수 규칙:
- Entity 생성자는 `protected`
- Setter 금지
- UUID PK는 `UuidCreator.getTimeOrderedEpoch()` 사용
- JSONB 필드는 `@Column(columnDefinition = "jsonb")`와 `@JdbcTypeCode(SqlTypes.JSON)` 병기
- `TenantMember`와 `UserSettings`는 외부에서 ID를 지정

- [x] 정적 팩토리 메서드를 작성한다.

필수 메서드:
- `Tenant.ofPersonal(String displayName, String slug)`
- `User.ofOAuth(String email, String username, String displayName, String avatarUrl)`
- `OAuthIdentity.of(User user, String provider, String providerUserId, String email)`
- `TenantMember.ofOwner(UUID tenantId, UUID userId)`
- `UserSettings.defaultFor(UUID userId)`

- [x] Setter가 없는지 확인한다.

```powershell
rg "set[A-Z]|@Setter" src/main/java/com/synapse/platform/auth/domain
```

기대 결과:
- 불필요한 setter 없음

---

## Task 5. Repository 작성

**파일**
- 생성: `UserRepository.java`
- 생성: `OAuthIdentityRepository.java`
- 생성: `TenantRepository.java`
- 생성: `TenantMemberRepository.java`
- 생성: `UserSettingsRepository.java`

경로:
- `src/main/java/com/synapse/platform/auth/repository/`

- [x] Repository 인터페이스를 작성한다.

필수 메서드:
- `UserRepository.findByEmail(String email)`
- `OAuthIdentityRepository.findByProviderAndProviderUserId(String provider, String providerUserId)`
- `TenantRepository.existsBySlug(String slug)`

- [x] 모듈 경계 위반 import가 없는지 확인한다.

```powershell
rg "^import com\.synapse\.platform\.(billing|notification|audit)\." src/main/java/com/synapse/platform/auth
```

기대 결과:
- 매치 없음

---

## Task 6. SlugGenerator 구현 및 테스트

**파일**
- 생성: `src/main/java/com/synapse/platform/auth/util/SlugGenerator.java`
- 생성: `src/test/java/com/synapse/platform/auth/SlugGeneratorTest.java`

- [x] 테스트를 먼저 작성한다.

테스트 3건:
- 중복 없음: `user@example.com` -> `user`
- 중복 1회: base가 존재하면 `base_{6자리}` 반환
- base가 비면 `user` prefix 사용

- [x] 실패 확인을 위해 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.SlugGeneratorTest"
```

- [x] `SlugGenerator`를 구현한다.

규칙:
- email의 `@` 앞부분 사용
- 소문자화
- 영숫자만 유지
- 빈 값이면 `user`
- 중복 시 `_{6자리 난수}` suffix
- 최대 10회 시도 후 실패하면 `IllegalStateException`

- [x] 테스트 통과를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.SlugGeneratorTest"
```

기대 결과:
- 3건 통과

---

## Task 7. OAuthAttributes 구현 및 테스트

**파일**
- 생성: `src/main/java/com/synapse/platform/auth/oauth/OAuthAttributes.java`
- 생성: `src/test/java/com/synapse/platform/auth/OAuthAttributesTest.java`

- [x] 테스트를 먼저 작성한다.

테스트 4건:
- Google attribute mapping
- GitHub attribute mapping
- GitHub email null 유지
- 알 수 없는 provider 예외

- [x] 실패 확인을 위해 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuthAttributesTest"
```

- [x] `OAuthAttributes` record를 구현한다.

필드:
- `provider`
- `providerId`
- `email`
- `name`
- `avatarUrl`

규칙:
- Google name attribute key: `sub`
- GitHub name attribute key: `id`
- 지원 provider 외에는 `IllegalArgumentException`

- [x] 테스트 통과를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuthAttributesTest"
```

기대 결과:
- 4건 통과

---

## Task 8. OAuth 요청 쿠키 저장소 구현 및 테스트

**파일**
- 생성: `OAuth2AuthorizationRequestDto.java`
- 생성: `HttpCookieOAuth2AuthorizationRequestRepository.java`
- 생성: `HttpCookieOAuth2AuthorizationRequestRepositoryTest.java`

- [x] 테스트를 먼저 작성한다.

테스트 4건:
- 저장 시 HttpOnly cookie 생성
- 저장된 cookie에서 authorization request 복원
- remove 시 request 반환 및 cookie 삭제
- null 저장 시 cookie 삭제

- [x] 실패 확인을 위해 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.HttpCookieOAuth2AuthorizationRequestRepositoryTest"
```

- [x] DTO와 Repository를 구현한다.

필수:
- Jackson JSON 직렬화
- Base64 URL 인코딩
- Java 직렬화 사용 금지
- cookie name: `oauth2_auth_request`
- path: `/`
- HttpOnly: `true`
- Max-Age: `180`
- SameSite: `Lax`

- [x] 테스트 통과를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.HttpCookieOAuth2AuthorizationRequestRepositoryTest"
```

기대 결과:
- 4건 통과

---

## Task 9. CustomOAuth2UserService 구현 및 테스트

**파일**
- 생성: `src/main/java/com/synapse/platform/auth/oauth/CustomOAuth2UserService.java`
- 생성: `src/test/java/com/synapse/platform/auth/CustomOAuth2UserServiceTest.java`

- [x] 테스트를 먼저 작성한다.

테스트 4건:
- Case A: 동일 provider 재로그인 시 기존 user 반환
- Case B: 같은 email 기존 사용자면 oauth identity만 추가
- Case C: 신규 Google 사용자는 tenant, user, identity, member, settings 생성
- repository 실패 시 예외 전파 및 이후 저장 중단

- [x] 실패 확인을 위해 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.CustomOAuth2UserServiceTest"
```

- [x] `CustomOAuth2UserService`를 구현한다.

필수:
- `@Transactional`
- 기본 생성자는 `DefaultOAuth2UserService` delegate 사용
- 테스트용 package-private 생성자에서 delegate 주입 허용
- Case A/B/C 로직 구현
- Case C 저장 순서: Tenant -> User -> OAuthIdentity -> TenantMember -> UserSettings
- GitHub email null이면 `users.email`은 `{login}@github.placeholder`, `oauth_identities.email`은 null 유지

- [x] 테스트 통과를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.CustomOAuth2UserServiceTest"
```

기대 결과:
- 4건 통과

---

## Task 10. OAuth 성공 핸들러와 콜백 컨트롤러 작성

**파일**
- 삭제: 기존 auth placeholder 3개
- 생성: `OAuth2SuccessHandler.java`
- 생성: `AuthCallbackController.java`
- 생성: `OAuth2SuccessHandlerTest.java`

- [x] placeholder 파일을 삭제한다.

삭제 대상:
- `AuthPlaceholder.java`
- `AuthController.java`
- `AuthService.java`

- [x] 성공 핸들러 테스트를 먼저 작성한다.

테스트 1건:
- OAuth2 principal의 `userId`를 읽어 `/auth/callback?userId={userId}`로 redirect

- [x] `OAuth2SuccessHandler`를 구현한다.

필수:
- `@Component`
- `AuthenticationSuccessHandler` 구현
- Step 3 전까지 userId redirect만 수행

- [x] `AuthCallbackController`를 구현한다.

필수:
- `@RestController`
- `@RequestMapping("/auth")`
- `GET /callback`
- `@RequestParam String userId`
- 응답: `{"userId": "..."}` 형태

- [x] 테스트 통과를 확인한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuth2SuccessHandlerTest"
```

기대 결과:
- 1건 통과

---

## Task 11. SecurityConfig 작성

**파일**
- 생성: `src/main/java/com/synapse/platform/auth/config/SecurityConfig.java`

- [x] Security filter chain을 구현한다.

필수:
- `@Configuration`
- `@EnableWebSecurity`
- stateless session
- CSRF disable
- OAuth2 login 활성화
- authorization request repository는 쿠키 저장소 사용
- user info endpoint는 `CustomOAuth2UserService` 사용
- success handler는 `OAuth2SuccessHandler` 사용
- permit all: `/actuator/**`, `/oauth2/**`, `/login/**`, `/auth/callback`
- 나머지는 인증 필요

- [x] 쿠키 authorization request repository bean을 등록한다.

필수:
- `HttpCookieOAuth2AuthorizationRequestRepository`
- `ObjectMapper` 주입

- [x] 모듈 경계 위반 import가 없는지 확인한다.

```powershell
rg "^import com\.synapse\.platform\.(billing|notification|audit)\." src/main/java/com/synapse/platform/auth
```

기대 결과:
- 매치 없음

---

## Task 12. OAuth 로그인 통합 테스트 작성

**파일**
- 생성: `src/test/java/com/synapse/platform/auth/OAuth2LoginIntegrationTest.java`

- [x] `@SpringBootTest` + MockMvc 통합 테스트를 작성한다.

테스트 3건:
- 인증된 OAuth user가 `/auth/callback` 호출 시 200 OK
- `userId` 누락 시 400 Bad Request
- 보호된 endpoint는 미인증 시 401 또는 redirect

- [x] 통합 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuth2LoginIntegrationTest"
```

기대 결과:
- 3건 통과

---

## Task 13. OAuth 가입 롤백 통합 테스트 작성

**파일**
- 생성: `src/test/java/com/synapse/platform/auth/OAuthSignupRollbackIntegrationTest.java`

- [x] 트랜잭션 롤백 통합 테스트를 작성한다.

검증:
- downstream repository 실패가 발생하면 예외가 전파됨
- 해당 OAuth 가입 시도의 user/tenant 등 부분 데이터가 DB에 남지 않음

주의:
- Spring transaction proxy를 우회하지 않는 방식으로 실패를 주입한다.

- [x] 롤백 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.OAuthSignupRollbackIntegrationTest"
```

기대 결과:
- 1건 통과

---

## Task 14. 최종 검증

- [x] auth 테스트 전체를 실행한다.

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.*"
```

기대 결과:
- auth 테스트 20건 이상 통과

- [x] Modulith 구조 검증을 실행한다.

```powershell
.\gradlew.bat test --tests "*ModuleStructureTest"
```

기대 결과:
- `ApplicationModules.verify()` 통과

- [x] 전체 빌드를 실행한다.

```powershell
.\gradlew.bat build
```

기대 결과:
- 전체 빌드 성공
- 전체 테스트 통과

- [x] 금지 패턴을 확인한다.

```powershell
rg "System\.out\.println|ObjectOutputStream|ObjectInputStream|HS256|refresh.*token" src/main/java src/test/java
```

기대 결과:
- 구현 코드에 금지 패턴 없음

- [x] 최종 보고서를 HANDOFF 형식으로 작성한다.

```markdown
## 구현 완료 보고

### 생성/수정 파일 목록
- ...

### 테스트 결과
.\gradlew.bat build 결과: N tests, N passed, 0 failed
.\gradlew.bat test --tests "*ModuleStructureTest" 결과: passed

### 특이사항
- ...
```

---

## 완료 기준 체크리스트

- [x] Google OAuth 로그인 -> 신규 사용자 자동 회원가입
- [x] GitHub OAuth 로그인 -> 신규 사용자 자동 회원가입
- [x] GitHub email null -> placeholder 처리
- [x] 기존 사용자 OAuth 로그인 시 기존 계정과 매핑
- [x] provider/provider_user_id는 `oauth_identities`에 저장
- [x] `users`에 provider/provider_id 직접 저장 없음
- [x] OAuth state는 Spring Security OAuth2 flow와 쿠키 저장소로 보존
- [x] Java 직렬화 미사용
- [x] Refresh Token DB 저장 없음
- [x] JWT 발급은 Step 3으로 보류
- [x] auth 테스트 20건 이상 통과
- [x] `ModuleStructureTest` 통과
- [x] `.\gradlew.bat build` 성공
