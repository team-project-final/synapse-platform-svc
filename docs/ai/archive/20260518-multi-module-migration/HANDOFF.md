# HANDOFF — Step 4: 멀티모듈 아키텍처 마이그레이션

> **TO**: Worker (Codex)
> **FROM**: Director (Claude)
> **날짜**: 2026-05-19
> **브랜치**: feature/PLAT-000-multi-module-migration (dev 기준 신규 생성)
> **배경**: 팀 공식 아키텍처 문서(docs/synapse-platform-svc_ARCHITECTURE.md v1.0)가 Gradle 멀티모듈 구조를 요구함. 기존 Spring Modulith 단일 앱 구조를 전면 전환한다.

---

## 브랜치 준비

```bash
git checkout dev
git checkout -b feature/PLAT-000-multi-module-migration
```

> feature/PLAT-007-stripe-billing는 dev에 머지하지 않고 버려두면 된다.

---

## 목표 구조

```
synapse-platform-svc/
├── build.gradle.kts          ← 루트 컨벤션 설정만
├── settings.gradle.kts       ← 5개 모듈 선언
├── platform-common/          ← 공통 라이브러리 (Spring Boot 플러그인 없음)
├── auth-service/             ← 독립 Spring Boot 앱 (기존 auth/* + user/* 포팅)
├── billing-service/          ← 독립 Spring Boot 앱 (플레이스홀더)
├── audit-service/            ← 독립 Spring Boot 앱 (플레이스홀더)
└── notification-service/     ← 독립 Spring Boot 앱 (플레이스홀더)
```

---

## 구현 순서

### 1. settings.gradle.kts 교체

```kotlin
rootProject.name = "synapse-platform-svc"

include(
    ":platform-common",
    ":auth-service",
    ":billing-service",
    ":audit-service",
    ":notification-service"
)
```

---

### 2. 루트 build.gradle.kts 교체

기존 파일을 아래로 완전 교체:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.github.spotbugs") version "6.0.9" apply false
    checkstyle apply false
}

allprojects {
    group = "io.synapse"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "checkstyle")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.0")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    checkstyle {
        toolVersion = "10.12.5"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }
}
```

---

### 3. platform-common 모듈 생성

#### platform-common/build.gradle.kts

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

#### 이동할 클래스 (패키지 rename 포함)

| 현재 경로 | 새 경로 |
|-----------|---------|
| `com.synapse.platform.shared.exception.BusinessException` | `io.synapse.platform.common.exception.BusinessException` |
| `com.synapse.platform.shared.exception.GlobalExceptionHandler` | `io.synapse.platform.common.exception.GlobalExceptionHandler` |
| `com.synapse.platform.shared.crypto.FieldEncryptor` | `io.synapse.platform.common.crypto.FieldEncryptor` |
| `com.synapse.platform.shared.security.AuthenticatedUser` | `io.synapse.platform.common.security.AuthenticatedUser` |

`package-info.java` 파일은 이동하지 않는다.

#### 이동할 테스트

| 현재 경로 | 새 경로 |
|-----------|---------|
| `com.synapse.platform.shared.exception.GlobalExceptionHandlerTest` | `io.synapse.platform.common.exception.GlobalExceptionHandlerTest` |
| `com.synapse.platform.shared.crypto.FieldEncryptorTest` | `io.synapse.platform.common.crypto.FieldEncryptorTest` |

---

### 4. auth-service 모듈 생성

#### auth-service/build.gradle.kts

```kotlin
plugins {
    id("org.springframework.boot")
    id("com.github.spotbugs")
}

