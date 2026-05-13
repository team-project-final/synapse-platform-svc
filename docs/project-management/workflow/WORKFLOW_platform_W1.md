# WORKFLOW: @platform-owner — Week 1

> **Task 문서**: [TASK_platform.md](../task/TASK_platform.md)  
> **기간**: 2026-05-12 ~ 2026-05-16  
> **기능개발 Workflow**: [README §7](../README.md)

---

## Step 1: platform-svc 골격 생성

### 1.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W1 해당 요구사항 확인 (프로젝트 골격)
- [x] Duration 산정 확인 (1일)

### 1.2 요구사항 분석
- [x] Spring Boot 4 + Modulith 프로젝트 구조 분석
- [x] auth/billing/notification/audit 4개 모듈 역할 정의
- [x] Gradle 의존성 목록 도출
- [x] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [x] 인증 필요 여부: No (골격만 생성)
- [x] 권한 종류: 없음
- [x] 공개 API 여부: No (Health endpoint만)
- [x] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [x] 골격 단계 — ERD 해당 없음 (DB 마이그레이션 Out of Scope)
- [x] 모듈별 패키지 구조도 작성
- [x] Duration(final) 갱신

### 1.5 Security 2차 검토
- [x] 민감 정보 암호화: 비해당 (골격 단계)
- [x] Soft Delete 정책: 비해당
- [x] 행 단위 접근 제어: 불필요
- [x] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [x] 골격 단계 — 빈 Controller/Service 클래스만 생성
- [x] 각 모듈 package-info.java 작성
- [x] Output Format → TASK 반영

### 1.7 Repository 구현
- [x] 골격 단계 — Repository 해당 없음
- [x] ApplicationModulesTest 구조 검증 테스트 작성

### 1.8 Service + Test
- [x] 빈 Service 클래스 생성 (4개 모듈)
- [x] ApplicationModulesTest 통과 확인
- [x] `./gradlew build` 성공 확인

### 1.9 Controller + Test
- [x] 빈 Controller 클래스 생성 (4개 모듈)
- [x] Dockerfile 작성 (multi-stage build)
- [x] Docker 이미지 빌드 성공 확인

### 1.10 View + Test (해당 시)
- [x] Flutter 화면 연동: 해당 없음
- [x] docker compose에서 platform-svc 실행 확인
- [x] RULE Reference → TASK 반영

**Step 1 Status**: ✅ Done (2026-05-13)

---

## Step 2: OAuth 회원가입/로그인

### 1.1 TASK 시작
- [x] Step Goal / Done When / Scope / Input 확인
- [x] PRD_W1 해당 요구사항 확인 (FR-PL-001, FR-PL-002)
- [x] Duration 산정 확인 (2일)

### 1.2 요구사항 분석
- [x] Google/GitHub OAuth 연동 플로우 분석
- [x] 신규 사용자 자동 회원가입 로직 설계
- [x] 기존 사용자 매핑 로직 (email 기준) 설계
- [x] Instructions 초안 → TASK 문서 반영

### 1.3 Security 1차 검토
- [x] 인증 필요 여부: No (인증 생성 API 자체)
- [x] 권한 종류: 없음 (공개 접근)
- [x] 공개 API 여부: Yes (OAuth 콜백 엔드포인트)
- [x] OAuth state 파라미터 CSRF 방어 필수
- [x] 결과 → TASK Constraints 반영

### 1.4 ERD 설계
- [ ] users 테이블 설계 (id UUID, email, name, avatar_url, provider, provider_id, role, status, created_at, updated_at, deleted_at)
- [ ] 인덱스 설계 (email UNIQUE, provider+provider_id UNIQUE)
- [ ] ~~oauth_accounts 테이블~~ — 샘플링 결과 users 테이블에 통합 (provider, provider_id 컬럼)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 민감 정보 암호화: OAuth Provider access_token 서버에 저장 안 함 (최소 수집 원칙)
- [ ] Soft Delete 정책: 논리삭제 (deleted_at) — Step 8 개인정보 마스킹 연계
- [ ] 행 단위 접근 제어: 불필요 (OAuth 콜백은 시스템 처리)
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] User Entity 작성 (UUID PK, 정적 팩토리 메서드, @Setter 금지)
- [ ] OAuthAttributes record 작성 (provider별 attribute 추출)
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] UserRepository 인터페이스 작성 (package-private)
- [ ] findByEmail, findByProviderAndProviderId 쿼리 메서드

### 1.8 Service + Test
- [ ] CustomOAuth2UserService 구현 (사용자 조회/생성, delegate 패턴)
- [ ] 신규 사용자 자동 회원가입 로직 구현
- [ ] 기존 사용자 매핑 로직 구현 (email 우선)
- [ ] 단위 테스트 작성 (Mockito)
- [ ] 테스트 통과 확인

### 1.9 Controller + Test
- [ ] HttpCookieOAuth2AuthorizationRequestRepository 구현 (Jackson 직렬화)
- [ ] SecurityConfig 구현 (STATELESS + OAuth2 + 쿠키 저장소)
- [ ] OAuth2AuthenticationSuccessHandler 구현 (userId redirect)
- [ ] OAuth2AuthenticationFailureHandler 구현
- [ ] 통합 테스트 작성 (@SpringBootTest + oauth2Login())
- [ ] 테스트 통과 확인

### 1.10 View + Test (해당 시)
- [ ] Flutter 화면 연동: Step 2에서는 해당 없음 (프론트 별도)
- [ ] RULE Reference → TASK 반영

**Step 2 Status**: 🔄 In Progress (2026-05-13~)

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
- [ ] refresh_tokens 관리: Redis 저장 (key: userId, TTL: 7d)
- [ ] mfa_secrets 테이블 설계 (user_id, secret_enc, enabled, created_at)
- [ ] 인덱스 설계 (user_id UNIQUE on mfa_secrets)
- [ ] Duration(final) 갱신

### 1.5 Security 2차 검토
- [ ] 민감 정보 암호화: TOTP secret AES-256 암호화 저장
- [ ] Refresh Token: Redis만 저장 (DB 저장 X)
- [ ] 행 단위 접근 제어: 필요 (본인 MFA만 관리)
- [ ] 결과 → TASK Constraints 반영

### 1.6 DTO / Entity 설계 (API First)
- [ ] TokenRefreshRequest 정의 (refresh_token)
- [ ] TokenResponse 정의 (access_token, refresh_token, expires_in)
- [ ] MfaSetupResponse 정의 (qr_code_url, secret)
- [ ] MfaVerifyRequest 정의 (code)
- [ ] MfaSecret Entity 작성
- [ ] Output Format → TASK 반영

### 1.7 Repository 구현
- [ ] MfaSecretRepository 인터페이스 작성
- [ ] Redis Template 설정 (Refresh Token 저장/조회/삭제)

### 1.8 Service + Test
- [ ] JwtService 구현 (생성, 파싱, 검증 — RS256)
- [ ] RefreshTokenService 구현 (Redis CRUD)
- [ ] MfaService 구현 (TOTP 생성, QR URL, 검증)
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
