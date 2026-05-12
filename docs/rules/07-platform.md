# 7. 플랫폼 공통 RULE — Platform Common

> **프로젝트**: Synapse — 통합 학습-지식 그래프 SaaS  
> **대상**: 전체 백엔드 개발자 (4개 서비스 공통)  
> **버전**: v1.0 · 2026-05-12  
> **기술 스택**: Spring Boot 4 / Java 21 / Spring Modulith / PostgreSQL 16

---

## 7.0.1 의존 방향 `[MUST]`

**모든 의존은 `shared ← 각 도메인 모듈` 방향이야. 역방향 절대 금지.**

도메인 모듈끼리 직접 참조하는 것도 안 돼 — 이벤트로 연결해.

### 텍스트 다이어그램

```
┌──────────────────────────────────────────────────────┐
│                   synapse-*-svc                      │
│                                                      │
│   ┌───────┐   ┌───────┐   ┌───────┐                │
│   │ note  │   │ graph │   │chunking│   ← 도메인 모듈│
│   └──┬────┘   └──┬────┘   └──┬─────┘                │
│      │           │           │                       │
│      │    ✅ OK  │    ✅ OK  │    (shared 방향 의존)  │
│      ▼           ▼           ▼                       │
│   ┌──────────────────────────────────┐               │
│   │           shared/                │               │
│   │  BaseEntity, ApiResponse, etc.   │               │
│   └──────────────────────────────────┘               │
│                                                      │
│   note ──❌──→ graph   (모듈 간 직접 참조 금지!)      │
│   shared ──❌──→ note   (역방향 금지!)                │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### ✅ Good — shared를 의존하는 도메인 모듈

```java
// note 모듈 내부
package com.synapse.knowledge.note.domain.model;

import com.synapse.knowledge.shared.BaseEntity;  // ✅ shared ← note

public class Note extends BaseEntity {
    private String title;
    private String content;
}
```

### ❌ Bad — shared가 도메인 모듈을 참조

```java
// shared 패키지에서 note를 import
package com.synapse.knowledge.shared;

import com.synapse.knowledge.note.domain.model.Note;  // ❌ 역방향!

public class SomeSharedUtil {
    public void process(Note note) { /* ... */ }
}
```

### ❌ Bad — 도메인 모듈 간 직접 참조

```java
// graph 모듈에서 note를 직접 import
package com.synapse.knowledge.graph.application;

import com.synapse.knowledge.note.domain.model.Note;  // ❌ 모듈 간 직접 참조!

public class GraphService {
    public void linkNote(Note note) { /* ... */ }
}
```

> **이유**: 의존 방향이 꼬이면 순환 참조가 생기고, 모듈 분리(미래 MSA 전환)가 불가능해져. shared는 순수 유틸/베이스만 담고, 도메인 로직 절대 넣지 마.

---

## 7.0.2 환경 설정 `[MUST]`

**`application-{profile}.yml`로 환경별 설정 분리해. local / dev / staging / prod 4단계 필수.**

### 예시 구조

```
src/main/resources/
├── application.yml                  # 공통 설정 (서버 포트, 로깅 기본값 등)
├── application-local.yml            # 로컬 개발 (H2/docker-compose DB)
├── application-dev.yml              # 개발 서버 (공유 DB, 디버그 로깅)
├── application-staging.yml          # 스테이징 (prod와 동일 인프라, 테스트 데이터)
└── application-prod.yml             # 프로덕션 (시크릿 외부 주입)
```

### ✅ Good — 환경별 분리 + 시크릿 외부화

```yaml
# application.yml (공통)
spring:
  application:
    name: synapse-knowledge-svc
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

server:
  port: 8080

---
# application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/synapse_knowledge
    username: synapse
    password: local-only-password
  jpa:
    show-sql: true

logging:
  level:
    com.synapse: DEBUG

---
# application-prod.yml
spring:
  datasource:
    url: ${DB_URL}              # 환경 변수로 주입
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false

logging:
  level:
    com.synapse: WARN
