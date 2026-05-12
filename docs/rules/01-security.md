# 1. 보안 RULE — Security

> **참조**: [전체 Rule 목록](../rules/) | [준수 체크리스트](appendix-c-checklist.md)

---

## 1.1 Secrets 관리 \[MUST\]

`.env`, `*.key`, `*.pem` 파일은 **절대 커밋하지 마**.
GitHub PAT는 반드시 **fine-grained** 토큰만 사용해. classic PAT 쓰면 안 돼.
`MIRROR_TOKEN`, `GITOPS_TOKEN`은 **90일 주기**로 로테이션해야 해.

### .gitignore 필수 항목

```gitignore
# Secrets — 절대 커밋 금지
.env
.env.*
*.key
*.pem
*.p12
*.jks
```

### 예시

```yaml
# ✅ Good — GitHub Actions에서 secrets 참조
env:
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
  MIRROR_TOKEN: ${{ secrets.MIRROR_TOKEN }}
```

```java
// ❌ Bad — 하드코딩된 시크릿
@Value("sk-live-abc123xyz")
private String apiKey;
```

> **이유**: 시크릿이 Git 히스토리에 한 번이라도 남으면 영구적으로 노출돼. BFG로 제거해도 포크/미러에 남을 수 있어.

---

### 1.1.1 Secrets Rotation 정책 \[MUST\]

시크릿은 종류별로 로테이션 주기가 달라. Grace Period 동안 이전 키와 새 키를 **동시 허용**해서 무중단 전환해.

| 시크릿 종류 | 로테이션 주기 | Grace Period | 비고 |
|---|---|---|---|
| DB 비밀번호 | 30 ~ 90일 | 1시간 | Spring Vault 연동 권장 |
| API 키 (외부 서비스) | 90일 | 24시간 | 클라이언트 전파 시간 고려 |
| JWT 서명 키 (RSA) | 90 ~ 180일 | 24시간 | `kid` 헤더로 키 식별 |
| MIRROR_TOKEN | 90일 | 1시간 | GitHub Actions secret 갱신 |
| GITOPS_TOKEN | 90일 | 1시간 | ArgoCD 재동기화 필요 |

```java
// ✅ Good — kid 기반 다중 키 지원으로 무중단 로테이션
@Component
public class JwtKeyProvider {
    private final Map<String, RSAPublicKey> publicKeys;

    public RSAPublicKey resolve(String kid) {
        RSAPublicKey key = publicKeys.get(kid);
        if (key == null) throw new InvalidKeyException("Unknown kid: " + kid);
        return key;
    }
}
```

```java
// ❌ Bad — 단일 키만 지원, 로테이션 시 다운타임 발생
@Value("${jwt.secret}")
private String jwtSecret;
```

> **이유**: Grace Period 없이 키를 바꾸면 기존 토큰이 즉시 무효화돼서 사용자가 전부 로그아웃돼.

---

## 1.2 접근 제어 \[MUST\]

### IDOR 방지

노트, 카드, 덱 등 모든 리소스 접근 시 `userId == currentUser`를 반드시 체크해.
인증 실패(401)와 권한 없음(403)은 명확히 구분해서 응답해.

| HTTP 상태 | 의미 | 사용 시점 |
|---|---|---|
| `401 Unauthorized` | 미인증 | 토큰 없음 / 만료 |
| `403 Forbidden` | 권한 없음 | 인증됐지만 해당 리소스 접근 불가 |

```java
// ✅ Good — IDOR 방지: 리소스 소유자 검증
@GetMapping("/api/notes/{noteId}")
public ResponseEntity<NoteResponse> getNote(
        @PathVariable Long noteId,
        @AuthenticationPrincipal UserPrincipal principal) {

    Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new ResourceNotFoundException("Note not found"));

    if (!note.getUserId().equals(principal.getId())) {
        throw new AccessDeniedException("이 노트에 접근 권한이 없습니다");  // 403
    }
    return ResponseEntity.ok(NoteResponse.from(note));
}
```

```java
// ❌ Bad — 소유자 검증 없이 ID만으로 조회
@GetMapping("/api/notes/{noteId}")
public ResponseEntity<NoteResponse> getNote(@PathVariable Long noteId) {
    Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
    return ResponseEntity.ok(NoteResponse.from(note));
}
```

> **이유**: IDOR는 OWASP A01 접근 제어 취약점의 대표적인 사례야. `noteId`만 바꿔서 남의 데이터를 볼 수 있으면 끝장이야.

---

## 1.3 CORS \[MUST\]

CORS는 **화이트리스트 방식**으로만 설정해. `Access-Control-Allow-Origin: *`는 절대 금지.
`localhost` 허용은 **dev 프로파일에서만** 활성화해.

```java
// ✅ Good — 화이트리스트 기반 CORS 설정
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

```yaml
# application-dev.yml
cors:
  allowed-origins:
    - http://localhost:3000
    - http://localhost:5173

# application-prod.yml
cors:
  allowed-origins:
    - https://synapse.example.com
    - https://app.synapse.example.com
```

```java
// ❌ Bad — 와일드카드 오리진
registry.addMapping("/**")
        .allowedOrigins("*")
        .allowedMethods("*");
```

> **이유**: `*` 쓰면 아무 도메인에서나 API를 때릴 수 있어. CSRF 공격에 문을 활짝 열어주는 거야.

---

## 1.4 입력 검증 \[SHOULD\]

### 마크다운 XSS

사용자가 입력한 마크다운에서 `<script>`, `onerror`, `javascript:` 등을 필터링해.

```java
// ✅ Good — OWASP Java HTML Sanitizer로 마크다운 XSS 방지
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

