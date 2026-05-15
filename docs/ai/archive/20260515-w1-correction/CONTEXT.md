# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- **모듈 구조**: auth / user / notification / admin / shared (D-005)
  - billing, audit 모듈 제거
  - user, admin 모듈 신규 추가
- **Refresh Token 저장**: DB(`refresh_tokens` 테이블, token_hash SHA-256) + Redis 캐시 병행 (D-006)
  - raw token은 DB/Redis 어디에도 저장 금지
  - device_fingerprint, ip_address, expires_at DB 저장 (감사 필드)
- **Refresh Token active 정책**: 사용자당 1개 active token (D-009, D-010)
  - save() 시 deleteAllByUserId() 먼저 실행 후 신규 저장
  - DB 레벨에서 `refresh_tokens(user_id)` unique index로 단일 active token 강제
  - device_fingerprint는 멀티-디바이스 구분용 아님 — 감사 추적 전용
- **OAuth access_token_enc**: OAuthIdentity 엔티티에 추가, FieldEncryptor(AES-256-GCM) 사용 (D-007)
  - 기존 identity 재로그인 시에도 access_token_enc 갱신
  - 새 access token이 null이면 기존 access_token_enc를 덮어쓰지 않음
- **MFA 테이블**: `totp_credentials` → `mfa_credentials` (D-008)
  - 컬럼: type VARCHAR(20) DEFAULT 'totp', secret_enc TEXT, is_active BOOLEAN, verified_at TIMESTAMPTZ
  - secret_iv 컬럼 제거 (FieldEncryptor {iv}:{cipher} 포맷 유지)
- **API 경로**: /api/v1/auth/... 유지 (new WORKFLOW 문서 표기 오기)
- **JWT 서명**: RS256, jjwt 0.12.6
- **Testcontainers**: Docker Desktop 4.66.1 / Docker Engine 29.3.1 환경에서 Testcontainers 1.21.4 사용
  - `disabledWithoutDocker` 사용 금지
  - Windows Codex 실행 시 Docker Desktop Linux engine pipe 명시 필요:
    `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`

## 현재 미결 사항

- W1 Step 1~3 구현 보정 및 리뷰 후속 수정 완료
- 현재 알려진 코드 미결 사항 없음

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token raw 원문 저장 금지 (DB token_hash + Redis 캐시)
- 사용자당 Refresh Token 1개 active (D-009, D-010)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- Flyway 버전: V19까지 기존 사용, 신규는 V20부터

## 검증 상태

- `.\gradlew.bat compileJava compileTestJava` — 통과
- `.\gradlew.bat test --tests "com.synapse.platform.auth.jwt.RefreshTokenServiceTest"` — 통과
- `.\gradlew.bat test --tests "com.synapse.platform.auth.oauth.CustomOAuth2UserServiceTest" --tests "com.synapse.platform.auth.oauth.OAuthSignupRollbackIntegrationTest"` — 통과
- `.\gradlew.bat test --tests "*ModuleStructureTest"` — 통과
- `.\gradlew.bat checkstyleMain checkstyleTest spotbugsMain spotbugsTest` — 통과
- `.\gradlew.bat build` — 통과

## 참고할 공식 문서

- docs/new_md/WORKFLOW_platform_W1.md — 새 workflow 기준 (최우선)
- docs/ai/decisions/DECISION_LOG.md — D-005 ~ D-010
- docs/rules/06-auth-token.md
- docs/rules/07-platform-spring.md
