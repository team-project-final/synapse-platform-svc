# TASK — Refresh Token 멀티 디바이스 세션 지원 (최대 5대)

> 출처: 팀 회의 결정 사항 및 [D-027] 설계 결정

## 상태

- Phase: 구현
- 담당 Agent: Worker
- 시작일: 2026-05-21
- 목표 완료일: 2026-05-21

---

## Step Goal

사용자가 최대 5대까지 멀티 디바이스 세션(활성 Refresh Token)을 동시에 유지할 수 있도록 리프레시 토큰 정책을 변경하고, 5개 초과 시 FIFO 방식으로 가장 오래된 세션을 자동 만료시킨다.

## Done When

- [ ] `uq_refresh_tokens_user_id` UNIQUE 인덱스를 해제하는 Flyway 마이그레이션(`V28__allow_multiple_refresh_tokens.sql`) 추가
- [ ] `RefreshTokenRepository`에 세션 목록 조회 및 특정 토큰 조작용 메서드 추가
- [ ] `RefreshTokenService`의 Redis 캐시 키를 `refresh:{userId}:{tokenHash}` 형태로 고도화
- [ ] `RefreshTokenService.save()`에 최대 5개 세션 제한 및 초과 시 가장 오래된 세션(DB 및 Redis) 제거 로직(FIFO) 구현
- [ ] `RefreshTokenService.rotate()`에 기존 토큰을 넘겨 특정 세션만 교체하고 기존 기기/IP 메타데이터를 보존하는 로직 구현
- [ ] `AuthController.java`의 `/refresh` API에 변경된 rotate 시그니처 적용
- [ ] `RefreshTokenServiceTest.java` 및 `AuthControllerTest.java` 수정 후 `./gradlew test --tests "com.synapse.platform.auth.*"` 테스트 통과

## Scope

- In Scope:
  - `refresh_tokens` 테이블 유니크 제약 조건 완화
  - `RefreshTokenRepository` 인터페이스 수정
  - `RefreshTokenService` 저장, 조회, 검증, 회전 로직 변경 및 Redis 키 고도화
  - `AuthController` 토큰 회전 시그니처 수정
  - 관련 단위/통합 테스트 수정 및 검증
- Out of Scope:
  - 로그아웃 엔드포인트 구현 (Step 9 대상)

## Input

- `src/main/resources/db/migration/`
- `src/main/java/com/synapse/platform/auth/`
- `src/test/java/com/synapse/platform/auth/`

## Instructions

1. `V28__allow_multiple_refresh_tokens.sql` 마이그레이션 파일 작성 (`DROP INDEX uq_refresh_tokens_user_id;`)
2. `RefreshTokenRepository.java`에 `findAllByUserIdOrderByCreatedAtAsc` 및 `findByUserIdAndTokenHash` 추가
3. `RefreshTokenService.java` 내의 Redis 키 구조를 `key(userId, tokenHash)` 포맷으로 리팩토링
4. `save` 메서드에서 `deleteAllByUserId`를 호출하는 대신, 현재 개수 확인 후 5개 이상이면 초과분만큼 오래된 토큰(DB 및 Redis)을 FIFO 방식으로 지우고 새 토큰을 저장하도록 변경
5. `rotate(userId, oldRefreshToken, newRefreshToken)`로 서명 변경 후 기존 토큰의 기기 핑거프린트/IP 주소를 가져와 새 토큰에 유지한 채 구버전 삭제 및 신규 저장 처리
6. `AuthController.java`에 구버전 리프레시 토큰을 `rotate` 호출 시 파라미터로 넘기도록 변경
7. `RefreshTokenServiceTest.java`의 `save_secondTokenInvalidatesOldToken()`을 FIFO 및 최대 5대 지원 방식으로 변경하고, `rotate` 테스트와 `AuthControllerTest`도 변경 사항에 맞게 보정
8. `./gradlew test --tests "com.synapse.platform.auth.*"` 빌드/테스트 수행 후 결과 검증

## Output Format

- 수정된 파일 목록 및 테스트 성공 결과 (경로 + 전체 코드 블록)

## Constraints

- Refresh Token 원문 저장 금지 (token_hash SHA-256 저장 필수)
- 사용자당 최대 5개 활성 세션(Refresh Token) 허용
- 토큰 회전 시 기존 감사 정보(기기 핑거프린트, IP 주소) 보존 필수

## Assignee / Reviewer

- Assignee: Worker (Codex)
- Reviewer: Director (Claude)

