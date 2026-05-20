# TASK — W1~W2 완료 기준 점검

> 출처: Director 판단 (2026-05-20) — TASK_platform.md Step 외 점검 태스크

## 상태

- Phase: 점검
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-20
- 목표 완료일: 2026-05-20

---

## Step Goal

W3 착수 전, W1~W2 리팩토링(멀티모듈 → Spring Modulith 전환, 패키지 재편)으로 인한 Done When 미충족 항목을 발견하고 수정한다.

## Done When

- [ ] `./gradlew test --tests "*ModuleStructureTest"` 통과
- [ ] 각 모듈 package-info.java 전부 존재 (auth, user, billing, notification, global, admin)
- [ ] Step 2 Done When 항목 전부 코드로 확인 (OAuth 3종 + oauth_identities + 테넌트 자동 생성)
- [ ] Step 3 Done When 항목 전부 코드로 확인 (JWT 만료시간 + Refresh 이중저장 + 엔드포인트 3개)
- [ ] Step 4 Done When 항목 전부 코드로 확인 (엔드포인트 3개 + Webhook 서명 + processed_events)
- [ ] Step 5 Done When 항목 전부 코드로 확인 (엔드포인트 2개 + device_tokens 스키마)
- [ ] `./gradlew check` 전체 통과
- [ ] JaCoCo 라인 커버리지 80% 이상
- [ ] 미충족 항목 발견 시 수정 후 재검증 통과
- [ ] 점검 결과를 HANDOFF.md에 항목별 OK/NG로 기록

## Scope

- In Scope:
  - W1 Step 1~3, W2 Step 4~5 Done When 기준 코드 검증
  - 미충족 항목 수정
  - 빌드 + 테스트 + 커버리지 검증
- Out of Scope:
  - 새 기능 추가
  - W3 Step 6 이후 작업
  - 성능 최적화

## Input

- docs/project-management/task/TASK_platform.md (Step 1~5 Done When)
- docs/ai/current/CONTEXT.md
- docs/ai/current/HANDOFF.md

## Instructions

HANDOFF.md 참조

## Constraints

- 수정은 미충족 항목만 — 동작 중인 코드 불필요하게 건드리지 않기
- 수정 발생 시 관련 테스트도 함께 확인
