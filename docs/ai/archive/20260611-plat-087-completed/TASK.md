# PLAT-087 ReviewCompleted audit DLT 원인 확정 및 owner 이관 정리

## Task ID
PLAT-087

## Title
platform 이슈 #87 `ReviewCompleted` DLT 원인 확정 및 shared 정본 유지 결정 기록

## Owner
platform

## Status
DONE

## Priority
P1

## Issue
https://github.com/team-project-final/synapse-platform-svc/issues/87

## Branch
`fix/PLAT-087-review-completed-contract`

## Base
`origin/main`

## Step Goal
platform 이슈 #87에서 관찰된 `ReviewCompleted` audit DLT의 원인을 정리하고, platform이 shared 정본을 유지해야 한다는 결정을 문서화한다.

초기에는 platform audit consumer를 learning 최신 실제 발행 스키마에 맞추는 방향을 검토했지만, shared 최신 정본과 팀장님 답변 확인 결과 platform 스키마 변경은 부적절하다. 근본 수정 owner는 learning-card이며, platform은 `com.synapse.learning.ReviewCompleted` 정본을 유지한다.

## Done When
- [x] shared 최신 main을 pull 받아 최신 상태를 확인한다.
- [x] shared `ReviewCompleted` 정본을 확인한다.
- [x] learning 최신 실제 발행 스키마와 shared 정본의 차이를 확인한다.
- [x] platform 현재 스키마가 shared 정본과 일치함을 확인한다.
- [x] 팀장님 답변 기준으로 platform 정본 유지 결정을 반영한다.
- [x] platform 이슈 #87에 원인, 근거, 권고를 코멘트로 정리한다.
- [x] `docs/project-management/history/HISTORY_platform.md`에 조사/결정 이력을 기록한다.

## Scope

### In Scope
- platform repo 문서 정리
- platform 이슈 #87 답변 준비
- shared, learning, platform 간 `ReviewCompleted` Avro 계약 대조
- platform이 shared 정본을 유지해야 하는 근거 정리

### Out of Scope
- platform `ReviewCompleted.avsc`를 `com.synapse.event.learning`으로 변경
- platform consumer import를 `com.synapse.event.learning.ReviewCompleted`로 변경
- learning/shared/gitops/frontend/gateway repo 코드 수정
- Kafka topic 이름 변경
- Schema Registry subject 리셋 또는 v2 topic 전환 작업
- live 환경 직접 배포 또는 운영 데이터 변경
- `TASK_platform.md` 개발 목록 수정

## Verified Evidence

### shared 정본
- Repo: `C:\workspace\team_project_2\synapse-shared`
- Branch: `main`
- Latest checked commit: `ada85ed`
- Avro: `src/main/avro/learning/ReviewCompleted.avsc`
- Standard: `docs/guides/EVENT_CONTRACT_STANDARD.md`

shared 기준:
- namespace: `com.synapse.learning`
- `reviewedAt`: `string`
- `occurredAt`: plain `long`
- `com.synapse.event.*` namespace는 폐기 대상
- `timestamp-millis` logicalType은 미사용

### platform 현재 상태
- Avro: `src/main/avro/learning/ReviewCompleted.avsc`
- Consumer: `src/main/java/com/synapse/platform/audit/consumer/AuditKafkaConsumer.java`
- Service: `src/main/java/com/synapse/platform/audit/service/AuditLogService.java`

platform 기준:
- namespace: `com.synapse.learning`
- `reviewedAt`: `string`
- `occurredAt`: `long`
- shared 정본과 일치

### learning 최신 실제 발행자
- Repo: `C:\workspace\team_project_2\synapse-learning-svc`
- Branch: `main`
- Latest checked commit: `e5e27ee`
- Avro: `learning-card/src/main/avro/learning/ReviewCompleted.avsc`
- Publisher: `learning-card/src/main/java/com/synapse/learning/srs/adapter/out/event/CardReviewedEventPublisher.java`

learning 실제 발행 기준:
- namespace: `com.synapse.event.learning`
- `reviewedAt`: `long` + `logicalType: timestamp-millis`
- `occurredAt`: `long` + `logicalType: timestamp-millis`

learning 쪽 검증:
```powershell
cd C:\workspace\team_project_2\synapse-learning-svc\learning-card
.\gradlew.bat test --tests "*KafkaEventFlowE2ETest"
```

결과:
- `tests="3"`
- `failures="0"`
- `errors="0"`
- `skipped="0"`

## Conclusion
platform 이슈 #87 DLT는 platform이 shared 정본을 어겨서 발생한 문제가 아니다.

현재 근본 원인은 learning 최신 실제 발행 스키마가 shared 정본과 갈라진 것이다. platform audit consumer는 specific Avro reader를 사용하므로, full name과 필드 타입 계약이 다른 이벤트를 정상적으로 읽기 어렵다.

## Recommendation
- platform은 `com.synapse.learning.ReviewCompleted` 정본을 유지한다.
- platform에서 `com.synapse.event.learning`으로 맞추는 변경은 하지 않는다.
- 근본 수정은 learning-card producer가 shared 정본에 맞추는 것이다.
- 변경 대상:
  - namespace: `com.synapse.event.learning` -> `com.synapse.learning`
  - `reviewedAt`: `timestamp-millis` -> ISO-8601 `string`
  - `occurredAt`: `timestamp-millis` -> plain `long`
- 단, `string <-> long` 변경은 BACKWARD 비호환이므로 신규 topic v2 전환 또는 개발 환경 subject 리셋/오프셋 폐기 같은 운영 결정이 필요하다.

## Test Plan
platform 코드 변경이 없으면 빌드/단위 테스트는 필수 검증 대상이 아니다.

코드 변경 없이 이슈 정리만 하는 경우:
```powershell
git diff -- docs/ai/current/TASK.md docs/ai/current/CONTEXT.md docs/ai/current/HANDOFF.md
```

platform 방어 로직을 별도 요청받는 경우에만 아래 테스트를 수행한다.
```powershell
.\gradlew.bat test --tests "*AuditConsumerIntegrationTest"
.\gradlew.bat clean build
```

## Constraints
- 다른 repo는 읽기/비교만 하고 수정하지 않는다.
- `.env`, profile, 운영 설정은 수정하지 않는다.
- shared 정본을 platform에서 임의 변경하지 않는다.
- `TASK_platform.md`는 초기 개발 목록 문서이므로 수정하지 않는다.
- PR/issue 본문은 반드시 UTF-8 파일 기반으로 작성한다.

## Notes
- 이전 current 문서는 `docs/ai/archive/20260611-plat-086-completed/`에 보관했다.
- platform 이슈 #87에 조사 결과를 코멘트로 남기고 close 완료했다.
- 현재 작업은 코드 수정 없이 원인 확정 및 이슈 답변 정리로 마무리했다.
