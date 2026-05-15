# HANDOFF

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Worker (Codex)

## TO

Director (Claude)

## 상태

W1 Step 1~3 구현 보정 및 리뷰 후속 수정 완료.

---

## 완료한 요청

W1 Step 1~3 구현 리뷰에서 발견된 버그 3건을 수정했다.

1. RefreshTokenService 단일 active token 정책 보정
2. OAuth 기존 identity 재로그인 시 access_token_enc 갱신
3. RefreshTokenServiceTest를 Testcontainers 통합 테스트로 전환

추가 리뷰에서 발견된 동시성 보장 이슈도 함께 처리했다.

---

## 주요 변경 사항

### FIX 1: RefreshTokenService 단일 active token 정책

- `save(UUID, String, String, String)`에서 `repository.deleteAllByUserId(userId)`를 먼저 실행하도록 수정
- DB 저장과 Redis 캐시 저장을 분리하는 `store(...)` helper 추가
- `rotate()`가 `save()`를 다시 호출하지 않고 `delete(userId)` 후 직접 저장하도록 수정
- Redis write/delete는 DB transaction commit 이후 실행되도록 `TransactionSynchronization` 적용
- DB fallback 검증을 `token_hash` 단독이 아니라 `userId + tokenHash + expiresAt` 기준으로 강화

### FIX 2: OAuthIdentity access_token_enc 갱신

- `OAuthIdentity.updateAccessTokenEnc(String accessTokenEnc)` 추가
- null access token은 기존 암호화 토큰을 덮어쓰지 않도록 보호
- `CustomOAuth2UserService.resolveUser()`의 기존 identity path에서 access_token_enc 갱신 후 저장

### FIX 3: RefreshTokenServiceTest 통합 테스트 전환

- Mockito 단위 테스트 제거
- `@SpringBootTest + @Testcontainers` 기반 통합 테스트로 전환
- PostgreSQL은 `pgvector/pgvector:pg16` 사용
- Redis는 `redis:7` 사용
- Flyway V1~V23 migration을 테스트에서 명시 실행
- 실제 users row를 생성해 `refresh_tokens.user_id` FK를 검증
- 검증 범위:
  - DB + Redis hash 저장
  - 두 번째 save 시 기존 token 무효화
  - Redis cache miss 시 DB fallback
  - rotate 시 기존 token 대체
  - delete 시 DB + Redis 제거

### 추가 리뷰 반영

- `refresh_tokens(user_id)` unique index 추가
- 기존 중복 refresh token row가 있을 경우 최신 row만 남기고 삭제하는 V23 migration 추가
- Docker 29 / Testcontainers 호환성 문제로 Testcontainers `1.19.8` → `1.21.4` 업데이트

---

## 수정 파일 목록

- `build.gradle.kts`
- `src/main/java/com/synapse/platform/auth/domain/OAuthIdentity.java`
- `src/main/java/com/synapse/platform/auth/jwt/RefreshTokenRepository.java`
- `src/main/java/com/synapse/platform/auth/jwt/RefreshTokenService.java`
- `src/main/java/com/synapse/platform/auth/oauth/CustomOAuth2UserService.java`
- `src/test/java/com/synapse/platform/auth/jwt/RefreshTokenServiceTest.java`
- `src/main/resources/db/migration/V23__add_refresh_tokens_user_unique.sql`

기존 Step 1~3 보정 작업에서 이미 반영된 파일:

- user/admin 모듈 추가 파일
- billing/audit 모듈 제거 파일
- `V20__add_oauth_identity_access_token.sql`
- `V21__create_refresh_tokens.sql`
- `V22__migrate_totp_to_mfa_credentials.sql`
- MfaCredential 관련 파일
- OAuth/MFA/RefreshToken 관련 테스트 파일

---

## 검증 결과

```powershell
.\gradlew.bat compileJava compileTestJava
```

Result: BUILD SUCCESSFUL

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.RefreshTokenServiceTest"
```

Result: BUILD SUCCESSFUL

```powershell
.\gradlew.bat test --tests "com.synapse.platform.auth.oauth.CustomOAuth2UserServiceTest" --tests "com.synapse.platform.auth.oauth.OAuthSignupRollbackIntegrationTest"
```

Result: BUILD SUCCESSFUL

```powershell
.\gradlew.bat test --tests "*ModuleStructureTest"
```

Result: BUILD SUCCESSFUL

```powershell
.\gradlew.bat checkstyleMain checkstyleTest spotbugsMain spotbugsTest
```

Result: BUILD SUCCESSFUL

```powershell
.\gradlew.bat build
```

Result: BUILD SUCCESSFUL

---

## Testcontainers 실행 조건

Windows Codex 환경에서는 Docker Desktop Linux engine pipe를 명시해야 한다.

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
```

이 설정 없이 실행하면 Codex 프로세스가 기본 `docker_engine` pipe 또는 Docker context 파일 접근 문제로 Testcontainers 초기화에 실패할 수 있다.

---

## Done When

- [x] RefreshTokenService.save() — deleteAllByUserId() 호출 후 신규 저장
- [x] rotate() — 중복 삭제 없이 단일 active token 보장
- [x] DB-level unique index로 사용자당 1개 active refresh token 보장
- [x] Redis write/delete after commit 처리
- [x] OAuthIdentity.updateAccessTokenEnc() 메서드 추가
- [x] CustomOAuth2UserService — 기존 identity path에서 access_token_enc 갱신 후 저장
- [x] RefreshTokenServiceTest — @SpringBootTest + @Testcontainers (disabledWithoutDocker 없음)
- [x] ./gradlew test 전체 통과
- [x] ./gradlew build 통과

## 기한

2026-05-16
