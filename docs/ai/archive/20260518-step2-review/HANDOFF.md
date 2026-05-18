# HANDOFF — Step 2 점검 수정 (완료)

> FROM: Director (Claude) → TO: Worker (Codex)
> 날짜: 2026-05-18 / 상태: 완료

## 수정 완료 항목

- [FIX-1] Apple OAuth OIDC 구현
  - application.yml Apple provider/registration 추가 (scope: openid,name,email, method: client_secret_post)
  - OAuthAttributes "apple" case 추가 (ofApple, nameAttributeKey)
  - OAuthUserResolver 신규 추출 (resolveUser/signUp 공통화)
  - CustomOidcUserService 신규 구현 (OidcUserService 상속, DefaultOidcUser + userId enrichment)
  - CustomOAuth2UserService OAuthUserResolver 위임으로 리팩토링
  - SecurityConfig .oidcUserService(customOidcUserService) 연결
  - OAuthUserResolverTest, CustomOidcUserServiceTest 신규 추가
- [FIX-2] Microsoft OAuth TODO 주석 (application.yml provider.microsoft 포함)

## 커밋 메시지

```
feat(auth): Step 2 점검 — Apple OAuth OIDC 구현 + Microsoft OAuth TODO 문서화
```
