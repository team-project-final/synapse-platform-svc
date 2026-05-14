# Work History: @platform

> **담당**: platform-svc / 인증·인가  
> **관련 문서**: [SCOPE](../scope/SCOPE_platform.md) | [TASK](../task/TASK_platform.md) | [WORKFLOW](../workflow/WORKFLOW_platform_W1.md)

---

## 진행 상태 대시보드

### W1 (2026-05-12 ~ 05-16)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 1 | platform-svc 골격 생성 | Done | 2026-05-12 | 2026-05-13 | PR #3, #4 merge 완료 |
| Step 2 | OAuth 회원가입/로그인 | Done | 2026-05-13 | 2026-05-14 | oauth_identities 분리 테이블, 3-케이스 처리 |
| Step 3 | JWT 발급/검증 + MFA 기초 | Not Started | — | — | |

**W1 진행률**: 2/3 Steps 완료 (Step 3 예정)

### W2 (2026-05-19 ~ 05-23)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 4 | Stripe 결제 연동 | Not Started | — | — | |
| Step 5 | FCM 푸시 알림 | Not Started | — | — | |
| Step 6 | 구독 관리 API | Not Started | — | — | |

**W2 진행률**: 0/3 Steps 완료

### W3 (2026-05-26 ~ 05-29)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 7 | audit 로그 시스템 | Not Started | — | — | |
| Step 8 | notification 서비스 | Not Started | — | — | |
| Step 9 | 관리자 API | Not Started | — | — | |

**W3 진행률**: 0/3 Steps 완료

### W4 (2026-06-01 ~ 06-05)

| Step | 내용 | 상태 | 시작일 | 완료일 | 비고 |
|------|------|------|--------|--------|------|
| Step 10 | E2E 테스트 | Not Started | — | — | |
| Step 11 | 안정화 및 버그 수정 | Not Started | — | — | |
| Step 12 | 문서화 | Not Started | — | — | |

**W4 진행률**: 0/3 Steps 완료

---

## 작업 로그

### W1 (2026-05-12 ~ 05-16)

#### 2026-05-12 (화)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-13 (수)
- **완료**:
  - AI Agent 워크플로 설계 (Director/Worker/Researcher 역할 분담)
  - docs/ai/ 폴더 구조 생성 (current/, decisions/, agent/, archive/)
  - CLAUDE.md, AGENTS.md, GEMINI.md 작성 (gitignore 처리)
  - Dockerfile multi-stage 빌드 작성 + docker build 검증 성공
  - docs/rules/13-git-rules.md 추가 (브랜치 전략, 커밋, PR 정책)
  - 브랜치 정리: feature/* → chore/PLAT-001, chore/PLAT-002
  - PR #3, #4 → dev merge 완료 / **Step 1 완료**
  - Step 2 브랜치 생성 (`feature/PLAT-004-oauth`)
  - OAuth 샘플링 완료 — A안 채택 (userId redirect, JWT는 Step 3에서 추가)
  - `application.properties` → `application.yml` 전환 + `spring.application.name=synapse-platform-svc`
  - Dockerfile / docker-compose.yml 포트 8080 → 8081 수정
  - `docs/ai/current/TASK.md` Step 2 내용으로 작성
  - `docs/ai/templates/` 폴더 분리 (템플릿 vs 실제 작업 문서 구조 개선)
  - `docs/spike/OAuth/` OAuth 샘플링 문서 추가
  - `docs/platform-owner__platform-svc-workflow-guide.html` 워크플로우 가이드 확인
- **진행 중**: Step 2 분석 단계 (10단계 워크플로 ①②③ 완료)
- **이슈**: 없음
- **다음**: Step 2 설계 단계 (CONTEXT.md 작성 → HANDOFF.md → Worker 구현)

#### 2026-05-14 (목)
- **완료**:
  - Step 2 추가 샘플링 완료 (Jackson 쿠키 직렬화, Tenant 트랜잭션, Flyway+PostgreSQL 검증)
  - CONTEXT.md / HANDOFF.md 작성 (D-001~D-005 설계 결정 반영)
  - Worker 구현 완료 — Entity 5개, Repository 5개, OAuth 서비스/핸들러/SecurityConfig
  - Flyway V1~V3, V16~V18 마이그레이션 파일 작성
  - 테스트 20건+ 통과 / `./gradlew build` 성공
  - 룰북 준수 수정 ([MUST] 7건 + [SHOULD] 3건) — 29건 테스트 통과
  - PR #8 dev merge 완료 / **Step 2 완료**
  - `feature/PLAT-005-jwt-mfa` 브랜치 생성 / **Step 3 시작**
- **진행 중**: Step 3 분석 단계
- **이슈**: 없음
- **다음**: JWT/TOTP 라이브러리 선택 → 설계 → Worker 구현

#### 2026-05-15 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

### W2 (2026-05-19 ~ 05-23)

#### 2026-05-19 (화)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-20 (수)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-21 (목)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-22 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

### W3 (2026-05-26 ~ 05-29)

#### 2026-05-26 (화)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-27 (수)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-28 (목)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-05-29 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

### W4 (2026-06-01 ~ 06-05)

#### 2026-06-01 (월)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-02 (화)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-03 (수)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-04 (목)
- **완료**:
- **진행 중**:
- **이슈**:
- **다음**:

#### 2026-06-05 (금)
- **완료**:
- **진행 중**:
- **이슈**:
- **주간 요약**:

---

## 변경 이력

| 날짜 | 변경 사항 |
|------|-----------|
| 2026-05-13 | 전체 일정 재정비 (05-12~06-05, 월~금), Step 1 Done 반영 |
| 2026-05-11 | W2/W3/W4 대시보드 및 로그 템플릿 추가 |
| 2026-05-11 | 초기 템플릿 생성 |