@Service
public class MarkdownSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES);

    public String sanitize(String rawMarkdown) {
        return POLICY.sanitize(rawMarkdown);
    }
}
```

```java
// ❌ Bad — 사용자 입력을 그대로 저장
public String save(String rawMarkdown) {
    return noteRepository.save(new Note(rawMarkdown)).getContent();
}
```

> **이유**: 마크다운 에디터 입력은 결국 HTML로 렌더링돼. 필터링 안 하면 XSS 공격에 노출돼.

### SQL Injection 방지

JPA를 쓸 때도 **파라미터 바인딩**을 반드시 사용해. 네이티브 쿼리에서 문자열 concat은 금지야.

```java
// ✅ Good — 파라미터 바인딩
@Query("SELECT n FROM Note n WHERE n.title LIKE %:keyword% AND n.userId = :userId")
List<Note> searchNotes(@Param("keyword") String keyword, @Param("userId") Long userId);
```

```java
// ❌ Bad — 문자열 연결 (SQL Injection 취약)
@Query(value = "SELECT * FROM notes WHERE title LIKE '%" + keyword + "%'", nativeQuery = true)
List<Note> searchNotes(String keyword);
```

> **이유**: JPA가 기본적으로 파라미터 바인딩을 지원하는데도 문자열 concat을 쓰면 SQL Injection에 무방비야.

### Avro 스키마 검증

Kafka 메시지를 주고받을 때 Avro 스키마 레지스트리로 **메시지 구조를 검증**해.
스키마에 맞지 않는 메시지는 즉시 reject해.

```java
// ✅ Good — Avro 직렬화 + Schema Registry
@Bean
public ProducerFactory<String, GenericRecord> producerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put("schema.registry.url", schemaRegistryUrl);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            KafkaAvroSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
}
```

```java
// ❌ Bad — JSON 문자열로 직접 전송, 스키마 검증 없음
kafkaTemplate.send("topic", objectMapper.writeValueAsString(event));
```

> **이유**: 스키마 검증 없이 메시지를 주고받으면 한쪽이 구조를 바꿨을 때 역직렬화 실패가 런타임에 터져.

---

## 1.5 OWASP Top 10 (2025) 매핑

Synapse 프로젝트에서 특히 주의해야 할 OWASP 항목과 대응 방안이야.

| OWASP 2025 | 위협 설명 | Synapse 대응 |
|---|---|---|
| **A01 — Broken Access Control** | 인증된 사용자가 권한 밖의 리소스에 접근 | IDOR 검증 (1.2절), 노트/카드/덱 소유자 체크, 역할 기반 접근 제어 |
| **A06 — Vulnerable & Outdated Components** | 취약한 라이브러리/프레임워크 사용 | `dependabot` 활성화, `./gradlew dependencyCheckAnalyze` 주기 실행, Spring Boot BOM 최신 유지 |
| **A10 — Server-Side Request Forgery (SSRF)** | 서버 측 요청 위조 | — |

### A01 — Broken Access Control 상세 대응

```java
// ✅ Good — 메서드 시큐리티로 역할 기반 접근 제어
@PreAuthorize("hasRole('ADMIN') or @deckAccessChecker.isOwner(#deckId, principal)")
@DeleteMapping("/api/decks/{deckId}")
public ResponseEntity<Void> deleteDeck(@PathVariable Long deckId) {
    deckService.delete(deckId);
    return ResponseEntity.noContent().build();
}
```

```java
// ❌ Bad — 역할 검사 없는 관리자 API
@DeleteMapping("/api/admin/users/{userId}")
public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
    userService.delete(userId);
    return ResponseEntity.noContent().build();
}
```

> **이유**: 접근 제어가 없으면 URL만 알면 누구든 관리자 기능을 실행할 수 있어.

### A06 — Vulnerable & Outdated Components 상세 대응

```groovy
// ✅ Good — build.gradle에 OWASP Dependency-Check 플러그인 적용
plugins {
    id 'org.owasp.dependencycheck' version '9.2.0'
}

dependencyCheck {
    failBuildOnCVSS = 7.0f  // CVSS 7.0 이상이면 빌드 실패
    formats = ['HTML', 'JSON']
}
```

```groovy
// ❌ Bad — 의존성 버전 고정 안 하고 취약점 스캔도 없음
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:+'
}
```

> **이유**: 최신 버전을 자동으로 땡기면 검증 안 된 버전이 프로덕션에 들어갈 수 있어. 취약점 스캔도 없으면 알려진 CVE에 무방비야.

### A10 — 예외 처리 (Server-Side)

내부 스택 트레이스가 클라이언트에 노출되면 안 돼. `@RestControllerAdvice`로 글로벌 예외 핸들링해.

```java
// ✅ Good — 글로벌 예외 핸들러로 내부 정보 숨기기
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);  // 서버 로그에만 기록
        return ResponseEntity.status(500)
                .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }
}
```

```java
// ❌ Bad — 스택 트레이스가 그대로 클라이언트에 노출
@GetMapping("/api/notes/{id}")
public Note getNote(@PathVariable Long id) {
    return noteRepository.findById(id).get();  // NoSuchElementException → 500 + 스택 트레이스
}
```

> **이유**: 스택 트레이스에는 클래스명, DB 스키마, 라이브러리 버전 등 공격자에게 유용한 정보가 가득해.

---

_마지막 업데이트: 2026-05-12_
