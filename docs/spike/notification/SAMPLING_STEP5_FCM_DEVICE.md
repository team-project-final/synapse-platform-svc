# SAMPLING — Step 5: FCM 디바이스 토큰 등록

> **목적**: Step 5 구현 전, FCM 디바이스 토큰 등록/삭제의 핵심 기술 결정을 샘플 코드로 검증한다.
> 샘플링 결과를 바탕으로 본 프로젝트 설계 및 구현 방향을 확정한다.

---

## 샘플링 환경

| 항목 | 내용 |
|------|------|
| 기반 프로젝트 | synapse-platform-svc 복사본 |
| 기술 스택 | Spring Boot 4.0.0 + Java 21 + Spring Modulith 2.0.6 |
| 빌드 | Gradle (Kotlin DSL) |
| DB | PostgreSQL 16 + Flyway |
| 인증 | JWT (RS256) |
| 목표 기간 | 0.5일 |

---

## 샘플링 목표 전체 목록

| # | 항목 | 리스크 | 핵심 검증 포인트 |
|---|------|--------|----------------|
| A | Platform enum 소문자 저장 | MEDIUM | `@Enumerated(STRING)` + lowercase value 매핑, JSON 역직렬화 |
| B | UPSERT — 중복 token 재할당 | HIGH | `ON CONFLICT (token) DO UPDATE SET user_id` 동작, 기존 소유자 토큰 제거 |
| C | 최대 5개 디바이스 제한 | LOW | 등록 전 COUNT 쿼리 + 409 응답 흐름 |
| D | DELETE 소유권 검증 | LOW | 타 사용자 토큰 삭제 시 403 반환 확인 |
| E | Flyway V27 DDL | LOW | device_tokens 테이블 + 인덱스 적용, `./gradlew build` 통과 |
| F | firebase-admin SDK 불필요 확인 | MEDIUM | Step 5는 토큰 저장만, SDK 없이 컴파일/테스트 통과 |

---

## A. Platform enum 소문자 저장

### 목적

`Platform` enum (`ios`, `android`, `web`)이 DB에 소문자로 저장/조회되고, JSON 요청에서 소문자 문자열로 역직렬화되는지 검증.

### 검증 항목

- [x] `@Enumerated(EnumType.STRING)` 대신 `AttributeConverter<Platform, String>` 채택 — `ios`, `android`, `web` DB 저장 확인
- [x] `@JsonCreator` + `equalsIgnoreCase()` 로 소문자 JSON 매핑 (`"android"` → `Platform.ANDROID`)
- [x] 잘못된 platform 값 (`"MOBILE"`, `"desktop"`) 입력 시 400 반환 확인
- [x] DB 저장 후 조회 시 `PlatformConverter.convertToEntityAttribute()` 로 enum 역매핑 정상 동작 확인

### 결과

- 동작 여부: O
- 발견된 문제: `@Enumerated(EnumType.STRING)`만으로는 소문자 DB 저장과 소문자 JSON 역직렬화를 동시에 안정적으로 만족하기 어렵다.
- 메인 프로젝트 반영 시 주의사항: `Platform`은 `@JsonCreator/@JsonValue`와 `AttributeConverter<Platform, String>` 조합을 사용한다. 잘못된 값(`MOBILE`)은 400으로 처리한다.

---

## B. UPSERT — 중복 token 재할당

### 목적

동일 FCM 토큰이 다른 사용자(또는 같은 사용자)로 재등록될 때, 기존 row의 `user_id`가 새 사용자로 교체되는지 검증.
이는 기기 공유 / 로그아웃 후 재로그인 시나리오를 커버한다.

### 배경

FCM 토큰은 디바이스에 귀속된다. 한 기기에서 다른 계정으로 로그인하면 동일 토큰이 새 사용자에게 속해야 한다.
두 가지 접근 방식을 비교 검증한다.

**방식 1: Application-level upsert**
```java
DeviceToken existing = repo.findByToken(token);
if (existing != null) {
    existing.setUserId(newUserId);
    existing.setUpdatedAt(Instant.now());
    return repo.save(existing);
}
return repo.save(new DeviceToken(newUserId, token, platform));
```

**방식 2: Native query UPSERT**
```sql
INSERT INTO device_tokens (id, user_id, token, platform, created_at, updated_at)
VALUES (:id, :userId, :token, :platform, NOW(), NOW())
ON CONFLICT (token) DO UPDATE
  SET user_id = EXCLUDED.user_id,
      updated_at = NOW()
```

### 검증 항목

