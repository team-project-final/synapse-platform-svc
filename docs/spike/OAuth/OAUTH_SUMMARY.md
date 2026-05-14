# OAuth 2.0 구현 정리

> 샘플링 완료 기준: 2026-05-13
> 대상 provider: Google, GitHub
> 기술 스택: Spring Boot 4.0.0 + Spring Security 7.0.0 + Spring Modulith 1.3.0

---

## 1. OAuth 2.0 Authorization Code Grant 플로우

이 프로젝트에서 사용한 방식이다. 브라우저 기반 웹 애플리케이션의 표준 인증 방식이다.

```
사용자 브라우저          우리 서버                    Provider (Google/GitHub)
      │                     │                                  │
      │  GET /oauth2/authorization/google                      │
      │────────────────────>│                                  │
      │                     │  state 생성 → 쿠키에 저장        │
      │  302 redirect        │                                  │
      │<────────────────────│                                  │
      │                     │                                  │
      │  GET /o/oauth2/v2/auth?client_id=...&state=...         │
      │─────────────────────────────────────────────────────>  │
      │                     │                                  │
      │  로그인 + 동의 화면                                       │
      │<──────────────────────────────────────────────────────  │
      │  동의 완료                                               │
      │─────────────────────────────────────────────────────>  │
      │                     │                                  │
      │  302 /login/oauth2/code/google?code=AUTH_CODE&state=.. │
      │<──────────────────────────────────────────────────────  │
      │                     │                                  │
      │  GET /login/oauth2/code/google?code=...&state=...      │
      │────────────────────>│                                  │
      │                     │  state 검증 (쿠키 ↔ 파라미터)    │
      │                     │  POST /token (code 교환)          │
      │                     │─────────────────────────────────>│
      │                     │  access_token 반환                │
      │                     │<─────────────────────────────────│
      │                     │  GET /userinfo (프로필 조회)      │
      │                     │─────────────────────────────────>│
      │                     │  {sub/id, email, name, ...}       │
      │                     │<─────────────────────────────────│
      │                     │  DB upsert → userId 추출          │
      │  302 /auth/callback?userId={userId}                     │
      │<────────────────────│                                  │
```

**핵심 포인트:**
- `code`는 1회용이며 만료 시간이 짧다 (보통 10분 이내)
- `state`는 CSRF 방어용 nonce — 요청 시 생성한 값과 콜백에서 받은 값이 일치해야 한다
- `access_token`은 우리 서버만 보유하며 브라우저에 절대 노출하지 않는다

---

## 2. Stateless + OAuth2 공존 — 핵심 설계 문제

### 문제 상황

Spring Security의 OAuth2 Authorization Code Grant는 기본적으로 **서버 세션**에 `state`를 저장한다.

```
요청 1: /oauth2/authorization/google → state 생성 → HttpSession에 저장
요청 2: /login/oauth2/code/google?state=... → HttpSession에서 state 꺼내서 검증
```

JWT 기반 인증 아키텍처는 `SessionCreationPolicy.STATELESS`를 요구한다.
STATELESS 설정 시 서버 세션이 생성되지 않아 요청 2에서 state를 찾을 수 없어 인증이 실패한다.

### 해결책 — HttpCookieOAuth2AuthorizationRequestRepository

state를 서버 세션 대신 **HttpOnly 쿠키**에 저장한다.

```
요청 1: /oauth2/authorization/google
  → state 생성
  → OAuth2AuthorizationRequest 직렬화 → Base64 인코딩
  → Set-Cookie: oauth2_auth_request={직렬화값}; HttpOnly; Path=/; Max-Age=180

요청 2: /login/oauth2/code/google?state=...
  → Cookie: oauth2_auth_request={직렬화값} 읽기
  → 역직렬화 → state 검증 성공
  → 쿠키 삭제 (Max-Age=0)
```

```java
// SecurityConfig에서 연결
.oauth2Login(oauth2 -> oauth2
    .authorizationEndpoint(a -> a
        .authorizationRequestRepository(cookieAuthorizationRequestRepository())))
```

### 본 프로젝트 반영 시 주의

샘플링에서는 Java 직렬화(`ObjectOutputStream`)를 사용했다.
본 프로젝트에서는 역직렬화 공격 방지를 위해 **Jackson JSON 직렬화**로 교체해야 한다.

---

## 3. Provider별 Attribute 구조

