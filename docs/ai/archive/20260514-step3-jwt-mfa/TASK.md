# TASK — Step 3: JWT + MFA 기초

> 출처: TASK_platform.md Step 3

## 상태

- Phase: 구현 완료
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-14
- 목표 완료일: 2026-05-16

---

## Step Goal

인증된 사용자에게 JWT Access/Refresh Token을 발급하고, MFA(TOTP) 등록 기초가 동작한다.

## Done When

- [x] OAuth 로그인 성공 시 JWT Access Token (15분) 발급
- [x] Refresh Token (7일) 발급 + Redis 저장
- [x] Access Token 만료 시 Refresh Token으로 갱신 (`POST /api/v1/auth/refresh`)
- [x] MFA(TOTP) 시크릿 생성 + QR 코드 URL 반환 (`POST /api/v1/auth/mfa/setup`)
- [x] TOTP 코드 검증 API 동작 (`POST /api/v1/auth/mfa/verify`)
- [x] Security Filter에 JWT 검증 추가
- [x] 단위/통합 테스트 통과

## Scope

- In Scope:
  - JWT Access/Refresh Token 발급 로직
  - Refresh Token Redis 저장/조회/삭제
  - Token 갱신 API (`POST /api/v1/auth/refresh`)
  - TOTP 시크릿 생성 (`POST /api/v1/auth/mfa/setup`)
  - TOTP 검증 API (`POST /api/v1/auth/mfa/verify`)
  - Security Filter JWT 검증
  - 단위/통합 테스트
- Out of Scope:
  - MFA 강제 적용 정책
  - Token 블랙리스트 (W2)
  - SMS/이메일 MFA

## Input

JWT 라이브러리 (jjwt), TOTP 라이브러리 (GoogleAuth), Redis 접속 정보

## Instructions

1. JWT 유틸리티 클래스 구현 (생성, 파싱, 검증)
2. Access Token 발급 로직 (claims: userId, roles, exp=15min)
3. Refresh Token 발급 + Redis 저장 (key: userId, TTL: 7d)
4. Token 갱신 엔드포인트 구현 (`POST /api/v1/auth/refresh`)
5. TOTP 시크릿 생성 + QR 코드 URL 생성 API
6. TOTP 코드 검증 API 구현
7. Security Filter에 JWT 검증 추가
8. 단위 테스트 + 통합 테스트 작성

## Output Format

auth 모듈 JWT/MFA 코드 + Redis 설정 + 테스트 코드

## Constraints

- Access Token: 15분, Refresh Token: 7일
- Refresh Token은 Redis에만 저장 (DB 저장 X)
- TOTP는 RFC 6238 준수
- JWT 서명: RS256
- URI: `/api/v1/` prefix 필수

## Duration

2일

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
