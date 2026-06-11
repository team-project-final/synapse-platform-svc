# PLAT-087 Context

## Current State
- Working branch: `fix/PLAT-087-review-completed-contract`
- Base: `origin/main`
- Target issue: platform 이슈 #87
- 작업 대상 repo: `synapse-platform-svc`
- 다른 repo는 최신 상태 확인과 비교만 수행한다.
- 팀장님 답변 기준으로 platform 정본 유지 방향이 확정됐다.

## Problem Summary
platform 이슈 #87은 learning의 `ReviewCompleted` 이벤트가 platform audit consumer에서 DLT로 이동한 현상이다.

초기 가설은 두 가지였다.

1. platform audit consumer가 실제 learning 발행 스키마를 못 읽는 실제 결함
2. 합성 이벤트 직접 주입 때문에 발생한 false alarm

3레포 소스 대조 결과, 가설 A에 가깝다. 다만 책임 방향은 platform 스키마 변경이 아니라 learning producer 정렬이다.

## Three Repo Contract Comparison

| Repo | 기준 | namespace | reviewedAt | occurredAt | 판단 |
|---|---|---|---|---|---|
| shared | 정본 | `com.synapse.learning` | `string` | plain `long` | 표준 |
| platform | consumer | `com.synapse.learning` | `string` | plain `long` | shared 정본과 일치 |
| learning | producer | `com.synapse.event.learning` | `timestamp-millis` | `timestamp-millis` | shared 정본과 불일치 |

## Shared Contract Checked
경로:
`C:\workspace\team_project_2\synapse-shared\src\main\avro\learning\ReviewCompleted.avsc`

정본:
```text
namespace = com.synapse.learning
reviewedAt = string
occurredAt = long
```

표준 문서:
`C:\workspace\team_project_2\synapse-shared\docs\guides\EVENT_CONTRACT_STANDARD.md`

핵심 기준:
- namespace는 `com.synapse.*`로 통일
- `com.synapse.event.*`는 폐기 대상
- `occurredAt`은 plain `long`
- `logicalType: timestamp-millis`는 미사용
- 도메인 시각은 ISO-8601 `string`

## Platform Contract Checked
경로:
`C:\workspace\team_project_2\synapse-platform-svc\src\main\avro\learning\ReviewCompleted.avsc`

현재 platform:
- namespace: `com.synapse.learning`
- `reviewedAt`: `string`
- `occurredAt`: `long`
- consumer/service import: `com.synapse.learning.ReviewCompleted`
- `specific.avro.reader=true`

platform은 shared 정본을 따르고 있다.

## Learning Contract Checked
경로:
`C:\workspace\team_project_2\synapse-learning-svc\learning-card\src\main\avro\learning\ReviewCompleted.avsc`

learning 최신 실제 발행:
- namespace: `com.synapse.event.learning`
- `reviewedAt`: `long` + `logicalType: timestamp-millis`
- `occurredAt`: `long` + `logicalType: timestamp-millis`

발행자:
`C:\workspace\team_project_2\synapse-learning-svc\learning-card\src\main\java\com\synapse\learning\srs\adapter\out\event\CardReviewedEventPublisher.java`

learning 테스트:
```powershell
.\gradlew.bat test --tests "*KafkaEventFlowE2ETest"
```

결과:
- 성공
- producer 내부 기준 이벤트 발행 테스트는 통과

## DLT Mechanism
platform audit consumer는 specific Avro reader를 사용한다.

문제 지점:
- namespace mismatch: `com.synapse.event.learning.ReviewCompleted` vs `com.synapse.learning.ReviewCompleted`
- `reviewedAt` type mismatch: writer `long`, reader `string`
- `occurredAt`: 둘 다 물리 타입은 `long`이라 상대적으로 덜 치명적이지만 logicalType 정책 위반

이 조합에서는 platform이 shared 정본을 유지해도 learning 실제 발행 이벤트를 읽지 못해 DLT로 빠질 수 있다.

## Why Registry BACKWARD Did Not Prevent It
shared 표준은 `com.synapse.learning.ReviewCompleted` subject를 기준으로 정렬되어 있다.

learning이 `com.synapse.event.learning.ReviewCompleted`처럼 full name이 다른 record를 발행하면, Schema Registry 입장에서는 같은 subject 검증 흐름을 타지 않거나 별도 schema로 취급될 수 있다. 따라서 shared 정본 기준 BACKWARD 검증이 통과했더라도 실제 producer drift를 막지 못할 수 있다.

## Decision
팀장님 답변 기준 결정:

1. platform은 shared 정본 유지가 맞다.
2. platform이 `com.synapse.event.learning`으로 바꾸면 정본을 따르는 쪽이 깨진다.
3. 근본 수정 owner는 learning-card다.
4. learning 발행 스키마를 shared 정본에 정렬해야 한다.
5. 단, `string <-> long` 변경은 BACKWARD 비호환이므로 v2 topic 전환 또는 개발 환경 subject 리셋 같은 운영 결정이 필요하다.

## Expected Platform Action
platform 코드 변경은 현재 기준으로 하지 않는다.

platform에서 할 일:
- platform 이슈 #87에 원인과 근거를 정리한다.
- shared 정본 유지 결정을 명시한다.
- learning owner 수정 필요 사항을 연결한다.
- 필요 시 history에 조사 이력을 남긴다.

platform 방어 로직은 별도 요구가 있을 때만 검토한다.

## Risk Notes
- platform이 임의로 learning 실제 발행 스키마를 따라가면 shared 표준과 충돌한다.
- learning owner 수정은 BACKWARD 비호환 가능성이 있어 단순 PR로 끝나지 않을 수 있다.
- 운영/개발 환경의 Schema Registry subject 정책에 따라 v2 topic 또는 subject 리셋 결정이 필요하다.

## Verification Direction
코드 변경 없음 기준:
- 문서와 이슈 답변 검증
- shared/platform/learning 계약 근거 명시

코드 변경이 추가 요청될 경우:
- `AuditConsumerIntegrationTest`
- `clean build`
