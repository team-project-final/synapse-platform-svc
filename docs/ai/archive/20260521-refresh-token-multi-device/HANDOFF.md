# HANDOFF

**FROM**: Director (Claude)  
**TO**: Worker (Codex)  
**DATE**: 2026-05-21  
**SUBJECT**: Refresh Token 멀티 디바이스 세션 지원 (최대 5대) 구현 요청

---

## 요청 내용

[D-027] 설계 결정 및 [implementation_plan.md](file:///C:/Users/G/.gemini/antigravity-cli/brain/59121e41-5201-4c8c-bb21-c94e0b83af5b/implementation_plan.md)의 설계 계획에 따라, 사용자당 최대 5개의 active Refresh Token을 지원하고 초과 시 FIFO 방식으로 가장 오래된 세션을 만료하는 기능을 구현해 주세요.

## 구현 지침 및 대상 파일

### 1. Database Layer (Flyway Migration)
- **파일**: [V28__allow_multiple_refresh_tokens.sql](file:///C:/workspace/team_project_2/synapse-platform-svc/src/main/resources/db/migration/V28__allow_multiple_refresh_tokens.sql) [NEW]
- **작업**: 기존 `uq_refresh_tokens_user_id` UNIQUE INDEX를 드롭합니다.
```sql
DROP INDEX IF EXISTS uq_refresh_tokens_user_id;
```

### 2. Repository Layer
- **파일**: [RefreshTokenRepository.java](file:///C:/workspace/team_project_2/synapse-platform-svc/src/main/java/com/synapse/platform/auth/repository/RefreshTokenRepository.java) [MODIFY]
- **작업**: 
  - `List<RefreshToken> findAllByUserIdOrderByCreatedAtAsc(UUID userId);` 추가
  - `Optional<RefreshToken> findByUserIdAndTokenHash(UUID userId, String tokenHash);` 추가
  - `void deleteByUserIdAndTokenHash(UUID userId, String tokenHash);` 추가

### 3. Service Layer
- **파일**: [RefreshTokenService.java](file:///C:/workspace/team_project_2/synapse-platform-svc/src/main/java/com/synapse/platform/auth/service/RefreshTokenService.java) [MODIFY]
- **작업**:
  - Redis 키 생성 구조 변경: `refresh:{userId}:{tokenHash}` 형태로 개별 기기 토큰 캐시 키를 설정.
  - `save(RefreshToken)`: 기존 `deleteAllByUserId`를 호출하는 대신, DB에 저장된 토큰이 5개 이상일 경우 FIFO 방식으로 가장 오래된 토큰(DB 및 Redis 캐시)을 제거 후 저장.
  - `rotate(UUID userId, String oldRefreshToken, String newRefreshToken)`: 서명을 변경하고, `oldRefreshToken`의 해시로 기존 엔티티를 찾아 IP/기기 정보를 획득한 후 구 토큰(DB/Redis)을 제거하고, 동일 메타데이터를 승계하여 새 토큰을 저장.
  - `isValid(...)`: 캐시 유효성 판단 시 변경된 Redis 키(`refresh:{userId}:{tokenHash}`)로 검증하고 캐시 미스 시 DB 폴백.
  - `delete(UUID userId)`: 해당 사용자의 전체 세션을 제거하며, Redis에서는 `refresh:{userId}:*` 패턴을 사용해 일괄 삭제(flush).

### 4. Controller Layer
- **파일**: [AuthController.java](file:///C:/workspace/team_project_2/synapse-platform-svc/src/main/java/com/synapse/platform/auth/controller/AuthController.java) [MODIFY]
- **작업**: `/refresh` 호출 시 `refreshTokenService.rotate` 호출부에 구 리프레시 토큰 원문을 함께 넘기도록 변경.

### 5. Test Layer
- **파일**: 
  - [RefreshTokenServiceTest.java](file:///C:/workspace/team_project_2/synapse-platform-svc/src/test/java/com/synapse/platform/auth/service/RefreshTokenServiceTest.java) [MODIFY]
  - [AuthControllerTest.java](file:///C:/workspace/team_project_2/synapse-platform-svc/src/test/java/com/synapse/platform/auth/AuthControllerTest.java) [MODIFY]
- **작업**: 
  - `save_secondTokenInvalidatesOldToken()` 테스트를 `save_maxDevicesLimitAndFifoEviction()`으로 수정 및 보완하여 5대 한도 및 FIFO 교체 로직 검증.
  - `rotate` 메서드 서명 및 Redis 키 구조 변경에 맞춰 테스트 보정.

---

## 출력 형식

구현 완료 후 아래 내용을 작성하여 결과를 반환해 주세요.

1. **작성 및 수정된 파일 목록**
2. **각 수정/추가 파일별 전체 코드 블록**
3. **테스트 실행 명령어 및 결과** (`./gradlew test --tests "com.synapse.platform.auth.*"`)
4. **Done When 충족 여부 체크리스트 결과**

---

## 기한
2026-05-21
