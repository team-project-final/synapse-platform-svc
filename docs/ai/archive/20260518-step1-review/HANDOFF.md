# HANDOFF — Step 1 점검 수정

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Director (Claude)

## TO

Worker (Codex)

## 작업 배경

Step 1~3이 구현 완료된 상태에서, 팀장이 개발문서를 전면 리뉴얼했다.
Director가 Step 1을 신규 문서 기준으로 점검한 결과 3건의 수정이 필요하다.

---

## 수정 항목

### [FIX-1] audit/package-info.java 생성

**경로**: `src/main/java/com/synapse/platform/audit/package-info.java`

**이유**: git에서 삭제(`D` 상태)되어 Modulith가 audit를 정식 모듈로 인식하지 못함.

**작성 내용** (auth/notification 패턴 동일):
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Audit",
    allowedDependencies = {"shared"}
)
package com.synapse.platform.audit;
```

---

### [FIX-2] billing/package-info.java 생성

**경로**: `src/main/java/com/synapse/platform/billing/package-info.java`

**이유**: 파일 자체가 없어 Modulith 모듈 등록 안 됨.

**작성 내용**:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Billing",
    allowedDependencies = {"shared"}
)
package com.synapse.platform.billing;
```

---

### [FIX-3] ModuleStructureTest → ApplicationModulesTest rename

**현재 경로**: `src/test/java/com/synapse/platform/ModuleStructureTest.java`
**변경 경로**: `src/test/java/com/synapse/platform/ApplicationModulesTest.java`

**이유**: TASK_platform.md Step 1 Done When에 `ApplicationModulesTest` 명시. 파일명 + 클래스명 동일하게 변경.

**변경 내용** (파일명 + 클래스명만, 로직 동일):
```java
package com.synapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void verifyModuleStructure() {
        ApplicationModules.of(PlatformSvcApplication.class).verify();
    }
}
```

기존 `ModuleStructureTest.java`는 삭제.

---

## 완료 기준

- [x] `./gradlew test --tests "*.ApplicationModulesTest"` 통과
- [x] `ModuleStructureTest.java` 파일 없음 확인

## 주의사항

- 비즈니스 로직, Step 2/3 코드에는 손대지 않음
- allowedDependencies는 현재 단계 최소값 유지 (Step 진행 시 추가 예정)

## 커밋 메시지

```
chore(infra): Step 1 점검 — audit/billing package-info 복구 + 테스트 클래스명 수정
```

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-18
