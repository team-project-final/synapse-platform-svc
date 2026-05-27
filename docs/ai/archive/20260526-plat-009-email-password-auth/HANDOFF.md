# HANDOFF

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Director (Claude)

## TO

Worker (Codex)

## DATE

2026-05-26

## SUBJECT

이메일/비밀번호 회원가입·로그인 구현 (PLAT-009)

---

## 배경

팀리더 요청으로 OAuth 외 이메일/비밀번호 인증을 추가한다.
이메일 인증(verification)은 Step 7 SES 연동 시점으로 미룬다.

**중요**: `users` 테이블과 `User` 엔티티에 필요한 필드가 이미 모두 존재한다.
Flyway 마이그레이션 신규 작성 불필요.

이미 존재하는 필드:
- `users.password_hash VARCHAR(255)` — nullable (OAuth 유저는 null)
- `users.email_verified_at TIMESTAMPTZ` — null이면 미인증
- `users.failed_login_count INTEGER DEFAULT 0`
- `users.locked_until TIMESTAMPTZ`

---

## 브랜치

`feature/PLAT-009-email-password-auth` (이미 생성됨, dev 기준)

---

## 구현 대상

### 신규 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/signup` | 이메일/비밀번호 회원가입 |
| POST | `/api/v1/auth/login` | 이메일/비밀번호 로그인 |

### SecurityConfig 변경

두 엔드포인트를 `permitAll()` 목록에 추가.

---

## 구현 명세

### 1. POST /api/v1/auth/signup

**Request body:**
```json
{ "email": "user@example.com", "password": "P@ssw0rd!" }
```

**검증:**
- email: `@Email`, `@NotBlank`
- password: `@NotBlank`, 최소 8자, 영문+숫자+특수문자 1개 이상 (`@Pattern`)

**처리 순서:**
1. 이메일 중복 확인 → 이미 존재하면 `409 Conflict` (`PLAT-xxx` 에러 코드)
2. `BCryptPasswordEncoder`로 비밀번호 해싱
3. `UserApi.createForEmailPassword(email, passwordHash, username)` 호출
   - username은 email의 `@` 앞 부분 사용 (예: `user@example.com` → `user`)
   - 중복 username은 뒤에 랜덤 4자리 숫자 붙이기 (예: `user1234`)
4. `201 Created` 반환 — body: `{ "userId": "uuid" }`

**이메일 인증:** 지금은 미구현. `email_verified_at`은 null로 둔다.

---

### 2. POST /api/v1/auth/login

**Request body:**
```json
{ "email": "user@example.com", "password": "P@ssw0rd!" }
```

**처리 순서:**
1. 이메일로 User 조회 → 없으면 `401` (계정 존재 여부 노출 금지)
2. `locked_until` 확인 → 현재 시각보다 미래면 `423 Locked` 반환
3. `passwordEncoder.matches(입력, user.passwordHash)` 검증
4. 실패 시:
   - `failed_login_count++`
   - 5회 이상이면 `locked_until = now + 15분` 설정
   - `401` 반환
5. 성공 시:
   - `failed_login_count = 0`, `locked_until = null`, `last_login_at = now` 초기화
   - Access Token + Refresh Token 발급 (기존 `JwtTokenProvider` 사용)
   - Refresh Token → HttpOnly Cookie (PLAT-008과 동일 방식)
   - `200 OK` — body: `{ "accessToken": "..." }`

**OAuth 계정 충돌 처리:**
- 같은 이메일로 OAuth 가입된 유저의 `password_hash`가 null인 경우
- `401` 반환 + 메시지: `"이 이메일은 소셜 로그인으로 가입되었습니다"`

---

### 3. UserApi 확장

`user/api/UserApi.java`에 메서드 추가:

```java
UserInfo createForEmailPassword(String email, String passwordHash, String username);
UserInfo findByEmail(String email);  // 이미 있으면 재사용
```

`UserApiImpl` (user 모듈 내부)에서 구현:
- `createForEmailPassword`: User 엔티티 생성 + UserSettings 생성 + Tenant + TenantMember 생성 (`createForOAuth`와 유사한 흐름)

---

### 4. PasswordEncoder Bean 등록

`SecurityConfig` 또는 별도 `PasswordConfig`에 추가:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

---

## 테스트 작성 필수

| 케이스 | 검증 포인트 |
|--------|-----------|
| 회원가입 성공 | 201, userId 반환, DB password_hash bcrypt 저장 확인 |
| 회원가입 — 이메일 중복 | 409 반환 |
| 회원가입 — 비밀번호 형식 불일치 | 400 반환 |
| 로그인 성공 | 200, accessToken 반환, Set-Cookie refresh_token 존재 |
| 로그인 — 잘못된 비밀번호 | 401 반환 |
| 로그인 — 5회 실패 후 계정 잠금 | 423 반환 |
| 로그인 — 잠긴 계정 | 423 반환 |
| 로그인 — OAuth 계정으로 시도 | 401 반환 |

---

## 제약 사항

- 비밀번호 평문 절대 저장/로깅 금지
- 로그인 실패 시 계정 존재 여부를 응답 메시지로 노출 금지 (단, OAuth 충돌은 예외)
- `BCryptPasswordEncoder` 외 다른 해싱 알고리즘 사용 금지
- 테스트 커버리지 80% 이상
- `./gradlew test` 전체 통과 확인

---

## 필요한 출력 형식

구현 완료 후 아래 항목 기록:

- [ ] 변경/추가된 파일 목록
- [ ] 추가된 테스트 케이스 목록
- [ ] `./gradlew test` 결과

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-26
