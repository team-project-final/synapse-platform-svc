# TASK — W1 Step 1~3 구현 보정

> 출처: docs/new_md/WORKFLOW_platform_W1.md (팀리더 최신화 문서)

## 상태

- Phase: 구현 완료
- 담당 Agent: Worker
- 시작일: 2026-05-15
- 완료일: 2026-05-15

---

## Step Goal

팀리더가 제공한 최신 WORKFLOW를 기준으로 W1 Step 1~3 구현을 보정한다.
모듈 구조, OAuth 저장 모델, Refresh Token 저장 방식, MFA 테이블 구조를 변경하고
전체 테스트를 통과시킨다.

## Done When

- [x] auth / user / notification / admin / shared 5개 모듈 구조로 변경 (billing, audit 제거)
- [x] `ApplicationModulesTest` 통과
- [x] `oauth_identities.access_token_enc` 컬럼 추가 + FieldEncryptor 암호화 저장
- [x] `refresh_tokens` DB 테이블 생성 (token_hash, device_fingerprint, ip_address, expires_at)
- [x] RefreshTokenService: DB 저장(token_hash) + Redis 캐시 병행 구현
- [x] `mfa_credentials` 테이블로 이관 (totp_credentials 대체)
- [x] MfaCredential Entity/Repository/Service 이름 정리
- [x] `./gradlew build` 성공
- [x] `./gradlew test` 전체 통과

## Scope

- Completed:
  - user, admin 모듈 패키지 + package-info.java + 빈 Controller/Service 추가
  - billing, audit 모듈 패키지 제거
  - OAuthIdentity 엔티티 access_token_enc 필드 추가 + Flyway 마이그레이션
  - OAuth 성공 핸들러에서 access_token 암호화 저장 로직 추가
  - 기존 OAuth identity 재로그인 시 access_token_enc 갱신
  - refresh_tokens Flyway 마이그레이션 + Entity + Repository 신규 생성
  - RefreshTokenService DB+Redis 병행 로직으로 수정
  - 사용자당 1개 active refresh token 정책 구현
  - `refresh_tokens(user_id)` unique index 추가
  - totp_credentials → mfa_credentials Flyway 마이그레이션
  - TotpCredential → MfaCredential Entity/Repository/Service 이름 수정
  - RefreshTokenServiceTest를 Testcontainers 통합 테스트로 전환
  - Docker 29 호환을 위해 Testcontainers 1.21.4 적용
- Out of Scope:
  - project-management/ 공식 문서 교체
  - W2 이후 기능 구현
  - 로그아웃/Token Blacklist (W2 이월 유지)

## Input

- docs/new_md/WORKFLOW_platform_W1.md — 새 기준
- docs/ai/current/CONTEXT.md — 확정 결정 (D-005~D-010)
- docs/ai/current/HANDOFF.md — 구현 상세 명세

## Output

- 수정/신규 파일 목록
- 실행한 검증 명령어 + 결과
- Done When 항목 체크리스트

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

## Constraints

- Flyway: V20부터 사용 (V19까지 기존 사용)
- MFA 테이블: dev 환경 기준 DROP+재생성 허용
- token_hash: SHA-256(raw_token), HEX 인코딩 저장
- billing/audit 패키지 디렉토리 물리적 삭제 완료
- Windows Codex 환경에서 Testcontainers 실행 시 필요:

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
```

## Assignee / Reviewer

- Assignee: Worker (Codex)
- Reviewer: Director (Claude)
