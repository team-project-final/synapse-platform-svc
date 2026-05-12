# 2. 기능 설계 RULE — Function Design

> **프로젝트**: Synapse — 통합 학습-지식 그래프 SaaS  
> **버전**: v1.0 | 2026-05-12  
> **대상**: 백엔드 개발자 전원  
> **준수 수준**: [MUST] 반드시 / [SHOULD] 권장 / [MAY] 선택

---

## 2.1 API 설계 [MUST]

모든 REST API는 일관된 URI 컨벤션을 따른다.

### 규칙

- URI는 **kebab-case** 사용
- depth 는 **3~4단계** 이내로 제한
- prefix는 항상 `/api/v1/`
- 리소스명은 **복수형 명사**
- 동사 사용 금지 (행위는 HTTP Method로 표현)

### URI 예시 테이블

| 상태 | URI | 설명 |
|------|-----|------|
| ✅ Good | `GET /api/v1/notes` | 노트 목록 조회 |
| ✅ Good | `POST /api/v1/learning-cards` | 학습카드 생성 |
| ✅ Good | `GET /api/v1/users/{id}/knowledge-graphs` | 유저의 지식그래프 조회 (3단계) |
| ✅ Good | `PATCH /api/v1/communities/{id}/posts/{postId}` | 커뮤니티 게시글 수정 (4단계) |
| ❌ Bad | `GET /api/v1/getNote` | 동사 사용, camelCase |
| ❌ Bad | `POST /api/v1/learning_cards/create` | snake_case, 불필요한 동사 |
| ❌ Bad | `GET /api/v1/users/{id}/notes/{nid}/chunks/{cid}/tags/{tid}` | 5단계 초과 |
| ❌ Bad | `GET /v1/notes` | `/api/` prefix 누락 |

> **이유**: URI 일관성은 프론트엔드-백엔드 간 커뮤니케이션 비용을 줄이고, Gateway 라우팅 규칙을 단순하게 유지해줌. depth 제한은 URL 가독성과 캐시 키 설계에 직결됨.

---

## 2.2 페이지네이션 [SHOULD]

### 규칙

- **기본**: 커서 기반(Cursor-based) 페이지네이션 사용
- **예외**: 관리자 API(`/api/v1/admin/...`)에 한해 offset 허용
- 커서 값은 opaque string (클라이언트가 내부 구조를 몰라야 함)
- 기본 `size`는 20, 최대 100

### ✅ Good — 커서 기반 응답

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": "01941a00-...", "title": "Graph Theory 101" },
      { "id": "01941a01-...", "title": "Spaced Repetition" }
    ],
    "cursor": {
      "next": "eyJpZCI6IjAxOTQxYTAxLi4uIiwiY3JlYXRlZEF0IjoiMjAyNi0wNS0xMlQwOTowMDowMFoifQ==",
      "hasNext": true
    }
  },
  "meta": {
    "timestamp": "2026-05-12T09:00:00Z",
    "traceId": "trace-abc-123"
  }
}
```

### ❌ Bad — 일반 API에 offset 사용

```json
{
  "data": {
    "items": [...],
    "page": 3,
    "totalPages": 42,
    "totalElements": 834
  }
}
```

> **이유**: offset 방식은 데이터가 많아지면 `OFFSET N`이 full scan에 가까워져 성능이 급락함. 커서 방식은 인덱스를 타므로 데이터 규모에 무관하게 일정한 성능을 보장함.

---

## 2.3 에러 응답 [MUST]

### 규칙

- 모든 에러 응답은 **RFC 7807 Problem Details** 형식을 따름
- 서비스별 에러 코드 prefix를 부여:
  - `PLAT-xxx` : Platform Service (auth, audit, billing, notification)
  - `ENGM-xxx` : Engagement Service (community, gamification)
  - `KNOW-xxx` : Knowledge Service (note, graph, chunking)
  - `LRNG-xxx` : Learning Service (card, srs, ai)
- 코드 번호는 001부터 순차 증가
- `detail` 필드는 사용자에게 노출 가능한 메시지, `instance`에 trace 정보 포함

### ✅ Good — RFC 7807 준수

```json
{
  "type": "https://api.synapse.app/errors/KNOW-003",
  "title": "Note Not Found",
  "status": 404,
  "detail": "요청한 노트(id=01941a00-...)를 찾을 수 없어",
  "instance": "/api/v1/notes/01941a00-...",
  "code": "KNOW-003",
  "traceId": "trace-xyz-789"
}
```

### ❌ Bad — 비표준 에러 응답

```json
{
  "error": true,
  "message": "not found",
  "errorCode": 404
}
```

> **이유**: RFC 7807은 프론트엔드가 에러를 프로그래밍적으로 처리할 수 있는 표준 스키마를 제공함. 서비스별 코드 prefix는 운영 중 에러 발생 서비스를 즉시 식별할 수 있게 해줌.

---

## 2.4 Soft Delete [MUST]

### 규칙

- **사용자 데이터**는 반드시 soft delete — `deleted_at` 타임스탬프로 관리
- **물리 삭제**는 시스템 내부 데이터(캐시, 임시 토큰 등)에만 허용
- 조회 시 `deleted_at IS NULL` 조건을 기본 적용 (`@Where` 또는 `@SQLRestriction`)
- 복구 API 제공 시 30일 이내만 허용

### ✅ Good — Soft Delete Entity

```java
@Entity
@SQLRestriction("deleted_at IS NULL")
public class Note extends BaseEntity {