- [x] 방식 1 검토: `findByToken()` + `save()` 사이 TOCTOU race condition 존재 → 탈락
- [x] 방식 2: Native `ON CONFLICT (token) DO UPDATE` — 단일 atomic 쿼리로 교체 완료 확인
- [x] 동일 token 재등록 후 DB row 1개 유지 확인 (통합 테스트 `register_sameTokenForDifferentUser_shouldKeepOneRowAndMoveOwner`)
- [x] 기존 소유자의 device_tokens에서 해당 token row user_id가 새 사용자로 변경됨 확인
- [x] 최종 채택: 방식 2 (Native UPSERT)

### 결과

- 채택 방식: 방식 2, Native `ON CONFLICT (token) DO UPDATE`
- 동작 여부: O
- 발견된 문제: UPSERT 전에 같은 토큰을 조회하면 영속성 컨텍스트에 기존 엔티티가 남을 수 있다.
- 메인 프로젝트 반영 시 주의사항: Repository의 `@Modifying`에 `clearAutomatically = true`, `flushAutomatically = true`를 지정해 UPSERT 후 재조회가 최신 DB 상태를 보도록 한다. 기존 토큰 재등록은 5개 제한 검사에서 제외한다.

---

## C. 최대 5개 디바이스 제한

### 목적

사용자당 FCM 디바이스 토큰을 최대 5개로 제한하는 로직이 정상 동작하고 초과 시 409 반환되는지 검증.

### 검증 항목

- [x] `countByUserId(userId) >= 5` 조건에서 `DeviceRegistrationLimitExceededException` 발생 확인
- [x] 5개 등록 후 6번째 신규 요청에서 409 Conflict 반환 확인 (통합 테스트 `register_sixthNewDevice_shouldReturnConflict`)
- [x] UPSERT(기존 token 재등록)는 count 검사 제외 확인 (단위 테스트 `register_existingTokenForDifferentUser_shouldSkipLimitCheckAndUpsert`)
- [x] 단위 테스트: count 4→5→6 경계값 검증 (`register_newTokenWhenUserHasFourDevices_shouldRegisterFifthDevice`)

### 결과

- 동작 여부: O
- 발견된 문제: 없음
- 메인 프로젝트 반영 시 주의사항: `findByToken(token).isEmpty()`인 신규 토큰에만 `countByUserId(userId) >= 5` 검사를 적용한다. 초과 시 `DeviceRegistrationLimitExceededException`을 409로 반환한다.

---

## D. DELETE 소유권 검증

### 목적

`DELETE /api/v1/notifications/devices/{id}` 호출 시, 요청자의 `userId`와 토큰의 `userId`가 다를 때 403이 반환되는지 검증.

### 검증 항목

- [x] 본인 소유 token 삭제 → 204 No Content 반환 확인 (`unregister_ownDevice_shouldReturnNoContent`)
- [x] 타인 소유 token ID로 삭제 요청 → 403 Forbidden 반환 확인 (`unregister_otherUsersDevice_shouldReturnForbidden`)
- [x] 존재하지 않는 token ID 삭제 요청 → 404 Not Found 반환 확인 (`unregister_missingDevice_shouldReturnNotFound`)
- [x] JWT에서 추출한 userId(`@AuthenticationPrincipal`)와 DB row의 user_id 비교 로직 동작 확인

### 결과

- 동작 여부: O
- 발견된 문제: 없음
- 메인 프로젝트 반영 시 주의사항: 삭제는 먼저 ID로 조회해 404를 확정하고, 조회된 row의 `user_id`와 JWT principal의 userId를 비교해 불일치 시 403을 반환한다.

---

## E. Flyway V27 DDL

### 목적

`device_tokens` 테이블 DDL이 PostgreSQL에 정상 적용되고 빌드가 통과하는지 확인.

### DDL 초안

```sql
CREATE TABLE device_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token       TEXT        NOT NULL,
    platform    VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);
```

### 검증 항목

- [x] `V27__create_device_tokens.sql` Flyway 마이그레이션 적용 성공
- [x] `UNIQUE (token)` 제약 — UPSERT `ON CONFLICT (token)` 정상 동작 확인
- [x] `CREATE INDEX idx_device_tokens_user_id` 생성 확인
- [x] `./gradlew build` 전체 통과
- [x] `platform` 컬럼 CHECK 제약 추가 결정: `CHECK (platform IN ('ios', 'android', 'web'))` 포함 (DB 레벨 이중 보호)

### 결과

