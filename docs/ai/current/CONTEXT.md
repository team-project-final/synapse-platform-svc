# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- 브랜치: `feature/PLAT-008-httponly-refresh-cookie` (dev 기준, 생성 완료)
- 토큰 전달 방식 (D-028): Access Token → query string, Refresh Token → HttpOnly Cookie
- 환경별 쿠키: dev=SameSite=Lax+Secure없음, prod=SameSite=None+Secure
- `server.forward-headers-strategy: native` 추가 예정 (Gateway X-Forwarded-Proto 신뢰)
- CORS: allowCredentials(true) + 명시적 origins (`app.cors.allowed-origins`)
- 프론트엔드 합의 완료 (flutter_secure_storage for access_token, Cookie for refresh_token)

## 현재 미결 사항

- Worker 구현 결과 대기 중

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token raw 저장 금지 (DB: SHA-256 hash만, Redis: 조회 캐시)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- 쿠키 path: `/api/v1/auth`로 제한

## 참고할 공식 문서

- docs/ai/decisions/DECISION_LOG.md (D-028)
- docs/rules/06-auth-token.md
- docs/ai/current/HANDOFF.md
