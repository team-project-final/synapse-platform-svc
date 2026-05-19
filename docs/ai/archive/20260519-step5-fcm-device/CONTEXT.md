# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

### Step 5: FCM 디바이스 등록 (2026-05-19 착수)

- **브랜치**: `feature/PLAT-005-fcm-device`
- **다음 Flyway 버전**: V27 (V26__create_processed_events.sql 이후)
- **notification 모듈 현황**: `NotificationPlaceholder.java` + `package-info.java` 만 존재 (빈 골격)
- **샘플링 결과**: A~F 전항목 통과 (SAMPLING_STEP5_FCM_DEVICE.md)

### 기존 코드에서 확인된 사항

| 항목 | 현황 | Step 5 처리 |
|------|------|------------|
| `BusinessException` | `global/exception/` — abstract, `(errorCode, status, message)` 생성자 | 직접 상속 |
| `GlobalExceptionHandler` | `BusinessException` O, `EntityNotFoundException → 404` **없음** | 핸들러 추가 필요 |
| `SecurityConfig` | 단일 FilterChain, `@Order` 없음 | `@Order(2)` 추가 필요 |
| billing 패키지 구조 | `controller/`, `dto/request/`, `dto/response/`, `entity/`, `exception/`, `repository/`, `service/` | notification도 동일 구조 |
| userId 추출 패턴 | `UUID.fromString(authentication.getName())` — BillingController 기준 | notification도 동일 패턴 |

### 샘플링으로 확정된 설계 결정

| 항목 | 결정 |
|------|------|
| Platform enum | `AttributeConverter<Platform, String>` + `@JsonCreator/@JsonValue` (소문자 DB 저장) |
| UPSERT | Native `ON CONFLICT (token) DO UPDATE SET user_id` — 원자적 처리 |
| @Modifying | `clearAutomatically = true, flushAutomatically = true` 필수 |
| 5개 제한 검사 | `findByToken(token).isEmpty()` 선판별 후 count ≥ 5 검사 (기존 token 재등록은 제외) |
| DELETE 소유권 | `findById()` → `userId` 비교 → 불일치 시 403 |
| firebase-admin | Step 5 미추가 (Step 7에서 별도 추가) |

### DDL 확정 (V27)

```sql
CREATE TABLE device_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    user_id     UUID        NOT NULL,
    token       TEXT        NOT NULL,
    platform    VARCHAR(10) NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_tenant_user ON device_tokens (tenant_id, user_id);
```

> `tenant_id` — ERD 표준 컬럼. `UserApi.findById(userId).defaultTenantId()`로 resolve
> `is_active` — WORKFLOW 5.4/5.6 명시
> 인덱스 — ERD 컨벤션: `tenant_id` prefix 필수
> UPSERT: `ON CONFLICT (token) DO UPDATE SET tenant_id, user_id, updated_at`

## 현재 미결 사항

- 없음 — Worker 구현 완료

## 활성 제약

- `firebase-admin` 의존성 추가 금지
- JWT 서명: RS256 고정
- 패키지 루트: `com.synapse.platform.notification.*`
- 테스트 커버리지: 신규 코드 80% 이상 (JaCoCo)
- NotificationPlaceholder.java 삭제 — 구현 클래스로 대체

## SecurityConfig 처리 방침

- 기존 `SecurityConfig`에 `@Order(2)` 추가
- 신규 `NotificationSecurityConfig`에 `@Order(1)` + `/api/v1/notifications/**` 인증 필터체인

## 참고할 공식 문서

- `docs/spike/notification/SAMPLING_STEP5_FCM_DEVICE.md` (설계 결정 근거)
- `src/main/java/com/synapse/platform/billing/` (패키지 구조 참조 패턴)
- `docs/ai/current/TASK.md` (Done When + Instructions)
