# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- 아키텍처: Gradle 멀티모듈 + 4개 독립 Spring Boot 서비스 (D-013)
  - `platform-common` (라이브러리), `auth-service`, `billing-service`, `audit-service`, `notification-service`
- 패키지 루트: `com.synapse.platform` → `io.synapse.platform`
- platform-common: exception, crypto, security 공통 클래스 포함 (Spring Boot 플러그인 없는 라이브러리)
- auth-service: 기존 auth/* + user/* 코드 포팅 대상. Flyway V1~V23 이동.
- billing/audit/notification: 플레이스홀더 선언만 (기능 구현은 각 해당 Step에서)
- gRPC: Phase 2 연기 (D-015)
- feature/PLAT-007-stripe-billing 브랜치: 폐기 (D-014) — dev 머지 안 함
- 마이그레이션 브랜치: `feature/PLAT-000-multi-module-migration` (dev 기준 신규 생성)
- Flyway 분리: auth-service V1~V23, billing-service는 Step 5에서 V1부터 시작

## 현재 미결 사항

- auth-service 포트 번호 (로컬 개발 시 서비스별 포트 분리 필요 — 아키텍처 문서 미정의)
- Docker Compose 분리 (docker-compose.dev.yml 각 서비스 별도 서비스로 추가 — Step 이후 처리)
- 서비스별 DB 스키마 분리 (`platform_auth`, `platform_billing` 등) — Phase 2

## 활성 제약

- JWT 서명: RS256 고정 (auth-service 유지)
- Spring Modulith 의존성 완전 제거
- `@SpringBootApplication(scanBasePackages = "io.synapse.platform")` 필수
- auth-service 기존 테스트 100% 통과 필수
- 신규 코드 테스트 커버리지 80% 이상

## 참고 문서

- docs/synapse-platform-svc_ARCHITECTURE.md (v1.0) — 마이그레이션 기준 아키텍처
- docs/ai/decisions/DECISION_LOG.md (D-013, D-014, D-015)
- docs/project-management/task/TASK_platform.md Step 4
