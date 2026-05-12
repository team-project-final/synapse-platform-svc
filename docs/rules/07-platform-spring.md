# 7.1 Spring Boot 4 + Modulith RULE — Platform Spring

> **프로젝트**: Synapse — 통합 학습-지식 그래프 SaaS  
> **대상**: 백엔드 개발자 (Spring Boot 4 / Java 21 / Modulith)  
> **버전**: v1.0 · 2026-05-12  
> **선행 규칙**: [07-platform.md](./07-platform.md) (의존 방향, 환경 설정, 레포 공유)

---

## 7.1.1 Modulith 모듈 선언 `[MUST]`

**모든 도메인 모듈은 `@ApplicationModule`이 선언된 `package-info.java`를 가져야 해. CI에서 `ApplicationModules.verify()`가 통과해야 머지 가능.**

### package-info.java 예시

```java
// com/synapse/knowledge/note/package-info.java
@ApplicationModule(
    allowedDependencies = { "shared" },
    displayName = "Note Module",
    type = ApplicationModule.Type.OPEN
)
package com.synapse.knowledge.note;

import org.springframework.modulith.ApplicationModule;
```

### ModuleStructureTest 예시

```java
package com.synapse.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleStructureTest {

    private final ApplicationModules modules =
        ApplicationModules.of(SynapseKnowledgeApplication.class);

    @Test
    void verifyModuleStructure() {
        modules.verify();  // ✅ 의존성 위반 있으면 여기서 터짐
    }

    @Test
    void generateDocumentation() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

### ❌ Bad — package-info.java 없이 패키지만 만들기

```java
// ❌ package-info.java 없음 → Modulith가 모듈로 인식 못 함
// 의존성 위반 무방비 상태
```

> **이유**: `package-info.java` 없으면 Modulith가 모듈로 인식 안 해서, `verify()`가 무의미해져. CI에서 강제해야 "같은 레포인데 경계 넘어가는" 사고를 잡을 수 있어.

---

## 7.1.2 DTO 정의 `[SHOULD]`

**DTO는 Java Record로 선언해. 불변 보장 + 보일러플레이트 제거.**

### ✅ Good — Record DTO

```java
// 요청 DTO
public record NoteCreateRequest(
    @NotBlank(message = "제목은 필수야")
    @Size(max = 200, message = "제목은 200자 이내")
    String title,

    @NotBlank(message = "본문은 필수야")
    String content,

    List<String> tags
) {}

// 응답 DTO
public record NoteResponse(
    Long id, String title, String content,
    List<String> tags, LocalDateTime createdAt
) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(
            note.getId(), note.getTitle(), note.getContent(),
            note.getTags(), note.getCreatedAt()
        );
    }
}
```

### ❌ Bad — class DTO (Lombok 남발)

```java
@Getter
@Setter              // ❌ 가변 DTO
@NoArgsConstructor   // ❌ 빈 상태 허용
@AllArgsConstructor
public class NoteCreateRequest {
    private String title;   // ❌ 런타임에 변경 가능
    private String content;
    private List<String> tags;
}
```

> **이유**: Record는 컴파일 타임에 불변이 보장돼. Setter 있는 DTO는 어디서든 값이 바뀔 수 있어서 디버깅 지옥이야. equals/hashCode/toString도 자동이라 코드량도 줄어.

---

## 7.1.3 Entity 규칙 `[MUST]`

**Entity에는 `@Getter`만 허용. `@Setter` 절대 금지. 상태 변경은 반드시 도메인 메서드로.**

### ✅ Good — 도메인 메서드로 상태 변경

```java
@Entity
@Table(name = "notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Note extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private NoteStatus status = NoteStatus.DRAFT;

    // ✅ 정적 팩토리 메서드
    public static Note create(String title, String content) {
        Note note = new Note();
        note.title = title;
        note.content = content;
        return note;
    }

    // ✅ 도메인 메서드 — 비즈니스 규칙 캡슐화
    public void publish() {
        if (this.status != NoteStatus.DRAFT) {
            throw new IllegalStateException("DRAFT 상태에서만 발행 가능해");
        }
        this.status = NoteStatus.PUBLISHED;
    }

    // ✅ 변경 의도가 이름에 드러남
    public void updateContent(String newTitle, String newContent) {
        this.title = newTitle;
        this.content = newContent;
    }
}
```

### ❌ Bad — Setter로 상태 변경

```java
@Entity
@Getter
@Setter  // ❌ 절대 금지!
@NoArgsConstructor
public class Note extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String content;
    private NoteStatus status;
}

