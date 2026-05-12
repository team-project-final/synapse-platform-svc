# 3. 기술 구현 RULE — Technical Implementation

> **프로젝트**: Synapse — 통합 학습-지식 그래프 SaaS  
> **버전**: v1.0 | 2026-05-12  
> **대상**: 백엔드 개발자 전원  
> **준수 수준**: [MUST] 반드시 / [SHOULD] 권장 / [MAY] 선택

---

## 3.1 메서드 크기 [SHOULD]

### 규칙

- 메서드 하나는 **30~40줄** 이내로 작성
- 초과하면 의미 단위로 private 메서드로 분리
- 분리 기준: "이 블록에 이름을 붙일 수 있는가?" — 붙일 수 있으면 분리 대상

### ✅ Good — 적절히 분리된 메서드

```java
@Service
public class CardReviewService {

    public ReviewResult review(UUID cardId, ReviewGrade grade) {
        Card card = findCard(cardId);
        SrsSchedule schedule = calculateNextSchedule(card, grade);
        card.applySchedule(schedule);
        cardRepository.save(card);
        publishReviewEvent(card, grade);
        return ReviewResult.from(card, schedule);
    }

    private Card findCard(UUID cardId) {
        return cardRepository.findById(cardId)
            .orElseThrow(() -> new CardNotFoundException(cardId));
    }

    private SrsSchedule calculateNextSchedule(Card card, ReviewGrade grade) {
        return srsAlgorithm.calculate(card.currentInterval(), grade);
    }

    private void publishReviewEvent(Card card, ReviewGrade grade) {
        eventPublisher.publish(new CardReviewedEvent(card.getId(), grade));
    }
}
```

### ❌ Bad — 80줄짜리 god method

```java
public ReviewResult review(UUID cardId, ReviewGrade grade) {
    // 카드 조회, 유효성 검증, SRS 계산, 저장, 이벤트 발행,
    // 감사 로그, 통계 업데이트, 알림 발송...
    // 80줄 이상의 로직이 한 메서드에 전부 들어있음
}
```

> **이유**: 30~40줄 제한은 한 화면에 메서드 전체가 보이는 수준이야. 이걸 넘으면 코드 리뷰 때 맥락 파악이 어렵고, 단위 테스트 작성도 힘들어짐.

---

## 3.2 N+1 방지 [MUST]

### 규칙

- 연관 엔티티 조회 시 반드시 **fetchJoin()** 또는 **@EntityGraph** 사용
- `findAll()` 후 loop에서 lazy 접근하는 패턴 절대 금지
- QueryDSL `fetchJoin()` 또는 Spring Data JPA `@EntityGraph` 둘 중 하나 선택

### ❌ Bad — N+1 발생 코드

```java
// 노트 100개 조회 -> 각 노트의 tags를 lazy로 접근 -> 101번 쿼리 발생!
public List<NoteWithTagsDto> getAllNotesWithTags(UUID userId) {
    List<Note> notes = noteRepository.findAllByUserId(userId);

    return notes.stream()
        .map(note -> new NoteWithTagsDto(
            note.getId(),
            note.getTitle(),
            note.getTags()  // Lazy 로딩 -> 매번 SELECT!
        ))
        .toList();
}
```

### ✅ Good — fetchJoin으로 한 방 쿼리

```java
// QueryDSL fetchJoin
public List<NoteWithTagsDto> getAllNotesWithTags(UUID userId) {
    List<Note> notes = queryFactory
        .selectFrom(note)
        .leftJoin(note.tags, tag).fetchJoin()
        .where(note.userId.eq(userId))
        .fetch();

    return notes.stream()
        .map(NoteWithTagsDto::from)
        .toList();
}
```

```java
// 또는 @EntityGraph 사용
public interface NoteRepository extends JpaRepository<Note, UUID> {

    @EntityGraph(attributePaths = {"tags"})
    List<Note> findAllByUserId(UUID userId);
}
```

> **이유**: N+1은 데이터 10개일 땐 티 안 나는데, 프로덕션에서 1000개 넘어가면 응답시간이 수십 배로 뻥튀기됨. 초기에 잡지 않으면 나중에 찾기도 힘들어.

