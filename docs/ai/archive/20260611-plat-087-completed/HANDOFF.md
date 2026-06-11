# PLAT-087 Handoff

## Status
완료. platform 이슈 #87에 조사 결과를 남기고 close 처리했다. 코드 수정은 하지 않는다.

## Branch
- Base: `origin/main`
- Working branch: `fix/PLAT-087-review-completed-contract`

## Archived
이전 current 문서는 아래 경로에 보관했다.

- `docs/ai/archive/20260611-plat-086-completed/TASK.md`
- `docs/ai/archive/20260611-plat-086-completed/CONTEXT.md`
- `docs/ai/archive/20260611-plat-086-completed/HANDOFF.md`

## Current Task
platform 이슈 #87 `ReviewCompleted` audit DLT의 원인을 확정하고 이슈 답변을 준비한다.

결론:
- platform은 shared 정본을 따르고 있다.
- learning 최신 실제 발행 스키마가 shared 정본과 다르다.
- platform 스키마를 learning drift에 맞추면 안 된다.
- 근본 수정 owner는 learning-card다.

## Key Comparison

```text
shared 정본:
  com.synapse.learning.ReviewCompleted
  reviewedAt = string
  occurredAt = long

platform consumer:
  com.synapse.learning.ReviewCompleted
  reviewedAt = string
  occurredAt = long

learning producer:
  com.synapse.event.learning.ReviewCompleted
  reviewedAt = timestamp-millis
  occurredAt = timestamp-millis
```

## Team Lead Decision
팀장님 답변 기준:

- platform은 정본 유지가 맞다.
- 근본 수정은 learning 발행 스키마를 정본에 정렬하는 것이다.
- `EVENT_CONTRACT_STANDARD`가 `com.synapse.event.*` namespace와 `timestamp-millis`를 폐기 대상으로 명시하고 있다.
- 단, `reviewedAt`의 `string <-> long` 변경은 BACKWARD 비호환이므로 신규 topic v2 전환 또는 개발 환경 subject 리셋/오프셋 폐기 같은 결정이 필요하다.

## Implementation Checklist
- [x] shared 최신 main pull 확인
- [x] shared `ReviewCompleted` 정본 확인
- [x] platform `ReviewCompleted`가 shared 정본과 일치함 확인
- [x] learning 최신 producer drift 확인
- [x] 작업문서 방향 정정
- [x] platform 이슈 #87 답변 작성
- [x] `HISTORY_platform.md`에 조사 이력 기록

## Relevant Files

Platform:
- `src/main/avro/learning/ReviewCompleted.avsc`
- `src/main/java/com/synapse/platform/audit/consumer/AuditKafkaConsumer.java`
- `src/main/java/com/synapse/platform/audit/service/AuditLogService.java`
- `docs/project-management/history/HISTORY_platform.md`

Shared, read-only:
- `C:\workspace\team_project_2\synapse-shared\src\main\avro\learning\ReviewCompleted.avsc`
- `C:\workspace\team_project_2\synapse-shared\docs\guides\EVENT_CONTRACT_STANDARD.md`
- `C:\workspace\team_project_2\synapse-shared\docs\fix-requests\owner-followups\platform-audit-reviewcompleted-dlt.md`

Learning, read-only:
- `C:\workspace\team_project_2\synapse-learning-svc\learning-card\src\main\avro\learning\ReviewCompleted.avsc`
- `C:\workspace\team_project_2\synapse-learning-svc\learning-card\src\main\java\com\synapse\learning\srs\adapter\out\event\CardReviewedEventPublisher.java`

## Test Commands
코드 변경 없음 기준으로 테스트 실행은 필수 아님.

문서 diff 확인:
```powershell
git diff -- docs/ai/current/TASK.md docs/ai/current/CONTEXT.md docs/ai/current/HANDOFF.md
```

platform 방어 로직을 추가 요청받는 경우:
```powershell
.\gradlew.bat test --tests "*AuditConsumerIntegrationTest"
.\gradlew.bat clean build
```

## Do Not Touch
- learning repo code
- shared repo code
- gitops repo code
- frontend/gateway/engagement repo code
- `.env`
- Spring profile
- 운영 DB
- `TASK_platform.md`

## Issue Response Direction
platform 이슈 #87에는 아래 형태로 답변한다.

```text
3레포 대조 결과 platform은 shared 정본(com.synapse.learning, reviewedAt string, occurredAt long)을 따르고 있습니다.
DLT 원인은 learning 최신 실제 발행 스키마가 shared 정본과 다르게 com.synapse.event.learning 및 timestamp-millis를 사용하기 때문으로 보입니다.
따라서 platform 스키마를 바꾸는 것은 정본 위반이므로 하지 않는 것이 맞고, 근본 수정은 learning-card producer를 shared 정본에 정렬하는 방향입니다.
단 reviewedAt의 string/long 변경은 BACKWARD 비호환이라 v2 topic 또는 개발 환경 subject 리셋 같은 운영 결정이 필요합니다.
```

platform 코드 수정 PR은 필요하지 않다. 현재 변경은 조사 문서 정리 성격이다.
