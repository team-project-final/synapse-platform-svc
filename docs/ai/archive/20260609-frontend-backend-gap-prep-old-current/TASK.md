# TASK — Step 11: W5 라이브 E2E 및 staging 검증

> 출처: `docs/project-management/prd/PRD_W5.md`, `docs/project-management/workflow/WORKFLOW_platform_W5.md`, GitHub issue #62, #37

## 상태

- Phase: 검증 진행 / 백엔드 회귀 테스트 완료
- 담당 Agent: Director(Codex) -> Worker(Codex)
- 시작일: 2026-06-09
- 목표 완료일: 2026-06-12
- 작업 브랜치: `feature/PLAT-062-w5-live-e2e`

---

## Step Goal

platform-owner가 staging 또는 로컬 통합 환경에서 인증, 결제, 알림 라이브 E2E를 실행하고 platform P0 이슈를 0건으로 정리한다.

## Done When

- [x] #37 staging profile/GitOps secret 정합을 확인하고 `/actuator/health` 결과를 기록한다.
- [x] 인증 E2E 결과를 기록한다: 회원가입 -> 로그인 -> JWT refresh -> MFA -> 로그아웃/토큰 무효화.
- [x] 결제 E2E 결과를 기록한다: Stripe Test Mode Checkout -> Webhook -> 플랜 활성화.
- [~] 알림 E2E 결과를 기록한다: `card.review.due`, `gamification.*`, `community.*` 관련 이벤트 -> notification -> FCM/SES/audit 경로.
- [x] 실패 항목을 P0/P1/P2로 분류하고, 필요한 경우 이슈 또는 후속 PR 링크를 남긴다.
- [x] 백엔드 회귀 테스트 결과를 기록한다.
- [x] #62에 실행 환경, 시나리오, 결과, 잔여 이슈를 공유할 수 있는 형태로 정리한다.

> `[~]` 알림 E2E: platform notification consumer/service/FCM/SES 백엔드 검증은 통과. cross-service 라이브 이벤트(`card.review.due`, `gamification.*`, `community.*`)는 shared W5 Day1 리포트 기준 engagement/learning P0 선결 후 재실행 필요.

## 현재 실행 결과 (2026-06-09)

| 영역 | 결과 | 근거 | 후속 |
|------|------|------|------|
| #37 staging | PASS | 최신 gitops staging overlay가 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 주입하고 shared가 staging 20/0/0 ALL PASSED 및 platform-svc Healthy 기록 | platform 코드 수정 없음 |
| Avro 정본 | PASS | `UserRegistered.avsc`, `NotificationSend.avsc`를 shared 최신 정본과 동일하게 정렬 | 변경 포함 |
| 인증/결제 백엔드 E2E | PASS | `.\gradlew.bat test --tests "*AuthBillingE2ETest"` BUILD SUCCESSFUL | 로그아웃 전용 HTTP endpoint 없음은 gap으로 기록 |
| 알림 백엔드 테스트 | PASS | `NotificationKafkaConsumerIT`, `NotificationServiceTest`, `FcmPushServiceTest`, `SesEmailServiceTest` BUILD SUCCESSFUL | cross-service live E2E는 다른 owner P0 후 재실행 |
| 전체 빌드 | PASS | `.\gradlew.bat clean build` BUILD SUCCESSFUL, 286 tests / failures 0 / errors 0 | Windows Embedded Kafka temp delete 로그는 비차단 |
| #62 라이브 E2E | PARTIAL | shared W5 Day1에서 서비스 단위 환경 13/13 healthy, platform audit 정상, engagement/learning P0 발견 | P0: engagement F1, learning-ai F2/F3 |

## Scope

- In Scope:
  - platform-svc staging profile과 GitOps ExternalSecret 키 정합 확인
  - 인증/결제/알림 라이브 또는 로컬 통합 E2E 실행
  - 기존 백엔드 E2E/통합 테스트 재실행
  - 실패 케이스 P0/P1/P2 분류
  - 실행 결과 보고 문서/이슈 코멘트 초안 작성
- Out of Scope:
  - 프론트엔드 결제 UI 신규 구현
  - 다른 서비스의 도메인 로직 수정
  - production 배포
  - Stripe 실결제
  - GitOps/shared 레포 임의 수정

## Input

- `docs/project-management/prd/PRD_W5.md`
- `docs/project-management/workflow/WORKFLOW_platform_W5.md`
- `docs/project-management/task/TASK_platform.md`
- `docs/project-management/history/HISTORY_platform.md`
- GitHub issue #62: `[W5 E2E] 알림 + 인증 + 결제 라이브 E2E 실행`
- GitHub issue #37: `[W3/staging] platform-svc staging Spring profile 추가`
- `src/main/resources/application-staging.yml`
- `README.md` 실행 환경/포트 정책
- Stripe Test Mode 계정/웹훅 설정
- OAuth 테스트 계정, FCM/SES 테스트 자격 증명, Kafka/Schema Registry 실행 환경

## Instructions

1. [x] 현재 브랜치와 작업 문서를 확인한다.
2. [x] #37의 최신 요구사항과 현재 `application-staging.yml`을 비교한다.
3. [x] GitOps/shared가 정의한 staging secret key와 platform 설정이 다른지 확인한다.
4. [x] 로컬 백엔드 회귀 테스트를 먼저 실행한다.
5. [x] staging 또는 로컬 통합 환경에서 인증 E2E를 실행한다. (백엔드 E2E 기준)
6. [x] Stripe Test Mode 결제 E2E를 실행한다. (백엔드 E2E 기준)
7. [~] 알림 이벤트 경로를 확인하고 FCM/SES/audit 결과를 기록한다.
8. [x] 실패 항목을 P0/P1/P2로 분류한다.
9. [x] 필요한 코드 수정이 생기면 별도 최소 변경으로 처리하고 회귀 테스트를 재실행한다.
10. [x] #62 공유용 결과 요약과 HISTORY 로그를 갱신한다.

## Output Format

- 작업 결과 요약: 실행 환경, 실행 일시, 시나리오별 PASS/FAIL/PARTIAL
- 실패 항목: 증상, 원인 추정, 우선순위(P0/P1/P2), 후속 액션
- 검증 로그: 실행한 Gradle 명령과 결과
- 이슈 코멘트 초안: #62에 붙일 수 있는 한국어 요약
- 필요 시 PR: base `dev`, 제목/본문은 `docs/rules/13-git-rules.md` 준수

## Constraints

- GitOps/shared가 관리하는 값이 있으면 해당 레포의 정의를 우선한다.
- secret, API key, Stripe key, Firebase key, AWS credential은 문서나 코드에 커밋하지 않는다.
- Stripe는 Test Mode만 사용한다.
- 프론트엔드 결제 UI는 현재 Out of Scope다. 설명 시 "백엔드 E2E/Test Mode 검증"으로 구분한다.
- 현재 레포 기준 로컬 기본 포트는 8081, Kubernetes/GitOps 배포는 `SERVER_PORT=8080` override를 사용한다. 포트 변경은 검증 결과가 필요할 때만 별도 판단한다.
- 로그아웃 전용 API가 없으면 현재 구현 가능한 토큰 무효화 경로를 확인하고 gap으로 기록한다.
- 코드 수정 전에는 실패 재현 또는 설정 불일치 근거를 먼저 남긴다.

## Duration

1.0 ~ 1.5일

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