---

## 3.3 트랜잭션 [MUST]

### 규칙

- 읽기 전용 메서드에는 **`@Transactional(readOnly = true)`** 필수
- 쓰기 메서드에는 전파 레벨을 명시적으로 작성 (기본값에 의존하지 않기)
- 트랜잭션은 **서비스(application) 계층에서만** 선언 — Controller, Repository에 걸지 않음
- 하나의 트랜잭션에서 외부 API 호출 금지 (트랜잭션 밖에서 호출 후 결과를 넘길 것)

### ✅ Good — 적절한 트랜잭션 설정

```java
@Service
@RequiredArgsConstructor
public class NoteService {

    @Transactional(readOnly = true)
    public NoteDto findById(UUID noteId) {
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new NoteNotFoundException(noteId));
        return NoteDto.from(note);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public NoteDto create(NoteCreateCommand command) {
        Note note = Note.create(command.title(), command.content());
        noteRepository.save(note);
        eventPublisher.publish(new NoteCreatedEvent(note.getId()));
        return NoteDto.from(note);
    }
}
```

### ❌ Bad — Controller에 트랜잭션 / readOnly 미설정

```java
@RestController
public class NoteController {

    @Transactional  // Controller에 트랜잭션 금지!
    @GetMapping("/api/v1/notes/{id}")
    public NoteDto getNote(@PathVariable UUID id) {
        return noteService.findById(id);  // readOnly도 아님
    }
}
```

> **이유**: `readOnly = true`는 JPA flush를 건너뛰고 DB 커넥션을 replica로 라우팅할 수 있게 해줘서 성능에 직결됨. Controller에 트랜잭션을 걸면 요청 전체가 하나의 트랜잭션이 되어 커넥션 점유 시간이 늘어남.

---

## 3.4 AOP [MUST]

### 규칙

- AOP는 **cross-cutting concern에만** 사용: 로깅, 감사(audit), 성능 측정, 보안 검증
- **비즈니스 로직에 AOP 적용 절대 금지** — 디버깅이 불가능해짐
- Aspect 클래스는 `infrastructure` 패키지에 위치

### ✅ Good — cross-cutting concern

```java
@Aspect
@Component
public class AuditAspect {

    @AfterReturning(pointcut = "@annotation(Auditable)", returning = "result")
    public void auditAction(JoinPoint joinPoint, Object result) {
        String method = joinPoint.getSignature().getName();
        auditLogger.log(method, result);
    }
}
```

### ❌ Bad — 비즈니스 로직을 AOP로 처리

```java
@Aspect
@Component
public class CardExpirationAspect {

    // 비즈니스 로직(만료 처리)을 AOP로 -> 디버깅 지옥
    @Before("execution(* com.synapse.learning.card..*Service.*(..))")
    public void checkCardExpiration(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        UUID cardId = (UUID) args[0];
        Card card = cardRepository.findById(cardId).orElseThrow();

        if (card.isExpired()) {
            card.markAsExpired();
            cardRepository.save(card);
            // 호출자는 이 로직이 실행되는지 코드만 봐서는 알 수 없음!
        }
    }
}
```

> **이유**: AOP는 코드에 안 보이는 곳에서 실행되니까, 비즈니스 로직을 넣으면 "왜 이게 실행되지?" 하고 코드 전체를 뒤져야 함. cross-cutting만 쓰면 "아 로깅이겠지" 하고 넘어갈 수 있어.

---

## 3.5 Modulith 경계 [MUST]

### 규칙

- 모듈은 `allowedDependencies`에 `"shared"`만 공통 허용
- 모듈 간 직접 import 금지 — **이벤트** 또는 **exposed interface**로 통신
- **순환 의존 절대 금지** — ArchUnit 테스트로 CI에서 검증
- 모듈 경계는 `package-info.java`로 선언

### ✅ Good — package-info.java 선언

```java
// com/synapse/knowledge/note/package-info.java
@ApplicationModule(
    allowedDependencies = {"shared"}
)
package com.synapse.knowledge.note;

import org.springframework.modulith.ApplicationModule;
```