// 서비스에서...
note.setStatus(NoteStatus.PUBLISHED);  // ❌ 비즈니스 규칙 없이 직접 변경
note.setTitle("");                      // ❌ 유효성 검증 없음
```

> **이유**: Setter 열면 어디서든 상태를 바꿀 수 있어서, "이 상태는 왜 바뀌었지?" 추적이 불가능해져. 도메인 메서드에 규칙을 넣으면 잘못된 상태 전이를 메서드 단위에서 막을 수 있어.

---

## 7.1.4 예외 핸들러 `[MUST]`

**`@RestControllerAdvice`는 서비스당 하나만. 도메인별 커스텀 예외 계층을 만들어서 처리해.**

### 예외 계층 구조

```
shared/exception/
├── BusinessException.java          # 추상 베이스
├── NotFoundException.java          # 404 공통
└── GlobalExceptionHandler.java     # @RestControllerAdvice

note/domain/exception/
└── NoteNotFoundException.java      # extends NotFoundException
```

### 예외 클래스 예시

```java
// 추상 베이스
@Getter
public abstract class BusinessException extends RuntimeException {
    private final String errorCode;
    private final int status;

    protected BusinessException(String errorCode, int status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}

// 404 공통
public abstract class NotFoundException extends BusinessException {
    protected NotFoundException(String errorCode, String message) {
        super(errorCode, 404, message);
    }
}

// 도메인별 구체 예외
public class NoteNotFoundException extends NotFoundException {
    public NoteNotFoundException(Long noteId) {
        super("NOTE_NOT_FOUND", "노트를 찾을 수 없어: id=" + noteId);
    }
}
```

### GlobalExceptionHandler 예시

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_FAILED", errors, LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "서버 내부 오류", LocalDateTime.now()));
    }

    public record ErrorResponse(String code, String message, LocalDateTime timestamp) {}
}
```

### ❌ Bad — 컨트롤러마다 try-catch

```java
@PostMapping("/api/v1/notes")
public ResponseEntity<?> createNote(@RequestBody NoteCreateRequest req) {
    try {                                          // ❌ 매 컨트롤러 반복
        return ResponseEntity.ok(noteService.create(req));
    } catch (NoteNotFoundException e) {
        return ResponseEntity.notFound().build();  // ❌ 에러 형식 제각각
    }
}
```

> **이유**: 컨트롤러마다 try-catch하면 에러 응답 형식이 제각각이고, 새 예외 추가할 때마다 모든 컨트롤러 수정해야 해. `@RestControllerAdvice`로 중앙화하면 일관성 + 유지보수 둘 다 잡아.

---

## 7.1.5 Gradle 빌드 `[SHOULD]`

**풀 빌드 30초 이내. CI에서는 `--no-daemon` 사용. Modulith 의존성 필수 포함.**

### ✅ Good — build.gradle.kts 예시

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.synapse"
version = "0.0.1-SNAPSHOT"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    // Spring Boot 핵심
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ✅ Spring Modulith 필수
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-events-api")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    // DB + Lombok + Test
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports { mavenBom("org.springframework.modulith:spring-modulith-bom:1.3.0") }
}

tasks.withType<Test> { useJUnitPlatform() }
tasks.withType<JavaCompile> { options.isIncremental = true }  // ✅ 빌드 속도
```

### CI 설정

```yaml
- name: Build & Test
  run: ./gradlew build --no-daemon   # ✅ CI에서는 --no-daemon
  timeout-minutes: 5
```

### ❌ Bad — 안 쓰는 의존성 + Modulith 빠짐

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")   // ❌ 안 씀
    implementation("org.springframework.boot:spring-boot-starter-websocket")  // ❌ 안 씀
    // Modulith 의존성 없음 → 모듈 검증 불가  ❌
}
```

> **이유**: 빌드 30초 넘으면 커밋을 미루게 돼. `--no-daemon`은 CI에서 재현 가능한 빌드를 보장해. 안 쓰는 의존성은 과감하게 빼.

---

## 7.1.6 API First 설계 `[SHOULD]`

**DTO 먼저 설계 → Entity는 나중. 컨트롤러 메서드 시그니처가 API 계약이야.**

### 설계 순서

```
1. API 엔드포인트 목록 (URL + Method + 역할)
2. Request/Response DTO Record 작성
3. Controller 시그니처 확정 (컴파일 되는 상태)
4. Service 구현
5. Entity + Repository (마지막)
```

