# CONTEXT (archive)

> Step 2 점검 완료 시점 스냅샷

## 확정된 것

- Google/GitHub OAuth, oauth_identities 분리, 테넌트 자동 생성, 기존 사용자 매핑 — 정상
- access_token_enc 암호화 저장 (V20 마이그레이션) — 정상
- 통합 테스트 (OAuth2LoginIntegrationTest, OAuthSignupRollbackIntegrationTest) — 정상
- Apple OAuth OIDC 구현 완료 (CustomOidcUserService + OAuthUserResolver 추출)
- Microsoft OAuth TODO 주석 문서화 완료

## 핵심 결정 사항

- Apple scope: `openid,name,email` (Spring Security OIDC dispatch 요건)
- Apple client-authentication-method: `client_secret_post` (private_key_jwt는 Out of Scope)
- resolveUser/signUp 공통 로직 → OAuthUserResolver로 추출 (DRY)
- Apple name null → email prefix fallback (OAuthUserResolver.displayName())

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token raw 원문 저장 금지 (DB token_hash + Redis 캐시, D-006)
- 사용자당 Refresh Token 1개 active (D-009, D-010)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
