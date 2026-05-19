# TASK — Refactor: 템플릿 기준 패키지/디렉토리 구조 정렬

> 출처: Director 지시 (TASK_platform.md 외 작업)

## 상태

- Phase: 구현
- 담당 Agent: Worker
- 시작일: 2026-05-19
- 목표 완료일: 2026-05-19

---

## Step Goal

`synapse-platform-svc`의 패키지·디렉토리 구조를 팀 표준 템플릿(`docs/synapse-svc-template-skeleton-platform-w1`)에 맞게 정렬한다. 기능 코드는 변경하지 않는다.

## Done When

- [ ] `io.synapse.platform` → `com.synapse.platform` 전체 치환 완료
- [ ] `shared/` → `global/` 이름 변경 완료
- [ ] `auth/jwt/`, `auth/mfa/`, `auth/oauth/`, `auth/domain/` 평탄화 완료
- [ ] `user/domain/` → `user/entity/` 변경 완료
- [ ] `billing/domain/` → `billing/entity/` 변경 완료
- [ ] `billing/dto/` → `billing/dto/request/` + `billing/dto/response/` 분리 완료
- [ ] `auth/api/`, `user/api/` 위치 유지 확인
- [ ] `.\gradlew.bat clean build` 통과
- [ ] `.\gradlew.bat test` 전체 그린
- [ ] `PlatformModuleStructureTest` 통과

## Scope

- In Scope:
  - package 선언 변경
  - import 경로 변경
  - 디렉토리 이동/이름 변경
  - `build.gradle.kts` group 변경
- Out of Scope:
  - 비즈니스 로직 변경
  - 의존성 추가/제거
  - 테스트 로직 변경
  - SQL 마이그레이션 파일 변경

## Input

- `docs/synapse-svc-template-skeleton-platform-w1/` — 목표 구조 레퍼런스
- `docs/ai/current/HANDOFF.md` — Phase별 상세 실행 스펙
- `docs/ai/current/CONTEXT.md` — 확정된 제약 및 불변 항목

## Instructions

1. Phase 1: `io.synapse` → `com.synapse` 전체 치환 + 디렉토리 이동 → `compileJava` 확인 → 커밋
2. Phase 2: `shared/` → `global/` 이름 변경 → `compileJava` 확인 → 커밋
3. Phase 3: `auth` 모듈 서브패키지 평탄화 → `compileJava` + auth 테스트 → 커밋
4. Phase 4: `user` 모듈 정렬 → `compileJava` + user 테스트 → 커밋
5. Phase 5: `billing` 모듈 정렬 → `compileJava` + billing 테스트 → 커밋
6. Phase 7: `clean build` + 전체 테스트 통과 확인

## Constraints

- `auth/api/package-info.java`, `user/api/package-info.java` 절대 이동 금지
- Phase별 `compileJava` 통과 후 다음 Phase로 진행
- 커밋은 Phase 단위로 분리

## Duration

0.5일

## Assignee / Reviewer

- Assignee: @platform-owner (Worker)
- Reviewer: @team-lead (Director)
