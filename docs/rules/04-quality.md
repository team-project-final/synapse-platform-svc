# 4. 품질 RULE — Quality

> Synapse 프로젝트 품질 게이트. 이 룰을 통과 못 하면 머지 안 됨.

---

## 4.1 테스트 구조 [MUST]

모든 테스트는 **Given-When-Then** 패턴으로 작성해.
네이밍은 BDD 스타일: `메서드명_상황_should기대결과`

### ✅ Good

```java
@DisplayName("createUser_중복이메일_shouldThrowDuplicateException")
@Test
void createUser_중복이메일_shouldThrowDuplicateException() {
    // Given
    UserCreateRequest request = new UserCreateRequest("dup@syn.com", "pass123");
    given(userRepository.existsByEmail("dup@syn.com")).willReturn(true);

    // When & Then
    assertThatThrownBy(() -> userService.createUser(request))
        .isInstanceOf(DuplicateEmailException.class);
}
```

### ❌ Bad

```java
@Test
void test1() {
    // 뭘 테스트하는지 모름, 구조도 없음
    User u = userService.createUser(new UserCreateRequest("a@b.com", "pw"));
    assertNotNull(u);
}
```

> **이유**: Given-When-Then은 테스트가 문서 역할을 하게 만들어. 이름만 보고 뭘 검증하는지 알 수 있어야 해.

---

## 4.2 커버리지 [SHOULD] / [MAY]

| 지표 | 목표 | 레벨 |
|------|------|------|
| Line Coverage | 70% 이상 | [SHOULD] |
| Branch Coverage | 60% 이상 | [MAY] |

### Jacoco 설정 예시 (build.gradle.kts)

```kotlin
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}
```

> **이유**: 커버리지 자체가 목적은 아니지만, 최소한의 안전망은 필요해. 70/60은 현실적으로 달성 가능하면서 의미 있는 숫자야.

---

## 4.3 코드 리뷰 [SHOULD]

| 항목 | 기준 |
|------|------|
| PR 크기 | 400줄 이하 |
| 리뷰 시작 | PR 생성 후 24h 이내 |
| 머지 완료 | 리뷰 승인 후 48h 이내 |

### ✅ Good

- PR 1건 = 1개 기능 or 1개 버그픽스
- 리뷰어가 24시간 내 코멘트 달고, 수정 후 바로 머지

### ❌ Bad

- PR 1건에 파일 40개, 라인 1200줄 변경
- 리뷰 요청 후 3일간 방치

> **이유**: PR이 커지면 리뷰 품질이 급격히 떨어져. 작게 자주 머지하는 게 전체 속도를 높여.

---

## 4.4 정적 분석 [MUST]

| 스택 | 도구 | 기준 |
|------|------|------|
| Spring | Checkstyle + SpotBugs | warning 0건 |
| Flutter | `flutter analyze` | issue 0건 |
| Python | ruff + mypy | error 0건 |

### ✅ Good

```bash
# Spring — CI에서 자동 실행
./gradlew checkstyleMain spotbugsMain  # 0 violations

# Flutter
flutter analyze  # No issues found!

# Python
ruff check . && mypy .  # All passed
```

### ❌ Bad

```bash
# "나중에 고치지~" 하면서 warning 무시
./gradlew checkstyleMain
# 12 violations found — 무시하고 머지
```

> **이유**: 정적 분석 경고를 하나라도 허용하면 깨진 유리창 효과로 걷잡을 수 없이 늘어나. 0건이 유일한 정답이야.

---

## 4.5 테스트 종류 [MUST]

| 종류 | 범위 | 실행 환경 | 속도 |
|------|------|-----------|------|
| Unit | 모듈 내 단일 클래스/함수 | Mock 의존성 | 빠름 (ms) |
| Integration | 모듈 간 연동, DB 포함 | Testcontainers/H2 | 중간 (s) |
| E2E | API 시나리오 전체 흐름 | 실제 서버 기동 | 느림 (10s+) |

### Unit 예시

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orderRepository;
    @InjectMocks OrderService orderService;

    @Test
    void calculateTotal_할인적용_should정가에서10프로할인() {
        // Given
        Order order = Order.of(10000, DiscountPolicy.TEN_PERCENT);
        // When
        int total = orderService.calculateTotal(order);
        // Then
        assertThat(total).isEqualTo(9000);
    }
}
```

### Integration 예시

```java
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {
    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Autowired UserRepository userRepository;

    @Test
    void save_정상유저_shouldDB에저장() {
        User user = User.of("test@syn.com", "닉네임");
        User saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();
    }
}
```

### E2E 예시

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class AuthE2ETest {
    @Autowired TestRestTemplate rest;

    @Test
    void 회원가입_로그인_프로필조회_시나리오() {
        // 1. 회원가입
        rest.postForEntity("/api/auth/signup", signupReq, Void.class);
        // 2. 로그인
        var loginRes = rest.postForEntity("/api/auth/login", loginReq, TokenResponse.class);
        String token = loginRes.getBody().accessToken();
        // 3. 프로필 조회
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var profile = rest.exchange("/api/users/me", GET, new HttpEntity<>(headers), UserProfile.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

> **이유**: 테스트 피라미드 구조를 지켜야 해. Unit 많이, Integration 적당히, E2E 핵심만. 비율이 뒤집히면 CI가 느려지고 유지보수 지옥이야.
