# WORKFLOW: @platform-owner — Week 1

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)
> **기간**: 2026-05-12 ~ 2026-05-15, 4 영업일
> **기능개발 Workflow**: [README §7](../README.md)

---

## Step 1: platform-svc 골격 생성

### 1.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W1 해당 요구사항 확인 (프로젝트 골격)
- [ ] Duration 산정 확인 (1일)

### 1.2 요구사항 분석
- [ ] Spring Boot 4 + Modulith 프로젝트 구조 분석
- [ ] auth/audit/billing/notification 4개 모듈 역할 정의
- [ ] Gradle 의존성 목록 도출
- [ ] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [ ] 인증 필요 여부: No (골격만 생성)
- [ ] 권한 종류: 없음
- [ ] 공개 API 여부: No (Health endpoint만)
- [ ] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] 골격 단계 — ERD 해당 없음 (DB 마이그레이션 Out of Scope)
- [ ] 모듈별 패키지 구조도 작성
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 민감 정보 암호화: 비해당 (골격 단계)
- [ ] Soft Delete 정책: 비해당
- [ ] 행 단위 접근 제어: 불필요
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] 골격 단계 — 빈 Controller/Service 클래스만 생성
- [ ] 각 모듈 package-info.java 작성
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] 골격 단계 — Repository 해당 없음
- [ ] ApplicationModulesTest 구조 검증 테스트 작성

### 1.8 Service + Test
- [ ] 빈 Service 클래스 생성 (4개 모듈)
- [ ] ApplicationModulesTest 통과 확인
- [ ] `./gradlew build` 성공 확인

### 1.9 Controller + Test
- [ ] 빈 Controller 클래스 생성 (4개 모듈)
- [ ] Dockerfile 작성 (multi-stage build)
- [ ] Docker 이미지 빌드 성공 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음
- [ ] docker compose에서 platform-svc 실행 확인
- [ ] RULE Reference → TASK 반영

**Step 1 Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 2: OAuth 회원가입/로그인

### 1.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W1 해당 요구사항 확인 (FR-AU-xxx OAuth 인증)
- [ ] Duration 산정 확인 (2일)

### 1.2 요구사항 분석
- [ ] Google/GitHub/Apple OAuth 연동 플로우 분석 (Microsoft는 확장 Provider로 문서화)
- [ ] 신규 사용자 자동 회원가입 로직 설계
- [ ] 기존 사용자 매핑 로직 (email 기준) 설계
- [ ] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [ ] 인증 필요 여부: No (인증 생성 API 자체)
- [ ] 권한 종류: 없음 (공개 접근)
- [ ] 공개 API 여부: Yes (OAuth 콜백 엔드포인트)
- [ ] OAuth state 파라미터 CSRF 방어 필수
- [ ] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] users 테이블 설계 (id, tenant_id, email, display_name, avatar_url, locale, status: active|suspended|deleted, created_at, updated_at)
- [ ] 참고: provider, provider_id 컬럼은 users 테이블에 없음 — oauth_identities 테이블로 분리
- [ ] oauth_identities 테이블 설계 (id, user_id, provider, provider_id, access_token_enc)
- [ ] 인덱스 설계 (email UNIQUE, provider+provider_id UNIQUE)
- [ ] 관계 정의 (users 1:N oauth_identities)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 민감 정보 암호화: OAuth access_token 암호화 저장
- [ ] Soft Delete 정책: 논리삭제 (deleted_at)
- [ ] 행 단위 접근 제어: 불필요 (OAuth 콜백은 시스템 처리)
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] OAuthCallbackRequest 정의 (code, state)
- [ ] OAuthTokenResponse 정의 (accessToken, refreshToken, expiresIn)
- [ ] User Entity 작성 (display_name, avatar_url, locale, status)
- [ ] OAuthIdentity Entity 작성
- [ ] MapStruct 매퍼 작성
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] UserRepository 인터페이스 작성
- [ ] OAuthIdentityRepository 인터페이스 작성
- [ ] findByEmail, findByProviderAndProviderId 커스텀 쿼리

