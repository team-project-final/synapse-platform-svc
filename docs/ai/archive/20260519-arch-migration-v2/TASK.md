# TASK — Arch Migration: Gradle 멀티모듈 → Spring Modulith 단일 앱

> 출처: docs/synapse-platform-svc_ARCHITECTURE_v2.md (v2.0 기준)

## 상태

- Phase: 구현
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-19
- 목표 완료일: 2026-05-20 (1일)

---

## Step Goal

commit #15(D-013)로 생성된 Gradle 멀티모듈 구조를 해체하고,
ARCHITECTURE_v2.md가 정의한 **Spring Modulith 단일 Spring Boot 앱**으로 복원한다.
완료 후 `./gradlew test`가 전체 통과하고 `docker compose up`이 정상 기동되어야 한다.

## Done When

**빌드 구조**
- [ ] `settings.gradle.kts` — 서브모듈 선언 없음 (루트 프로젝트만)
- [ ] 루트 `build.gradle.kts` — Spring Boot 4.0.0 + Spring Modulith 2.0.6 단일 앱 빌드
- [ ] `src/main/java/io/synapse/platform/PlatformApplication.java` 존재
- [ ] 서브모듈 디렉토리(`auth-service/`, `billing-service/`, `audit-service/`, `notification-service/`, `platform-common/`) 제거 완료

**모듈 구조**
- [ ] 5개 모듈 패키지 존재: `auth`, `user`, `notification`, `admin`, `shared`
- [ ] 각 모듈 루트에 `@ApplicationModule` 선언한 `package-info.java` 존재
- [ ] `src/test/.../PlatformModuleStructureTest.java` 존재 + 통과

**코드 이동**
- [ ] auth-service 코드(`io.synapse.platform.auth.*`) → `src/main/java/.../auth/` 이동 완료
- [ ] user 서브패키지(`io.synapse.platform.auth.user.*`) → `io.synapse.platform.user.*` 분리 완료
- [ ] `UserApi` 인터페이스 존재 + auth 모듈이 `UserService`가 아닌 `UserApi`로만 user 접근
- [ ] platform-common 3개 파일 → `io.synapse.platform.shared.*` 이동 + `common` → `shared` 패키지 rename 완료
- [ ] `io.synapse.platform.common.*` import 참조 없음 (전수 교체)
- [ ] admin/notification 모듈: placeholder 클래스 1개씩 존재

**리소스**
- [ ] `src/main/resources/application.yml` 존재 (port 8081)
- [ ] `src/main/resources/application-local.yml` 존재
- [ ] `src/main/resources/db/migration/` — 기존 V*.sql 파일 전체 이동 완료
- [ ] `src/test/resources/application.yml` 존재

**로컬 compose 기동**
- [x] `.env.example` 존재 — OAuth/JWT/AES 필수 환경변수 목록 포함
- [x] `.gitignore`에 `.env`, `.env.local` 제외 규칙 존재
- [x] `docker-compose.yml`의 `platform-svc`가 `env_file: .env`를 사용
- [x] `docker compose config` 성공
- [ ] 실제 `.env` 준비 후 `docker compose up --build` 정상 기동 확인

**테스트**
- [ ] `./gradlew test` — 기존 테스트 전체 통과 (최소 기존 건수 이상)

## Scope

- In Scope:
  - Gradle 빌드 파일 재구성 (settings + root build.gradle.kts)
  - 소스 파일 물리적 이동 (패키지 구조 유지)
  - user 모듈 auth 분리 + UserApi 인터페이스 생성
  - platform-common → shared 패키지 rename
  - admin/notification placeholder
  - Flyway 마이그레이션 파일 이동
  - 테스트 파일 이동 + ModuleStructureTest 추가
  - Dockerfile 유지 (이미 단일 앱 기준 — 수정 없음)
  - docker-compose.yml 유지 + local env 주입 경로 보정
  - `.env.example` 추가

- Out of Scope:
  - 헥사고날(Port/Adapter) 내부 구조 재편 — 기존 패키지 구조 유지
  - billing 모듈 구현 (v2에 없음 — Step 4에서 추가)
  - gRPC 설정 (D-015 — Phase 2 연기)
  - 신규 기능 추가

## Constraints

- 서비스 포트: 8081 (변경 없음)
- 패키지 루트: `io.synapse.platform` (변경 없음)
- JWT RS256 고정
- 실제 secret 원문 커밋 금지 (`.env.example`에는 placeholder만 작성)
- 기존 테스트 전체 통과 필수

## Duration

1일