OAuth2 provider마다 사용자 정보의 필드명이 다르다.

### Google

| 우리 필드 | Google attribute | 비고 |
|----------|-----------------|------|
| providerId | `sub` | 고유 식별자, String |
| email | `email` | 항상 존재 |
| name | `name` | 항상 존재 |
| avatarUrl | `picture` | 항상 존재 |

```json
{
  "sub": "106489855987534988836",
  "email": "user@gmail.com",
  "name": "홍길동",
  "picture": "https://lh3.googleusercontent.com/..."
}
```

### GitHub

| 우리 필드 | GitHub attribute | 비고 |
|----------|-----------------|------|
| providerId | `id` | Integer → String.valueOf() 변환 필요 |
| email | `email` | public email 미설정 시 null |
| name | `login` | `name` 필드는 nullable이므로 `login` 사용 |
| avatarUrl | `avatar_url` | 항상 존재 |

```json
{
  "id": 12345678,
  "login": "octocat",
  "email": "user@example.com",
  "avatar_url": "https://avatars.githubusercontent.com/u/12345678"
}
```

### GitHub email null 주의

GitHub는 email을 public으로 설정하지 않으면 `email`이 `null`로 반환된다.
본 프로젝트에서는 다음 중 하나를 선택해야 한다:
- GitHub `/user/emails` API를 추가 호출해 primary email을 가져온다
- email 없이 `provider + providerId` 기준으로만 사용자를 식별한다

---

## 4. Spring Security OAuth2 구현 패턴

### SecurityConfig 핵심 구조

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ...) throws Exception {
        return http
            // 1. 세션 비활성화 — JWT 아키텍처 요구사항
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 2. CSRF 비활성화 — OAuth2 state 파라미터가 CSRF를 대신 방어
            .csrf(AbstractHttpConfigurer::disable)
            // 3. OAuth2 로그인
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(a -> a
                    .authorizationRequestRepository(cookieRepo))   // state → 쿠키
                .userInfoEndpoint(u -> u.userService(customUserService))
                .successHandler(successHandler))
            // 4. 공개 경로
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated())
            .build();
    }
}
```

**CSRF와 OAuth2 state 관계:**
CSRF를 disable해도 OAuth2 플로우는 `state` 파라미터로 CSRF를 자체 방어한다.
`state`는 서버가 요청 시 생성하고 콜백에서 검증하는 nonce이므로 외부에서 위조할 수 없다.

### CustomOAuth2UserService 패턴

```java
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) {
        // 1. delegate로 provider API 호출 (Spring이 token 교환 포함 처리)
        OAuth2User oAuth2User = delegate.loadUser(request);

        // 2. provider별 attribute 추출
        String registrationId = request.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        // 3. DB upsert
        User user = saveOrUpdate(attrs);

        // 4. userId를 attribute에 추가해서 반환 (SuccessHandler에서 사용)
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("userId", user.getId().toString());

        return new DefaultOAuth2User(
            oAuth2User.getAuthorities(),
            attributes,
            nameAttributeKey(registrationId)  // "sub"(Google) or "id"(GitHub)
        );
    }
}
```

**주의:** `DefaultOAuth2User` 세 번째 인자는 name attribute의 **키 이름**이다 (값이 아님).
잘못 넣으면 `OAuth2User.getName()`이 의도하지 않은 값을 반환한다.

### 테스트 가능한 설계 — delegate 패턴

`DefaultOAuth2UserService`를 직접 `new`로 생성하면 테스트에서 실제 HTTP 호출이 발생한다.
**생성자 주입**으로 delegate를 외부에서 교체 가능하게 만들어야 한다.

```java
// 운영 생성자 — Spring이 자동 주입
@Autowired
public CustomOAuth2UserService(UserRepository userRepository) {
    this(userRepository, new DefaultOAuth2UserService());
}

// 패키지-private 생성자 — 테스트에서 mock delegate 주입
CustomOAuth2UserService(UserRepository repo, OAuth2UserService<?, ?> delegate) {
    this.userRepository = repo;
    this.delegate = delegate;
}
```

---

## 5. Step 2 / Step 3 경계 — 채택 결정

### A안 (채택)

```
[OAuth 인증 완료]
       ↓
[CustomOAuth2UserService] → DB upsert → userId attribute 추가
       ↓
[OAuth2SuccessHandler] → redirect /auth/callback?userId={userId}
       ↓
