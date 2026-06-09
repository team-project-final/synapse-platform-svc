# HANDOFF

> Agent 간 작업 전달 문서입니다.
> 태스크마다 덮어씁니다. 이전 HANDOFF는 archive에 있습니다.

## FROM

Director (Codex)

## TO

Worker (Codex)

## 작업 브랜치

`feature/PLAT-062-w5-live-e2e` (base: `origin/dev`)

## 요청 내용

W5 platform 작업으로 #62 라이브 E2E와 #37 staging health/profile 정합을 실행한다. 새 기능을 먼저 만들지 말고, 현재 구현과 환경 기준으로 무엇이 통과하고 무엇이 막히는지 증거를 남긴다.

## 실행 순서

### 1. 사전 확인

- `git status --short --branch`로 브랜치와 변경사항 확인
- `docs/ai/current/TASK.md`, `docs/ai/current/CONTEXT.md` 확인
- #62, #37 최신 본문/댓글 확인
- `README.md`, `src/main/resources/application-staging.yml`, `src/main/resources/application.yml` 확인

### 2. #37 staging profile 정합 확인

- 현재 `application-staging.yml`의 datasource/redis/env key를 확인한다.
- GitOps/shared 기준이 확인 가능하면 staging ExternalSecret key와 비교한다.
- `/actuator/health` 검증 방법을 정리한다.
- 설정 불일치가 재현되면 최소 수정 후보를 제시한다. 바로 수정하기 전 근거를 기록한다.

### 3. 백엔드 회귀 테스트

우선 아래 테스트를 실행하고 결과를 기록한다.

```powershell
.\gradlew.bat test --tests "*AuthBillingE2ETest"
.\gradlew.bat test --tests "*NotificationKafkaConsumerIT" --tests "*NotificationServiceTest" --tests "*FcmPushServiceTest" --tests "*SesEmailServiceTest"
```

코드 또는 설정을 수정했다면 다음도 실행한다.

```powershell
.\gradlew.bat clean build
```

### 4. 인증 E2E

- 회원가입 -> 로그인 -> access token 발급 확인
- refresh cookie 기반 JWT refresh 확인
- MFA setup -> TOTP verify 확인
- 로그아웃/토큰 무효화 경로 확인
- 전용 logout endpoint가 없으면 gap으로 기록하고 P0/P1 여부를 판단한다.

### 5. 결제 E2E

- Stripe Test Mode 기준으로 Checkout 생성 확인
- Webhook 서명 검증 경로 확인
- `checkout.session.completed`, `invoice.paid`, `customer.subscription.deleted` 등 현재 구현된 이벤트 처리 범위를 확인한다.
- 플랜 활성화/구독 조회 결과를 기록한다.
- 프론트 결제 UI 미연동은 별도 Out of Scope로 명시한다.

### 6. 알림 E2E

- platform이 직접 consume하는 토픽과 다른 서비스가 발행해야 하는 토픽을 구분한다.
- `card.review.due`, `gamification.*`, `community.*`가 notification으로 이어지는 실제 경로를 확인한다.
- FCM/SES 발송 성공, 실패, 지연 메트릭을 확인한다.
- audit 적재 여부를 확인한다.

### 7. 결과 정리

시나리오별로 아래 형식으로 정리한다.

```markdown
| 영역 | 시나리오 | 결과 | 근거 | 후속 |
|------|----------|------|------|------|
| 인증 | 회원가입 -> 로그인 -> refresh -> MFA | PASS/FAIL/PARTIAL | 명령/로그/응답 | 없음/#이슈 |
```

실패 항목은 다음 기준으로 분류한다.

- P0: 발표/핵심 시나리오를 막음
- P1: 우회 가능하지만 품질/시연 리스크가 큼
- P2: 문서화 또는 후속 개선으로 이월 가능

## 필요한 출력 형식

- 변경 파일 목록
- 실행한 명령과 결과 요약
- PASS/FAIL/PARTIAL 표
- P0/P1/P2 분류
- #62 issue comment 초안
- 코드 수정이 있었다면 PR 본문 초안

## 현재 결과 (2026-06-09)

### 변경 파일

- `src/main/avro/platform/UserRegistered.avsc`
- `src/main/avro/platform/NotificationSend.avsc`
- `docs/ai/current/TASK.md`
- `docs/ai/current/CONTEXT.md`
- `docs/ai/current/HANDOFF.md`
- `docs/project-management/history/HISTORY_platform.md`
- `docs/ai/archive/20260609-w5-live-e2e-prep-old-current/*`