dependencies {
    implementation(project(":platform-common"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-core")
    implementation("com.github.f4b6a3:uuid-creator:5.3.3")
    implementation("dev.samstevens.totp:totp:1.7.1")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotbugs {
    toolVersion = "4.8.3"
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}
```

#### Application 진입점

`auth-service/src/main/java/io/synapse/platform/auth/AuthServiceApplication.java`:

```java
package io.synapse.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.synapse.platform")
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

> `scanBasePackages = "io.synapse.platform"` — platform-common의 Bean(GlobalExceptionHandler, FieldEncryptor 등)이 스캔되도록 필수 설정

#### 이동할 패키지 (com.synapse → io.synapse, 전체 rename)

| 현재 패키지 | 새 패키지 (auth-service) |
|------------|--------------------------|
| `com.synapse.platform.auth.*` | `io.synapse.platform.auth.*` |
| `com.synapse.platform.user.*` | `io.synapse.platform.auth.user.*` |

> user 모듈은 auth-service 내부로 통합 (아키텍처 문서 기준: "User 정보 관리는 Auth Service 내부")

**삭제 대상** (auth-service에 포팅하지 않음):
- `com.synapse.platform.admin.*` — 관리자 API는 각 담당 서비스(Step 9)에서 구현
- `com.synapse.platform.PlatformSvcApplication` — AuthServiceApplication으로 대체

#### resources 이동

- `src/main/resources/application.yml` → `auth-service/src/main/resources/application.yml`
  - **Stripe 관련 설정 블록 제거** (billing-service 담당)
- `src/main/resources/application-dev.yml` → `auth-service/src/main/resources/application-dev.yml`
  - **Stripe 관련 설정 블록 제거**
- `src/main/resources/db/migration/V1~V23` → `auth-service/src/main/resources/db/migration/` (전체 복사)

> **V24~V26은 이동하지 않는다.** billing-service가 Step 5에서 자체 V1~V3으로 시작함.

#### 이동할 테스트

아래 테스트를 `auth-service/src/test/java/io/synapse/platform/auth/` 하위 해당 패키지로 이동:

| 현재 경로 | 처리 |
|-----------|------|
| `PlatformSvcApplicationTests` | **삭제** (AuthServiceApplicationTests로 교체) |
| `auth/CorsConfigTest` | 이동 |
| `auth/HttpCookieOAuth2AuthorizationRequestRepositoryTest` | 이동 |
| `auth/OAuth2FailureHandlerTest` | 이동 |
| `auth/OAuth2LoginIntegrationTest` | 이동 |
| `auth/SlugGeneratorTest` | 이동 |
| `auth/jwt/JwtAuthenticationFilterTest` | 이동 |
| `auth/jwt/RefreshTokenTest` | 이동 |
| `auth/jwt/RefreshTokenServiceTest` | 이동 |
| `auth/jwt/JwtTokenProviderTest` | 이동 |
| `auth/mfa/MfaControllerTest` | 이동 |
| `auth/mfa/TotpServiceTest` | 이동 |
| `auth/OAuthAttributesTest` | 이동 |
| `auth/OAuthClientRegistrationTest` | 이동 |
| `auth/OAuth2SuccessHandlerTest` | 이동 |
| `auth/oauth/CustomOidcUserServiceTest` | 이동 |
| `auth/oauth/CustomOAuth2UserServiceTest` | 이동 |
| `auth/oauth/OAuthUserResolverTest` | 이동 |
| `auth/oauth/OAuthSignupRollbackIntegrationTest` | 이동 |
| `auth/AuthControllerTest` | 이동 |
| `auth/tenant/DefaultTenantProvisioningServiceTest` | 이동 |
| `ApplicationModulesTest` | **삭제** (Modulith 구조 해체) |

새 스모크 테스트 생성:
`auth-service/src/test/java/io/synapse/platform/auth/AuthServiceApplicationTests.java`

```java
package io.synapse.platform.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {}
}
```

**test resources 이동**:
- `src/test/resources/application.yml` → `auth-service/src/test/resources/application.yml`
  - **Stripe 관련 설정 블록 제거** (whsec_test, price_pro, price_team)
- `src/test/resources/application-test.yml` 존재 시 동일하게 이동

---

### 5. billing-service 모듈 생성 (플레이스홀더)

#### billing-service/build.gradle.kts

```kotlin
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

#### BillingServiceApplication.java

```java
package io.synapse.platform.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.synapse.platform")
public class BillingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
```

#### 플레이스홀더 컨트롤러

`billing-service/src/main/java/io/synapse/platform/billing/BillingPlaceholder.java`:

```java
package io.synapse.platform.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingPlaceholder {

    @GetMapping("/billing/health")
    public String health() {
        return "billing-service placeholder";
    }
}
```

#### billing-service/src/main/resources/application.yml

```yaml
spring:
  application:
    name: billing-service

server:
  port: 8082
```

---

### 6. audit-service 모듈 생성 (플레이스홀더)

#### audit-service/build.gradle.kts

billing-service와 동일한 구조.

#### AuditServiceApplication.java

```java
package io.synapse.platform.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.synapse.platform")
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
```

#### audit-service/src/main/resources/application.yml

```yaml
spring:
  application:
    name: audit-service

server:
  port: 8083
```

---

### 7. notification-service 모듈 생성 (플레이스홀더)

#### notification-service/build.gradle.kts

billing-service와 동일한 구조.

#### NotificationServiceApplication.java

```java
package io.synapse.platform.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.synapse.platform")
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

#### notification-service/src/main/resources/application.yml

```yaml
spring:
  application:
    name: notification-service

server:
  port: 8084
```

---

### 8. 기존 src/ 디렉토리 삭제

모든 모듈 이동 완료 후:

```bash
# src/ 전체 삭제 (루트 레벨)
rm -rf src/
```

삭제 전 반드시 확인:
- auth-service의 모든 main/test 소스 이동 완료 여부
- platform-common의 shared 소스 이동 완료 여부
- Flyway V1~V23 auth-service로 이동 완료 여부

---

## 완료 조건

- [ ] `./gradlew build` 성공 (루트 빌드 전체)
- [ ] `./gradlew :auth-service:test` 전체 통과
- [ ] `./gradlew :platform-common:test` 전체 통과
- [ ] `./gradlew :billing-service:build` 성공 (플레이스홀더)
- [ ] `./gradlew :audit-service:build` 성공 (플레이스홀더)
- [ ] `./gradlew :notification-service:build` 성공 (플레이스홀더)
- [ ] `src/` 디렉토리 루트에 없음 확인

---

## 주의사항

1. Spring Modulith 의존성 (`spring-modulith-*`) 모든 모듈에서 제거
2. `ApplicationModulesTest.java` 삭제 필수
3. `PlatformSvcApplication.java` 삭제 필수
4. 기존 코드의 `import com.synapse.platform.*` → `import io.synapse.platform.*` 전체 rename
5. `platform-common` 클래스를 import하는 auth 코드: `import io.synapse.platform.common.*`으로 업데이트
6. Flyway V24~V26는 이동하지 않음 (billing-service Step 5에서 재작성)
7. auth-service의 `application.yml`에서 Stripe 설정 블록 제거
8. `group = "com.synapse"` → `group = "io.synapse"` (루트 build.gradle.kts에서 변경)