```

### ❌ Bad — 하나의 yml에 if-else 식으로 전부 때려넣기

```yaml
# application.yml — 이러지 마
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/synapse  # prod에서도 이거 쓸 거야?
    username: admin
    password: super-secret-123                      # ❌ 시크릿 하드코딩!
```

> **이유**: 환경별 설정이 섞이면 prod에 local 설정이 올라가는 사고가 나. 시크릿은 반드시 환경 변수로 주입하고, `application-prod.yml`에 절대 평문 비밀번호 넣지 마.

---

## 7.0.3 같은 레포 공유 규칙 `[MUST]`

**Synapse는 4개 서비스 내부를 Spring Modulith로 모듈 분리해. 같은 레포에 있어도 모듈 경계를 침범하면 안 돼.**

### 서비스별 모듈 매핑

| 서비스 | 트랙 | 모듈 | 공유 레포 |
|--------|------|------|-----------|
| `synapse-knowledge-svc` | C-1 | `note`, `graph` | knowledge-svc 레포 |
| `synapse-knowledge-svc` | C-2 | `chunking`, `search` | knowledge-svc 레포 |
| `synapse-learning-svc` | D-1 | `card`, `srs` | learning-svc 레포 |
| `synapse-learning-svc` | D-2 | `ai` | learning-svc 레포 |

**핵심**: C-1(note, graph)과 C-2(chunking, search)가 같은 `knowledge-svc` 레포를 공유하고, D-1(card, srs)과 D-2(ai)가 같은 `learning-svc` 레포를 공유해. 하지만 모듈 경계는 절대 넘지 마.

### ✅ Good — allowedDependencies 명시 (package-info.java)

```java
// knowledge-svc: note 모듈의 package-info.java
@ApplicationModule(
    allowedDependencies = {
        "shared"                   // ✅ shared만 허용
    }
)
package com.synapse.knowledge.note;

import org.springframework.modulith.ApplicationModule;
```

```java
// knowledge-svc: chunking 모듈의 package-info.java
@ApplicationModule(
    allowedDependencies = {
        "shared"                   // ✅ shared만 허용
    }
)
package com.synapse.knowledge.chunking;

import org.springframework.modulith.ApplicationModule;
```

### ❌ Bad — 다른 트랙의 모듈을 직접 참조

```java
// C-2 트랙의 chunking 모듈에서 C-1 트랙의 note를 직접 import
package com.synapse.knowledge.chunking.application;

import com.synapse.knowledge.note.domain.model.Note;  // ❌ 모듈 경계 침범!

public class ChunkingService {
    public void chunkNote(Note note) { /* ... */ }
}
```

### ✅ Good — 이벤트 기반으로 모듈 간 통신

```java
// note 모듈에서 이벤트 발행
package com.synapse.knowledge.note.application;

import org.springframework.context.ApplicationEventPublisher;

public class NoteService {
    private final ApplicationEventPublisher events;

    public void createNote(NoteCreateRequest req) {
        Note note = Note.create(req.title(), req.content());
        noteRepository.save(note);
        events.publishEvent(new NoteCreatedEvent(note.getId(), note.getContent()));
        // ✅ chunking 모듈은 이 이벤트를 구독해서 처리
    }
}

// chunking 모듈에서 이벤트 수신
package com.synapse.knowledge.chunking.application;

import com.synapse.knowledge.note.domain.event.NoteCreatedEvent;

@EventListener
public void onNoteCreated(NoteCreatedEvent event) {
    // ✅ 이벤트의 public API만 사용 — note 내부 구현에 의존 안 함
    chunkContent(event.noteId(), event.content());
}
```

> **이유**: 같은 레포에 있으면 IDE가 import 자동완성을 해주니까 경계를 넘기 쉬워. `allowedDependencies`를 명시하고 `ApplicationModules.verify()`를 CI에서 돌리면, 경계 침범을 컴파일 타임에 잡을 수 있어. 이벤트 기반 통신이 모듈 분리의 핵심이야.

---

> **다음**: [7.1 Spring Boot 4 + Modulith RULE](./07-platform-spring.md)에서 구체적인 Spring 컨벤션을 다뤄.