    @Id
    private UUID id;

    private String title;
    private String content;

    private LocalDateTime deletedAt;  // null = 활성

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}
```

### ❌ Bad — 사용자 데이터 물리 삭제

```java
// 사용자 노트를 DB에서 바로 삭제 -> 복구 불가
public void deleteNote(UUID noteId) {
    noteRepository.deleteById(noteId);  // 물리 삭제!
}
```

> **이유**: 사용자 데이터는 실수 삭제 복구, 감사 추적, 법적 보관 의무(GDPR 30일 등)를 위해 soft delete가 필수임. 물리 삭제하면 이 모든 게 불가능해짐.

---

## 2.5 DIP/OCP 준수 [MUST]

### 규칙

- 모듈 간 의존은 **인터페이스(추상)** 를 통해서만 허용
- 구현체를 직접 참조하면 모듈 결합도가 높아져 독립 배포/테스트가 불가능해짐
- Spring Modulith 경계를 넘는 호출은 반드시 `@ApplicationModuleListener` 이벤트 또는 exposed interface 사용

### ✅ Good — 인터페이스 의존

```java
// knowledge 모듈의 exposed API (인터페이스)
public interface NoteQueryPort {
    NoteDto findById(UUID noteId);
    List<NoteDto> findByUserId(UUID userId);
}

// learning 모듈에서 사용
@Service
@RequiredArgsConstructor
public class CardGenerationService {

    private final NoteQueryPort noteQueryPort;  // 인터페이스 의존

    public Card generateFromNote(UUID noteId) {
        NoteDto note = noteQueryPort.findById(noteId);
        return cardFactory.create(note);
    }
}
```

### ❌ Bad — 구현체 직접 참조

```java
// learning 모듈에서 knowledge의 구현체를 직접 import
import com.synapse.knowledge.note.infrastructure.persistence.NoteQueryRepository;

@Service
@RequiredArgsConstructor
public class CardGenerationService {

    // 구현체 직접 의존 -> 모듈 경계 위반!
    private final NoteQueryRepository noteQueryRepository;

    public Card generateFromNote(UUID noteId) {
        NoteEntity entity = noteQueryRepository.findById(noteId);
        return cardFactory.create(entity);
    }
}
```

> **이유**: DIP(Dependency Inversion Principle)를 지켜야 각 모듈이 독립적으로 테스트/배포 가능하고, OCP(Open-Closed Principle)에 의해 구현이 바뀌어도 호출 측 코드를 수정할 필요가 없음. Modulith에서 이거 안 지키면 순환 의존으로 빌드 자체가 터짐.

---

## 요약 체크리스트

| # | 규칙 | 수준 |
|---|------|------|
| 2.1 | RESTful URI — kebab-case, /api/v1/, 3~4 depth | [MUST] |
| 2.2 | 커서 기반 페이지네이션 (관리자 API만 offset) | [SHOULD] |
| 2.3 | RFC 7807 에러 + 서비스별 코드 prefix | [MUST] |
| 2.4 | 사용자 데이터 soft delete | [MUST] |
| 2.5 | DIP/OCP — 모듈 간 인터페이스 의존만 허용 | [MUST] |