- 동작 여부: O
- 발견된 문제: 테스트 컨텍스트에서 Flyway 자동 실행이 비활성화될 수 있어, Testcontainers 통합 테스트는 기존 `RefreshTokenServiceTest`와 동일하게 `@BeforeAll`에서 명시적으로 migrate했다.
- 메인 프로젝트 반영 시 주의사항: V27에는 `updated_at`과 `platform CHECK (platform IN ('ios', 'android', 'web'))`를 포함한다. PostgreSQL 통합 테스트에서 마이그레이션 적용과 `./gradlew clean build` 통과를 확인했다.

---

## F. firebase-admin SDK 불필요 확인

### 목적

Step 5는 토큰 저장만 수행하고 실제 FCM 푸시는 Step 7에서 구현한다.
`firebase-admin` SDK를 `build.gradle.kts`에 추가하지 않아도 컴파일과 테스트가 정상 통과하는지 확인.

### 검증 항목

- [x] `firebase-admin` 의존성 없이 `DeviceToken` 엔티티, 서비스, 컨트롤러 컴파일 성공
- [x] `NotificationService`가 Firebase 클래스를 직접 참조하지 않는 구조 확인 (빈 플레이스홀더 유지)
- [x] `./gradlew build` 전체 통과
- [x] Step 7에서 SDK 추가 시 기존 코드 수정 없이 통합 가능한 구조 확인 — `DeviceTokenService`는 DB 저장만, FCM 발송은 별도 Service 분리 예정

### 결과

- 동작 여부: O
- 발견된 문제: 없음
- 메인 프로젝트 반영 시 주의사항: Step 5는 토큰 저장/삭제만 담당하므로 `firebase-admin` 의존성을 추가하지 않는다. FCM 발송 SDK 통합은 Step 7에서 `NotificationService` 쪽으로 확장한다.

---

## 최종 샘플링 결과 요약

> Worker 샘플 완료 / Director 검토 완료 — 2026-05-19

| # | 항목 | 결과 | 메인 프로젝트 반영 여부 |
|---|------|------|----------------------|
| A | Platform enum 소문자 저장 | O | 반영 — `AttributeConverter` + `@JsonCreator/@JsonValue` 패턴 그대로 사용 |
| B | UPSERT — 중복 token 재할당 | O | 반영 — Native `ON CONFLICT` + `@Modifying(clearAutomatically=true)` |
| C | 최대 5개 디바이스 제한 | O | 반영 — `isNewToken` 선판별 후 count 검사 순서 유지 |
| D | DELETE 소유권 검증 | O | 반영 — `findById()` → userId 비교 → `AccessDeniedException` 패턴 |
| E | Flyway V27 DDL | O | 반영 — CHECK 제약 포함, `updated_at` 컬럼 포함 (초안 대비 변경) |
| F | firebase-admin SDK 불필요 | O | 반영 — Step 5 미추가, Step 7에서 별도 추가 |

## 메인 프로젝트 반영 시 주의사항 (종합)

1. **`@Modifying` 캐시 무효화 필수**: native UPSERT 후 JPA 1차 캐시가 stale 데이터를 반환할 수 있음. `clearAutomatically = true, flushAutomatically = true` 세트로 적용.

2. **DDL 초안 대비 변경점**: 메인 프로젝트 V27 작성 시 아래 두 가지를 반드시 포함:
   - `platform VARCHAR(10) NOT NULL CHECK (platform IN ('ios', 'android', 'web'))` — DB 레벨 이중 보호
   - `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` — UPSERT `SET updated_at = NOW()` 사용

3. **SecurityConfig 별도 FilterChain 분리**: `/api/v1/notifications/**` 경로를 `@Order(1)` SecurityFilterChain으로 분리 — OAuth2 로그인 리다이렉트 흐름과 충돌 없이 독립 401 처리.

4. **`GlobalExceptionHandler`에 `EntityNotFoundException` → 404 핸들러 확인**: 메인 프로젝트에 이미 존재하는지 확인 후 없으면 추가. 샘플링에서는 `AccessDeniedException` → 403은 Spring Security 자동 처리, `EntityNotFoundException` → 404 변환은 `@ControllerAdvice`에서 처리.

5. **Step 7 준비 사항**: `DeviceTokenRepository`에 `List<DeviceToken> findByUserId(UUID userId)` 추가 필요. `NotificationService`에 FCM 발송 로직을 `DeviceTokenService`와 별도로 통합.

6. **`BusinessException` 상속 패턴**: Worker가 `RuntimeException` 대신 프로젝트 표준 `BusinessException(errorCode, httpStatus, message)` 를 상속하여 구현 — 메인 프로젝트에서도 동일 패턴 유지. 에러 코드: `PLAT-NOTIFICATION-001`.
