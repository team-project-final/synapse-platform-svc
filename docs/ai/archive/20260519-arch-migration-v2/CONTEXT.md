# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

### 아키텍처 결정
- **D-017** (2026-05-19): ARCHITECTURE_v2.md 기준으로 Spring Modulith 단일 앱으로 복원 (D-013 번복)
- 패키지 루트: `io.synapse.platform` (변경 없음)
- 단일 Spring Boot 앱, 단일 Dockerfile, 단일 docker-compose `platform-svc` 서비스

### 현재 코드 상태 (commit #15 이후)
- **실제 코드 있는 곳**: `auth-service/src/main/java/io/synapse/platform/auth/` (40+ 클래스)
- **Placeholder만 있는 곳**: `billing-service/`, `audit-service/`, `notification-service/`
- **공통 유틸**: `platform-common/src/main/java/io/synapse/platform/common/` (3개 파일)
- **Flyway 마이그레이션**: `auth-service/src/main/resources/db/migration/V1~V23`

### Dockerfile/docker-compose 상태
- 루트 `Dockerfile`: 이미 단일 앱(`src/` 기준) — **수정 불필요**
- `docker-compose.yml`: 이미 `platform-svc` 단일 서비스
- 단, Dockerfile은 포트 `8081` EXPOSE — application.yml 포트와 일치
- 리뷰 후속: `platform-svc`가 DB/Redis env만 주입하고 OAuth/JWT/AES 필수 env 주입 경로가 없음. `.env.example` + `env_file: .env` 보정 필요.

### 패키지 이동 매핑
| 현재 위치 | 이동 후 위치 | 비고 |
|-----------|-------------|------|
| `auth-service/src/main/java/io/synapse/platform/auth/` (user 제외) | `src/main/java/io/synapse/platform/auth/` | 패키지명 동일 |
| `auth-service/src/main/java/io/synapse/platform/auth/user/` | `src/main/java/io/synapse/platform/user/` | 패키지 변경 |
| `platform-common/src/.../common/` | `src/main/java/io/synapse/platform/shared/` | `common` → `shared` rename |
| `audit-service` AuditPlaceholder | `src/main/java/io/synapse/platform/admin/` | placeholder 유지 |
| `notification-service` NotificationPlaceholder | `src/main/java/io/synapse/platform/notification/` | placeholder 유지 |

### 크로스모듈 의존성 (auth → user)
- `auth.oauth.CustomOAuth2UserService` — UserService(user 모듈) 직접 참조 → `UserApi`로 교체
- `auth.oauth.CustomOidcUserService` — 동일
- `auth.oauth.OAuthUserResolver` — 동일
- `auth.oauth.OAuth2SuccessHandler` — 동일
- `UserApi` 인터페이스를 `user.api` 패키지에 생성, `UserService`가 구현

### Spring Modulith 의존성
- `org.springframework.modulith:spring-modulith-starter-core` 추가 필요
- `org.springframework.modulith:spring-modulith-starter-test` (test scope) 추가 필요
- BOM: `spring-modulith-bom:2.0.6` (Boot 4.0 호환 확정 버전 — D-018)

### 주요 파일의 현재 의존성 (build.gradle.kts 합산)
- Spring Boot Web, JPA, Redis, Security, OAuth2 Client, Validation, Actuator
- Flyway Core + PostgreSQL Adapter
- jjwt 0.12.6 (api + impl + jackson)
- totp 1.7.1
- uuid-creator 5.3.3
- logstash-logback-encoder 7.4
- Test: spring-boot-test, spring-security-test, testcontainers 1.21.4, H2

## 현재 미결 사항

- `SlugGenerator`(auth.util) 위치: `auth.util` 유지 (auth 모듈 내부에서만 사용, 이동 불필요)
- `docker compose up` 정상 기동 조건 보정 필요: `.env.example`, `.gitignore`의 `.env` 제외, `docker-compose.yml`의 `env_file` 연결.

## 확정된 UserApi 설계 (D-019)

```
user.api.UserInfo          — record(id, email, displayName, defaultTenantId)
user.api.OAuthUserCreateCommand — record(email, slug, displayName, avatarUrl, defaultTenantId)
user.api.UserApi           — interface
  findById(UUID)           → Optional<UserInfo>
  findByEmail(String)      → Optional<UserInfo>
  createForOAuth(OAuthUserCreateCommand) → UserInfo
```

- `UserService` implements `UserApi`
- `user.api` 패키지에 `@NamedInterface("api")` 필수
- `User` 엔티티 외부 노출 금지 — 모든 외부 접근은 `UserInfo` DTO로

## 활성 제약

- 기존 테스트 68건 이상 전체 통과 필수
- `@ApplicationModule` + ModuleStructureTest 통과 필수
- 빌드 완료 후 `auth-service/`, `billing-service/`, `audit-service/`, `notification-service/`, `platform-common/` 디렉토리 제거
- secret 원문 커밋 금지. `.env.example`에는 placeholder 또는 테스트용 공개 값만 허용.

## 참고 문서

- docs/synapse-platform-svc_ARCHITECTURE_v2.md (기준 문서)
- docs/ai/decisions/DECISION_LOG.md (D-013, D-017)