```java
// com/synapse/knowledge/graph/package-info.java
@ApplicationModule(
    allowedDependencies = {"shared", "note"}  // 같은 서비스 내 note 모듈 참조 허용
)
package com.synapse.knowledge.graph;

import org.springframework.modulith.ApplicationModule;
```

### ❌ Bad — 순환 의존

```java
// note 모듈이 graph를 참조하고, graph 모듈이 note를 참조 -> 순환!
// note/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "graph"})
package com.synapse.knowledge.note;

// graph/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "note"})
package com.synapse.knowledge.graph;
// -> 빌드 시 ModulithVerificationFailedException 발생!
```

### ArchUnit 검증 예시

```java
@AnalyzeClasses(packages = "com.synapse.knowledge")
class ModulithArchTest {

    @ArchTest
    static final ArchRule noCyclicDependencies =
        slices().matching("com.synapse.knowledge.(*)..")
            .should().beFreeOfCycles();
}
```

> **이유**: 순환 의존이 생기면 모듈을 독립적으로 테스트/빌드할 수 없고, 나중에 마이크로서비스로 분리할 때 불가능해짐. Modulith의 존재 의미 자체가 경계 강제야.

---

## 3.6 예외 처리 [SHOULD]

### 규칙

- 도메인별 **커스텀 예외 계층**을 정의 (공통 BusinessException 상속)
- catch 후 **아무 로깅 없이 rethrow 금지** — 최소한 warn 레벨 로그 남기기
- 예외 메시지에 디버깅 컨텍스트(ID, 상태 등) 포함
- `GlobalExceptionHandler`에서 최종 변환 (서비스에서 HTTP 상태코드 지정 금지)

### ✅ Good — 도메인 예외 계층

```java
// 공통 Base Exception
public abstract class BusinessException extends RuntimeException {
    private final String errorCode;

    protected BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}

// Knowledge 도메인 예외
public class NoteNotFoundException extends BusinessException {
    public NoteNotFoundException(UUID noteId) {
        super("KNOW-003", "노트를 찾을 수 없음: " + noteId);
    }
}

public class GraphCycleDetectedException extends BusinessException {
    public GraphCycleDetectedException(UUID fromNode, UUID toNode) {
        super("KNOW-007", "순환 참조 감지: " + fromNode + " -> " + toNode);
    }
}
```

### ❌ Bad — catch 후 무로깅 rethrow

```java
public NoteDto findNote(UUID noteId) {
    try {
        return noteRepository.findById(noteId)
            .map(NoteDto::from)
            .orElseThrow();
    } catch (Exception e) {
        throw new RuntimeException(e);  // 로그도 없이 감싸서 던짐 -> 디버깅 불가!
    }
}
```

### ❌ Bad — 서비스에서 HTTP 상태 지정

```java
@Service
public class NoteService {
    public NoteDto findById(UUID noteId) {
        // 서비스 계층에서 HTTP 404를 알면 안 됨!
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "note not found");
    }
}
```

> **이유**: 예외에 컨텍스트가 없으면 프로덕션에서 "뭐가 없다는 거야?"가 됨. 커스텀 예외 계층이 있어야 GlobalExceptionHandler에서 에러 코드를 자동 매핑할 수 있고, 로그 없는 rethrow는 스택 트레이스가 끊겨서 장애 추적이 불가능해짐.

---

## 요약 체크리스트

| # | 규칙 | 수준 |
|---|------|------|
| 3.1 | 메서드 30~40줄 이내, 초과 시 분리 | [SHOULD] |
| 3.2 | N+1 방지 — fetchJoin / @EntityGraph 필수 | [MUST] |
| 3.3 | 트랜잭션 — readOnly, 전파 레벨 명시, 서비스만 | [MUST] |
| 3.4 | AOP — cross-cutting만, 비즈니스 로직 금지 | [MUST] |
| 3.5 | Modulith 경계 — allowedDependencies, 순환 금지 | [MUST] |
| 3.6 | 예외 처리 — 커스텀 계층, 무로깅 rethrow 금지 | [SHOULD] |
