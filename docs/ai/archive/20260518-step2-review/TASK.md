# TASK — Step 2 점검: Apple OAuth 구현 + Microsoft TODO 문서화

> 출처: TASK_platform.md Step 2 (신규 문서 기준 점검)

## 상태

- Phase: 완료
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-18
- 목표 완료일: 2026-05-18

---

## Step Goal

Step 2 점검 결과 누락된 Apple OAuth를 구현하고, Microsoft OAuth TODO를 문서화한다.

## Done When

- [x] Apple OAuth 로그인 → 신규 사용자 자동 회원가입 동작
- [x] Apple OAuth 재로그인 → 기존 계정 매핑 동작
- [x] `OAuthAttributes`에 "apple" case 추가
- [x] `CustomOidcUserService` 구현 (Apple OIDC 전용)
- [x] `SecurityConfig`에 `.oidcUserService()` 연결
- [x] `application.yml`에 Apple provider + registration 설정 추가
- [x] Apple OAuth scope를 `openid,name,email`로 보정 (Spring Security OIDC dispatch 필수)
- [x] Apple client authentication method를 `client_secret_post`로 보정
- [x] Microsoft OAuth 확장 TODO 코드 주석 명시
- [x] 기존 Google/GitHub 통합 테스트 회귀 없음