### ✅ Good — 시그니처만으로 API 계약이 명확

```java
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@Valid @RequestBody NoteCreateRequest request) {
        return noteService.create(request);
    }

    @GetMapping("/{noteId}")
    public NoteResponse getById(@PathVariable Long noteId) {
        return noteService.getById(noteId);
    }

    @GetMapping
    public List<NoteResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noteService.search(keyword, page, size);
    }

    @PatchMapping("/{noteId}")
    public NoteResponse update(@PathVariable Long noteId,
                               @Valid @RequestBody NoteUpdateRequest request) {
        return noteService.update(noteId, request);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long noteId) {
        noteService.delete(noteId);
    }
}
```

### ❌ Bad — Entity를 직접 받고 반환

```java
@PostMapping("/api/v1/notes")
public Note createNote(@RequestBody Note note) {  // ❌ Entity 직접 노출!
    return noteRepository.save(note);
}
```

> **이유**: Entity 먼저 만들면 DB 스키마에 API가 종속돼. DTO 먼저 설계하면 클라이언트 관점에서 생각하게 되고, Entity 구조가 바뀌어도 API 계약은 유지돼.

---

## 7.1.7 Validation `[MUST]`

**모든 요청 DTO에 `@Valid` + Jakarta Validation 어노테이션 필수. 컨트롤러에서 수동 검증 금지.**

### ✅ Good — Jakarta Validation 활용

```java
public record NoteCreateRequest(
    @NotBlank(message = "제목은 필수야")
    @Size(min = 1, max = 200, message = "제목은 1~200자 사이")
    String title,

    @NotBlank(message = "본문은 필수야")
    @Size(max = 50_000, message = "본문은 50,000자 이내")
    String content,

    @Size(max = 10, message = "태그는 최대 10개")
    List<@Size(max = 30, message = "태그 하나는 30자 이내") String> tags
) {}

// 학습 카드 예시 — 숫자 범위 검증
public record CardCreateRequest(
    @NotBlank @Size(max = 5_000) String front,
    @NotBlank @Size(max = 5_000) String back,
    @NotNull @Min(1) @Max(5) Integer difficulty,
    @NotNull @Positive Long deckId
) {}
```

컨트롤러에서는 `@Valid`만 붙이면 끝:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public NoteResponse create(@Valid @RequestBody NoteCreateRequest request) {
    return noteService.create(request);  // ✅ 여기 도달하면 이미 검증 완료
}
```

### ❌ Bad — 수동 검증

```java
@PostMapping("/api/v1/notes")
public ResponseEntity<?> createNote(@RequestBody NoteCreateRequest request) {
    if (request.title() == null || request.title().isBlank()) {  // ❌ 수동
        return ResponseEntity.badRequest().body("제목 필수");
    }
    if (request.title().length() > 200) {                        // ❌ 빠뜨리기 쉬움
        return ResponseEntity.badRequest().body("제목 200자 초과");
    }
    // ... 10줄 더
    return ResponseEntity.ok(noteService.create(request));
}
```

> **이유**: 수동 검증은 빠뜨리기 쉽고 에러 형식이 제각각이야. Jakarta Validation + `@RestControllerAdvice`의 `MethodArgumentNotValidException` 핸들러 조합하면, 모든 검증 에러가 일관된 JSON으로 나가.

---

## 부록: 규칙 체크리스트

| # | 규칙 | 레벨 | CI 강제 |
|---|------|------|---------|
| 7.1.1 | Modulith `@ApplicationModule` + `verify()` | `[MUST]` | ✅ |
| 7.1.2 | DTO는 Java Record | `[SHOULD]` | - |
| 7.1.3 | Entity `@Setter` 금지 | `[MUST]` | ArchUnit |
| 7.1.4 | `@RestControllerAdvice` 단일 | `[MUST]` | PR 리뷰 |
| 7.1.5 | 빌드 30초 이내 + `--no-daemon` CI | `[SHOULD]` | timeout |
| 7.1.6 | API First (DTO → Entity 순서) | `[SHOULD]` | PR 리뷰 |
| 7.1.7 | `@Valid` + Jakarta Validation | `[MUST]` | ArchUnit |

---

> **이전**: [07-platform.md](./07-platform.md) — 의존 방향, 환경 설정, 레포 공유 규칙  
> **다음**: 08-testing.md (예정) — 테스트 전략, 커버리지 기준
