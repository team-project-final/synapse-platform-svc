# TASK — Step 1 점검: platform-svc 골격 수정

> 출처: TASK_platform.md Step 1 (신규 문서 기준 점검)

## 상태

- Phase: 완료
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-18
- 목표 완료일: 2026-05-18

---

## Step Goal

Step 1 구현물을 신규 개발문서 기준으로 점검하여 누락·불일치 항목을 수정한다.

## Done When

- [x] `audit/package-info.java` 생성 + @ApplicationModule 선언 완료
- [x] `billing/package-info.java` 생성 + @ApplicationModule 선언 완료
- [x] `ModuleStructureTest.java` → `ApplicationModulesTest.java` 로 rename 완료
- [x] `ApplicationModules.of(...).verify()` 테스트 통과

## Scope

- In Scope:
  - audit/package-info.java 신규 생성
  - billing/package-info.java 신규 생성
  - 테스트 클래스 rename (기능 변경 없음)
- Out of Scope:
  - 비즈니스 로직 변경
  - Step 2/3 코드 수정
  - 문서 merge conflict 해소 (별도 처리)

## Input

- `docs/ai/current/HANDOFF.md` (Worker 전달 문서)
- `docs/ai/current/CONTEXT.md`

## Instructions

1. `audit/package-info.java` 생성
2. `billing/package-info.java` 생성
3. `src/test/.../ModuleStructureTest.java` → `ApplicationModulesTest.java` rename
4. 테스트 통과 확인

## Constraints

- package-info.java는 기존 auth/notification 패턴 동일하게 작성
- allowedDependencies는 Step 1 단계 최소값 (shared만)
- 클래스 내부 로직 변경 없이 파일명만 rename

## Duration

0.5일 미만
