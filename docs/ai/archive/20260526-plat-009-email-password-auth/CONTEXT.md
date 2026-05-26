# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- 브랜치: `feature/PLAT-009-email-password-auth` (dev 기준)
- 이메일 인증(verification)은 Step 7 SES 연동 시점으로 미룸
- DB 스키마/User 엔티티에 필요한 필드 이미 존재 (Flyway 마이그레이션 불필요)
- Refresh Token은 PLAT-008과 동일하게 HttpOnly Cookie로 전달

## 현재 미결 사항

- Worker 구현 결과 대기 중

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token 저장: DB에 `token_hash`(SHA-256)만 저장, Redis는 조회 캐시 (D-006)
  → raw token은 DB/Redis 어디에도 저장 금지
- 비밀번호: BCryptPasswordEncoder 고정, 평문 저장/로깅 금지
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상

## 참고할 공식 문서

- docs/ai/current/HANDOFF.md
- docs/ai/decisions/DECISION_LOG.md (D-006, D-028)
- docs/rules/06-auth-token.md