### 1.8 Service + Test
- [ ] OAuth2UserService 구현 (사용자 조회/생성 로직)
- [ ] 신규 사용자 자동 회원가입 로직 구현
- [ ] 기존 사용자 매핑 로직 구현
- [ ] 단위 테스트 작성 (Mockito)
- [ ] 테스트 통과 확인

### 1.9 Controller + Test
- [ ] OAuth2 콜백 핸들러 구현
- [ ] OAuth2 성공 핸들러 구현 (JWT 발급 연계)
- [ ] 통합 테스트 작성 (MockOAuth2User)
- [ ] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: Step 2에서는 해당 없음 (프론트 별도)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 2 Status**: [ ] Not Started / [ ] In Progress / [ ] Done

---

## Step 3: JWT + MFA 기초

### 1.1 TASK 시작
- [ ] Step Goal / Done When / Scope / Input 확인
- [ ] PRD_W1 해당 요구사항 확인 (FR-AU-xxx JWT/MFA)
- [ ] Duration 산정 확인 (2일)

### 1.2 요구사항 분석
- [ ] JWT Access/Refresh Token 발급 플로우 분석
- [ ] Refresh Token Redis 저장 구조 설계
- [ ] TOTP(RFC 6238) MFA 플로우 분석
- [ ] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [ ] 인증 필요 여부: Yes (MFA 설정은 인증 필요)
- [ ] 권한 종류: 로그인 사용자
- [ ] 공개 API 여부: POST /auth/refresh는 공개, POST /auth/mfa/* 는 인증 필요
- [ ] JWT 서명 알고리즘: RS256
- [ ] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] refresh_tokens 테이블 설계 (id, user_id, token_hash, device_fingerprint, ip_address, expires_at, created_at) — ERD에 DB 테이블로 정의됨 (Redis 전용이 아님)
- [ ] mfa_credentials 테이블 설계 (id, user_id, type: totp, secret_enc, is_active, verified_at, created_at)
- [ ] 인덱스 설계 (user_id UNIQUE on mfa_credentials)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 민감 정보 암호화: TOTP secret AES-256-GCM 암호화 저장
- [ ] Refresh Token 이중 저장 전략: Redis(활성 세션 빠른 조회용) + DB `refresh_tokens` 테이블(영속성/감사용) 병행 — token_hash, device_fingerprint, ip_address, expires_at 포함
- [ ] 행 단위 접근 제어: 필요 (본인 MFA만 관리)
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] TokenRefreshRequest 정의 (refreshToken)
- [ ] TokenResponse 정의 (accessToken, refreshToken, expiresIn)
- [ ] MfaSetupResponse 정의 (qrCodeUrl, secret)
- [ ] MfaVerifyRequest 정의 (code)
- [ ] MfaCredential Entity 작성 (type, is_active, verified_at)
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] MfaCredentialRepository 인터페이스 작성
- [ ] RefreshTokenRepository 인터페이스 작성 (DB 저장 + Redis 캐싱 병행)

### 1.8 Service + Test
- [ ] JwtService 구현 (생성, 파싱, 검증 — RS256)
- [ ] RefreshTokenService 구현 (Redis CRUD)
- [ ] MfaService 구현 (TOTP 생성, QR URL, 검증 — mfa_credentials 기반)
- [ ] 단위 테스트 작성 (Mockito)
- [ ] 테스트 통과 확인

### 1.9 Controller + Test
- [ ] POST /auth/refresh 엔드포인트 구현
- [ ] POST /auth/mfa/setup 엔드포인트 구현
- [ ] POST /auth/mfa/verify 엔드포인트 구현
- [ ] Security Filter에 JWT 검증 추가
- [ ] 슬라이스 테스트 (@WebMvcTest)
- [ ] 401/403 응답 테스트
- [ ] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: 해당 없음 (프론트 별도)
- [ ] Swagger API 문서 확인
- [ ] RULE Reference → TASK 반영

**Step 3 Status**: [ ] Not Started / [ ] In Progress / [ ] Done
