# TASK — Step 4: 멀티모듈 아키텍처 마이그레이션

> 출처: TASK_platform.md Step 4

## 상태

- Phase: 마이그레이션
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-19
- 목표 완료일: 2026-05-20 (1.5일)

---

## Step Goal

Spring Modulith 단일 앱을 Gradle 멀티모듈 + 서비스별 독립 Spring Boot 앱 구조로 전환한다.
기존 auth/user 코드는 auth-service로 포팅하고, billing/audit/notification은 플레이스홀더로 선언한다.

## Done When

- [ ] `settings.gradle.kts` — 5개 모듈 선언 완료
- [ ] `platform-common` 빌드 성공
- [ ] `auth-service` 빌드 성공 + 기존 auth 테스트 전체 통과
- [ ] `billing-service` 플레이스홀더 빌드 성공
- [ ] `audit-service` 플레이스홀더 빌드 성공
- [ ] `notification-service` 플레이스홀더 빌드 성공
- [ ] 기존 `src/` 완전 삭제
- [ ] `./gradlew build` 루트 전체 빌드 성공
- [ ] 패키지 루트 `io.synapse.platform.*` 전면 적용 확인

## Scope

- In Scope:
  - Gradle 멀티모듈 구조 전환 (settings.gradle.kts, 루트 build.gradle.kts)
  - `platform-common` 모듈: exception, crypto, security 공통 클래스 이동
  - `auth-service` 모듈: 기존 auth/* + user/* 코드 전체 포팅 (패키지 rename 포함)
  - `billing-service`, `audit-service`, `notification-service`: 플레이스홀더만
  - 패키지 루트: `com.synapse.platform` → `io.synapse.platform`
  - Flyway 마이그레이션: V1~V23 → auth-service로 이동
  - auth-service 기존 테스트 전체 포팅
- Out of Scope:
  - gRPC (D-015: Phase 2 연기)
  - 서비스별 DB 스키마 분리 (Phase 2)
  - billing 코드 포팅 (Step 5에서 재구현)
  - API 경로 변경 (`/api/v1/billing/**`, `/webhooks/stripe`) — Step 5에서 처리
  - Docker 이미지 분리

## Constraints

- 각 서비스 독립 `@SpringBootApplication` 클래스 필수
- `platform-common`에는 Spring Boot 플러그인 미적용 (라이브러리 모듈)
- Spring Modulith 의존성 전면 제거
- `ApplicationModulesTest` 삭제 (Modulith 구조 해체)
- `@SpringBootApplication(scanBasePackages = "io.synapse.platform")` 설정 필수 (platform-common 빈 스캔 보장)
- auth-service 기존 테스트 100% 통과 필수

## Duration

1.5일

## Assignee / Reviewer

- Assignee: @platform-owner
- Reviewer: @team-lead
