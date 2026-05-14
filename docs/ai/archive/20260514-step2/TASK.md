# TASK — Step 2: OAuth 회원가입/로그인

> 출처: TASK_platform.md Step 2

## 상태

- Phase: 구현 완료
- 담당 Agent: Director
- 시작일: 2026-05-13
- 목표 완료일: 2026-05-14

---

## Step Goal

사용자가 Google/GitHub OAuth를 통해 회원가입하고 로그인할 수 있다.

## Done When

- [x] Google OAuth 로그인 → 신규 사용자 자동 회원가입 (users + oauth_identities + tenants + tenant_members + user_settings 동시 생성)
- [x] GitHub OAuth 로그인 → 신규 사용자 자동 회원가입 (email null → placeholder 처리)
- [x] 기존 사용자 OAuth 로그인 시 기존 계정과 매핑 (Case A: 동일 provider 재로그인 / Case B: email 기준 계정 연결)
- [x] oauth_identities 분리 테이블에 provider/provider_user_id 저장 (users에 직접 저장 않음 — D-004)
- [x] ./gradlew build 성공 + 전체 테스트 통과 (20건 이상)

## Scope

- In Scope:
  - Spring Security OAuth2 Client 설정
  - Google OAuth 연동 (회원가입 + 로그인)
  - GitHub OAuth 연동 (회원가입 + 로그인)
  - users 테이블 설계 + Flyway 마이그레이션
  - OAuth 콜백 핸들러
  - 통합 테스트
- Out of Scope:
  - 이메일/비밀번호 로그인
  - 소셜 프로필 동기화
  - 계정 연동 해제

## Input

Google/GitHub OAuth Client ID/Secret, Spring Security OAuth2 문서

## Instructions

1. application.yml에 OAuth2 Client 설정 (Google, GitHub)
2. users 테이블 DDL 작성 + Flyway V1 마이그레이션
3. OAuth2UserService 구현 (사용자 조회/생성 로직)
4. OAuth2 성공 핸들러 구현 (JWT 발급 연계)
5. 신규 사용자 자동 회원가입 로직 구현
6. 기존 사용자 매핑 로직 구현 (email 기준)
7. 통합 테스트 작성 (MockOAuth2User)

## Output Format

auth 모듈 코드 + Flyway 마이그레이션 + 테스트 코드

## Constraints

- OAuth2 Authorization Code Grant만 사용
- 사용자 정보 최소 수집 (email, name, avatar)
- OAuth 상태값(state) CSRF 방어 필수

## Duration

2일

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