### 실행한 명령

```powershell
.\gradlew.bat generateAvroJava
.\gradlew.bat test --tests "*AuthBillingE2ETest"
.\gradlew.bat test --tests "*NotificationKafkaConsumerIT" --tests "*NotificationServiceTest" --tests "*FcmPushServiceTest" --tests "*SesEmailServiceTest"
.\gradlew.bat clean build
```

결과: 전부 BUILD SUCCESSFUL. 전체 빌드 기준 286 tests / failures 0 / errors 0 / skipped 0.

### PASS / FAIL / PARTIAL

| 영역 | 시나리오 | 결과 | 근거 | 후속 |
|------|----------|------|------|------|
| #37 | staging profile/GitOps env 정합 | PASS | gitops staging overlay가 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 주입. shared가 platform-svc staging Healthy 기록 | platform 코드 수정 없음 |
| Avro | shared 정본과 platform 벤더링 정합 | PASS | `Compare-Object` 차이 없음 | 변경 포함 |
| 인증 | 가입 -> 로그인 -> JWT 발급/refresh -> MFA | PASS | `AuthBillingE2ETest` 통과 | 로그아웃 endpoint gap 기록 |
| 결제 | Stripe Test Mode Checkout -> Webhook -> 플랜 활성화 | PASS | `AuthBillingE2ETest` 통과 | 프론트 결제 UI는 out of scope |
| 알림 | NotificationSend -> FCM/SES service path | PASS | notification 테스트 묶음 통과 | cross-service event path는 아래 PARTIAL |
| 알림 | card/gamification/community -> notification live chain | PARTIAL | shared W5 Day1이 P0 F1/F2/F3 발견 | engagement/learning owner P0 선결 |

### P0 / P1 / P2

- P0(platform): 없음.
- P0(other owner): engagement `UserRegistered` reader F1, learning-ai `NotificationSend` writer F2/F3.
- P1(platform): 없음.
- P2(platform): 로그아웃 전용 HTTP endpoint 부재. 현재는 refresh token delete/service event 경로만 존재.
- P2(other owner): learning-ai AI client key gate F4.

### #62 issue comment 초안

```markdown
## platform-svc W5 E2E 진행 결과 (2026-06-09)

### 확인한 것
- synapse-gitops / synapse-shared 최신 main 기준으로 재확인했습니다.
- #37 staging datasource 이슈는 최신 gitops overlay가 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 주입하고, shared W5 문서가 platform-svc staging Healthy를 기록하고 있어 platform 코드 수정은 필요 없어 보입니다.
- platform 벤더링 Avro(`UserRegistered`, `NotificationSend`)는 shared 최신 정본과 동일하게 맞췄습니다.

### 검증 결과
- `generateAvroJava`: PASS
- `AuthBillingE2ETest`: PASS
- `NotificationKafkaConsumerIT`, `NotificationServiceTest`, `FcmPushServiceTest`, `SesEmailServiceTest`: PASS
- `clean build`: PASS (286 tests, failures 0, errors 0)

### 시나리오 상태
| 영역 | 결과 | 비고 |
|------|------|------|
| 인증 백엔드 E2E | PASS | 가입/로그인/JWT refresh/MFA 경로 확인 |
| 결제 백엔드 E2E | PASS | Stripe Test Mode mock boundary + webhook + plan activation |
| 알림 platform 경로 | PASS | NotificationSend consumer/service/FCM/SES 테스트 통과 |
| cross-service live 알림 | PARTIAL | shared W5 Day1 기준 engagement F1, learning-ai F2/F3 P0 선결 필요 |

### 잔여
- platform P0: 없음
- P0(other owner): engagement UserRegistered reader, learning-ai NotificationSend writer
- P2(platform): 로그아웃 전용 HTTP endpoint 없음. 현재 토큰 무효화는 `RefreshTokenService.delete(userId)` 경로로만 존재
```

## 첨부할 파일

- `docs/ai/current/TASK.md`
- `docs/ai/current/CONTEXT.md`
- `docs/ai/current/HANDOFF.md`
- `docs/project-management/prd/PRD_W5.md`
- `docs/project-management/workflow/WORKFLOW_platform_W5.md`
- `docs/rules/13-git-rules.md`

## 기한

2026-06-12 (W5 금요일 EOD)