[Step 3에서 교체] → JWT 발급 → redirect /auth/callback?token={jwt}
```

### B안 (비교용, 미채택)

```
[OAuth 인증 완료]
       ↓
[OAuth2SuccessHandlerStub] → stub.{Base64(userId)} 생성
       ↓
redirect /auth/callback?token=stub.{...}
```

### A안 채택 근거

| 기준 | A안 | B안 |
|------|-----|-----|
| Step 2 책임 범위 | OAuth 인증 + 사용자 식별만 | 토큰 형식까지 소유 |
| Step 3 독립성 | 완전 분리 | stub 형식이 Step 3에 영향 |
| 테스트 독립성 | Step 2만 단독 검증 가능 | Step 3 stub 구조에 의존 |
| 본 프로젝트 교체 범위 | callback 처리만 수정 | 핸들러 전체 교체 필요 |

---

## 6. 쿠키 보안 속성 체크리스트 (본 프로젝트 필수 적용)

| 속성 | 샘플링 적용 여부 | 본 프로젝트 필수 여부 | 이유 |
|------|---------------|---------------------|------|
| `HttpOnly` | ✅ | ✅ | XSS로 쿠키 탈취 방지 |
| `Secure` | ❌ (HTTP 환경) | ✅ | HTTPS 전송만 허용 |
| `SameSite=Lax` | ❌ | ✅ | CSRF 추가 방어 |
| `Max-Age=180` | ✅ | ✅ | OAuth 플로우 완료 후 자동 만료 |
| 직렬화 방식 | Java 직렬화 | JSON 직렬화 교체 | 역직렬화 공격 방지 |

---

## 7. Spring Modulith 검증 결과

`SecurityConfig`를 `com.synapse.platform.auth.config`에 배치한 상태에서 `ApplicationModules.verify()` 통과를 확인했다.

```
모듈 구조 (verify 통과 기준)
com.synapse.platform
├── auth     (allowedDependencies = {"shared"})
│   ├── config/SecurityConfig
│   ├── config/HttpCookieOAuth2AuthorizationRequestRepository
│   ├── domain/User
│   ├── oauth/OAuthAttributes
│   ├── oauth/CustomOAuth2UserService
│   ├── oauth/OAuth2SuccessHandler
│   ├── oauth/OAuth2SuccessHandlerStub
│   └── repository/UserRepository
└── shared   (allowedDependencies = {})
```

SecurityConfig는 다른 모듈에서 참조하지 않는다.
Spring이 FilterChain Bean을 자동 수집하므로 auth 모듈 내부에 두어도 경계를 위반하지 않는다.

---

## 8. 테스트 전략 요약

| 계층 | 테스트 클래스 | 방식 | 테스트 수 |
|------|-------------|------|----------|
| Entity | UserTest | 순수 단위 | 2 |
| Attribute 매핑 | OAuthAttributesTest | 순수 단위 | 4 |
| Service | CustomOAuth2UserServiceTest | Mockito | 3 |
| Handler A안 | OAuth2SuccessHandlerTest | MockHttpServletRequest/Response | 1 |
| Handler B안 | OAuth2SuccessHandlerStubTest | MockHttpServletRequest/Response | 1 |
| Cookie 저장소 | HttpCookieOAuth2AuthorizationRequestRepositoryTest | MockHttpServletRequest/Response | 3 |
| 통합 | OAuth2LoginIntegrationTest | @SpringBootTest + MockMvc + oauth2Login() | 3 |
| Modulith | ModuleStructureTest | ApplicationModules.verify() | 1 |
| **합계** | | | **19 / 19 통과** |

### 통합 테스트 핵심 패턴

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuth2LoginIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())  // 필수 — Security FilterChain 적용
                .build();
    }

    @Test
    void googleCallback() throws Exception {
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-123", "email", "user@example.com",
                       "name", "User", "picture", "...",
                       "userId", UUID.randomUUID().toString()),
                "sub"  // nameAttributeKey
        );

        mockMvc.perform(get("/auth/callback").with(oauth2Login().oauth2User(user)))
                .andExpect(status().isOk());
    }
}
```

`oauth2Login().oauth2User(user)` — 실제 OAuth2 플로우 없이 인증 완료 상태를 시뮬레이션한다.
실제 Google/GitHub 서버 호출 없이 Security 필터를 통과하므로 CI 환경에서도 동작한다.
