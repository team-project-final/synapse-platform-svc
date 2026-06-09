# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 태스크 완료 시 archive로 이동 후 초기화합니다.

## 현재 확정된 것

- W5 platform 작업의 중심은 신규 기능 구현이 아니라 staging health, 라이브/통합 E2E, P0 0건 정리다.
- W4에서 `AuthBillingE2ETest` 기반 백엔드 E2E와 notification hardening은 완료 기록이 있다.
- 현재 열린 W5 관련 이슈는 #62(라이브 E2E 실행)와 #37(staging health/profile 정합)이다.
- 작업 브랜치는 `origin/dev` 기준 `feature/PLAT-062-w5-live-e2e`다.
- 프론트엔드 결제 UI는 아직 붙어 있지 않다. 결제 설명은 "백엔드에서 Stripe Test Mode/mock boundary로 검증"이라고 구분한다.
- 현재 레포 기준 로컬 기본 포트는 8081이고, Kubernetes/GitOps에서는 `SERVER_PORT=8080` override로 컨테이너/probe 포트를 맞춘다.
- 2026-06-09 기준 `synapse-gitops`/`synapse-shared`를 최신화했다.
- 최신 gitops staging overlay는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 platform-svc에 주입한다. 현재 `application-staging.yml`의 datasource 설정과 정합한다.
- shared W5 문서는 EKS 재apply 후 staging 20/0/0 ALL PASSED 및 platform-svc staging Healthy를 기록한다. #37의 GitHub OPEN 상태는 최신 문서보다 늦은 상태로 본다.
- `src/main/avro/platform/UserRegistered.avsc`, `NotificationSend.avsc`는 shared 최신 정본과 동일하게 맞췄다.

## 현재 미결 사항

- staging 실제 URL, OAuth 테스트 계정, Stripe webhook endpoint, FCM/SES 테스트 credential 제공 여부가 필요하다.
- 로그아웃 전용 HTTP endpoint는 없다. 현재 토큰 무효화는 `RefreshTokenService.delete(userId)`와 세션 무효화 이벤트 경로만 있다. #62 설명 시 gap으로 기록한다.
- `card.review.due`, `gamification.*`, `community.*` cross-service live E2E는 shared W5 Day1에서 발견된 engagement/learning P0 선결 후 재실행해야 한다.
- #62 실행 결과를 issue comment로 남길지 별도 문서로 남길지 최종 결정이 필요하다. 현재는 issue comment 초안을 HANDOFF에 작성한다.

## 현재 검증된 것

- `docs/project-management/prd/PRD_W5.md`: platform P0는 인증 E2E와 결제 E2E다.
- `docs/project-management/workflow/WORKFLOW_platform_W5.md`: 알림 안정화와 P0/P1/P2 분류까지 포함한다.
- `docs/project-management/history/HISTORY_platform.md`: W4 Step 9/10은 완료 상태다.
- GitHub issue #62: 알림 + 인증 + 결제 라이브 E2E 실행이 남아 있다.
- GitHub issue #37: staging profile datasource/env binding 이슈가 OPEN 상태다.
- `.\gradlew.bat generateAvroJava` 통과.
- `.\gradlew.bat test --tests "*AuthBillingE2ETest"` 통과.
- `.\gradlew.bat test --tests "*NotificationKafkaConsumerIT" --tests "*NotificationServiceTest" --tests "*FcmPushServiceTest" --tests "*SesEmailServiceTest"` 통과.
- `.\gradlew.bat clean build` 통과. 286 tests / failures 0 / errors 0 / skipped 0.
- Windows에서 Embedded Kafka 임시 디렉터리 삭제 실패 로그가 출력되지만 Gradle 결과는 성공이다.

## 활성 제약

- GitOps/shared가 관리하는 설정과 계약을 우선한다.
- secret 값은 절대 커밋하지 않는다.
- Stripe는 Test Mode만 사용한다.
- Kafka/Avro/Schema Registry 계약은 shared 표준과 drift가 없어야 한다.
- JWT RS256, Refresh Token DB+Redis, 모듈 순환 의존 금지, 신규 코드 커버리지 80%+ 기준 유지.
- PR base는 `dev`다.

## 참고할 공식 문서

- `docs/project-management/prd/PRD_W5.md`
- `docs/project-management/workflow/WORKFLOW_platform_W5.md`
- `docs/project-management/task/TASK_platform.md`
- `docs/project-management/history/HISTORY_platform.md`
- `docs/rules/01-security.md`
- `docs/rules/04-quality.md`
- `docs/rules/06-auth-token.md`
- `docs/rules/08-kafka-event.md`
- `docs/rules/12-working-log.md`
- `docs/rules/13-git-rules.md`
- GitHub issue #62
- GitHub issue #37
