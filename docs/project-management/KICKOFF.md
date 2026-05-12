# Synapse 프로젝트 킥오프

> 2026-05-12 (W1 Day 1) — 팀장 김민구

---

## 1. 프로젝트 한눈에 보기

**한 줄 정의**: 노트 → 플래시카드 자동 생성 → 간격 반복 → AI 학습 보조. PKM + SRS + AI 통합 SaaS.

> "노트가 카드가 되고, 복습이 노트를 다시 살린다."

- **대상 사용자**: 개발자, 대학원생, 자격증 준비생 — 장기 기술/학술 지식 유지가 필요한 사람
- **핵심 기능**: 마크다운 노트 작성 → 위키링크 기반 지식 그래프 → AI가 노트에서 플래시카드 자동 생성 → SM-2 간격 반복 → 학습 그룹에서 공유
- **디자인 컨셉**: "Warm Intellectual" — 서점/도서관 느낌. 따뜻하고 지적이지만 딱딱하지 않은 톤.
- **기간**: 2026-05-12 ~ 2026-06-15 (5주 + 발표일, 22 영업일)

---

## 2. 팀 구성 + 담당 배치

7명이 각자 트랙을 맡아. 같은 레포를 공유하는 트랙(C-1/C-2, D-1/D-2)은 모듈 경계로 나눠져 있어.

| 이름 | 트랙 | 서비스 | 담당 모듈 | GitHub 레포 |
|------|------|--------|-----------|-------------|
| 김민구 | 팀장 | Gateway / 인프라 | CI/CD, ArgoCD, Schema Registry | [syn](https://github.com/team-project-final/syn) · [shared](https://github.com/team-project-final/synapse-shared) · [mirror](https://github.com/team-project-final/synapse-mirror) · [gitops](https://github.com/team-project-final/synapse-gitops) |
| 김해준 | A | platform-svc | auth, billing, notification, audit | [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) |
| 한승완 | B | engagement-svc | community, gamification | [synapse-engagement-svc](https://github.com/team-project-final/synapse-engagement-svc) |
| 김현지 | C-1 | knowledge-svc | note, graph | [synapse-knowledge-svc](https://github.com/team-project-final/synapse-knowledge-svc) |
| 박은서 | C-2 | knowledge-svc | chunking, 검색, Modulith | [synapse-knowledge-svc](https://github.com/team-project-final/synapse-knowledge-svc) |
| 김나경 | D-1 | learning-svc | card, srs (Java) | [synapse-learning-svc](https://github.com/team-project-final/synapse-learning-svc) |
| 조유지 | D-2 | learning-svc | ai (Python/FastAPI) | [synapse-learning-svc](https://github.com/team-project-final/synapse-learning-svc) |

> **Frontend (Flutter)** 는 전원이 함께 작업해. 레포: [synapse-frontend](https://github.com/team-project-final/synapse-frontend)

---

## 3. 기술 스택

| 카테고리 | 스택 |
|----------|------|
| Backend | Spring Boot 4 + Modulith, Java 21, Gradle |
| AI 서비스 | FastAPI, Python 3.11, Anthropic SDK, OpenAI SDK |
| Frontend | Flutter 3, Riverpod 3, GoRouter 14, Dio 5 |
| DB | PostgreSQL 16, Redis 7, OpenSearch 8 |
| 메시징 | Kafka (MSK) + Avro + Confluent Schema Registry |
| 인프라 | AWS EKS, Docker, Kustomize |
| DevOps | GitHub Actions (CI/CD), ArgoCD |
| 디자인 시스템 | Material 3, Warm Intellectual 테마 ([DESIGN.md](../../DESIGN.md)) |

> 각 스택의 상세 버전은 위키 [18_기술_스택_정의서](https://github.com/team-project-final/documents/wiki) 참고.

---

## 4. GitHub 레포지토리 + CI/CD

### 레포 카탈로그

총 10개 레포가 이미 만들어져 있어. 자기 레포만 clone하면 돼.

| 레포 | 가시성 | 용도 | 담당 |
|------|:------:|------|------|
| [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) | public | auth, audit, billing, notification | 김해준 |
| [synapse-engagement-svc](https://github.com/team-project-final/synapse-engagement-svc) | public | community, gamification | 한승완 |
| [synapse-knowledge-svc](https://github.com/team-project-final/synapse-knowledge-svc) | public | note, graph, chunking | 김현지, 박은서 |
| [synapse-learning-svc](https://github.com/team-project-final/synapse-learning-svc) | public | card/srs (Java) + ai (Python) | 김나경, 조유지 |
| [synapse-frontend](https://github.com/team-project-final/synapse-frontend) | public | Flutter web/mobile | 전원 |
| [synapse-shared](https://github.com/team-project-final/synapse-shared) | public | Avro 스키마 + 공통 라이브러리 | 김민구 |
| [synapse-mirror](https://github.com/team-project-final/synapse-mirror) | private | Tier 1 자동 미러 (읽기 전용, 직접 커밋 금지) | 자동 |
| [synapse-gitops](https://github.com/team-project-final/synapse-gitops) | private | K8s manifest + ArgoCD ApplicationSet | 김민구 |
| [syn](https://github.com/team-project-final/syn) | public | 부트스트랩 스크립트 + 스펙 | 김민구 |
| [documents](https://github.com/team-project-final/documents) | public | 프로젝트 관리 문서 (이 레포) | 전원 |

### CI/CD 파이프라인

코드를 main에 push하면 자동으로 3가지가 돌아가:

```
main push
  ├─→ ci.yml      빌드 + 테스트 (Java: gradlew build / Flutter: analyze + build)
  ├─→ mirror.yml   synapse-mirror에 자동 동기화 (읽기 전용 백업)
  └─→ deploy.yml   Docker → ECR push → GitOps 태그 갱신 → ArgoCD dev 자동 배포
```

추가 워크플로:
- `schema-check.yml` — Avro 스키마 변경 PR 시 호환성 검증 (synapse-shared 전용)
- `validate-manifests.yml` — K8s manifest PR 시 yamllint + kustomize build (synapse-gitops 전용)

---

## 5. 5주 로드맵

| 주차 | 기간 | 핵심 목표 | 이번 주 끝에 이게 안 되면 위험 |
|:----:|------|-----------|------|
| **W1** | 05-12 ~ 05-16 | 인프라 + 서비스 골격 + 기본 CRUD | 금요일까지 `./gradlew build` 실패 |
| **W2** | 05-19 ~ 05-23 | SRS 복습 엔진, AI 골격, Graph + ES, Schema Registry | 핵심 API 3개 미만 동작 |
| **W3** | 05-26 ~ 05-29 (4일) | Kafka 이벤트 발행 + 검색 RRF + AI 카드 자동 생성 | producer 토픽 1개도 미발행 |
| **W4** | 06-01 ~ 06-05 (4일) | 이벤트 소비자 + notification/audit + 통합 검증 | Staging 배포 실패 |
| **W5** | 06-08 ~ 06-12 | E2E 테스트 + 버그 수정 + 발표 준비 | E2E 시나리오 50% 미만 통과 |
| **발표** | **06-15 (월)** | **최종 발표 · 시연 · 제출** | — |

> **공휴일**: 5/25(일) 부처님오신날, 6/3(화) 지방선거 — W3·W4가 4영업일인 이유.

각 주차의 상세 요구사항은 `prd/PRD_W{N}.md`에 있어. 자기 트랙 부분만 먼저 읽으면 돼.

---

## 6. 문서 체계 + 개발 워크플로

### 5종 문서 시스템

프로젝트 진행 상황을 5종류 문서로 추적해. 위에서 아래로 구체화되는 구조야.

```
SCOPE  → 5주 전체 책임 경계 (너의 영역이 뭔지)
  ↓
PRD    → 이번 주에 뭘 만들어야 하는지 (주차별 요구사항)
  ↓
TASK   → 구체적으로 뭘 어떻게 하는지 (Step별 작업 정의)
  ↓
WORKFLOW → Step을 10단계로 쪼갠 체크리스트
  ↓
HISTORY  → 매일 한 일 기록 (날짜별 일지)
```

### 너의 파일은 여기 있어

| 문서 | 경로 | 예시 |
|------|------|------|
| SCOPE | `scope/SCOPE_{트랙}.md` | `SCOPE_platform.md` |
| PRD | `prd/PRD_W{N}.md` | `PRD_W1.md` |
| TASK | `task/TASK_{트랙}.md` | `TASK_platform.md` |
| WORKFLOW | `workflow/WORKFLOW_{트랙}_W{N}.md` | `WORKFLOW_platform_W1.md` |
| HISTORY | `history/HISTORY_{트랙}.md` | `HISTORY_platform.md` |

### TASK 필수 10필드

TASK 파일의 각 Step에는 이 10가지가 반드시 들어가 있어:

| # | 필드 | 핵심 |
|---|------|------|
| 1 | Step Name | 단계 이름 |
| 2 | Step Goal | **측정 가능한 문장** — "[주체]가 [대상]에 [행위]를 [결과]한다" |
| 3 | Done When | 성공 기준 (Goal 바로 다음에 고정) |
| 4 | Scope | In Scope / Out of Scope |
| 5 | Input | 필요한 입력물 (문서, 코드, 환경) |
| 6 | Instructions | 수행할 작업 목록 |
| 7 | Output Format | 산출물 형태 · 위치 |
| 8 | Constraints | 이번 Step 고유 제약 |
| 9 | Duration | 예상 작업일 (1명 기준) |
| 10 | RULE Reference | 참조 문서 |

### 언제 뭘 업데이트해야 해?

| 이벤트 | 업데이트 대상 |
|--------|-------------|
| 작업 시작 | TASK → Status "In Progress" + HISTORY에 시작일 |
| Step 완료 | TASK → Status "Done" + WORKFLOW 전체 체크 + HISTORY 완료일 |
| 이슈 발생 | TASK → Constraints 갱신 + HISTORY에 기록 |

### 10단계 개발 워크플로

TASK의 각 Step을 실행할 때 이 순서로 진행해:

```
① TASK 확인 (Goal/Done When/Scope 읽기)
② 요구사항 분석 (Instructions 정리)
③ Security 1차 (인증 필요? 권한 종류? 공개 API?)
④ ERD 설계
⑤ Security 2차 (암호화? Soft Delete? 행단위 접근?)
⑥ DTO/Entity 설계 (API First: DTO 먼저 → Entity 나중)
⑦ Repository 구현
⑧ Service + Test (동시에)
⑨ Controller + Test (동시에, 401/403 포함)
⑩ View + Test (Smoke 1건 이상 필수)
```

> WORKFLOW 파일에 이 10단계가 체크박스로 되어 있어. 하나씩 체크하면서 진행하면 돼.

---

## 7. Git 워크플로

### 브랜치 전략

```
main (보호됨 — 직접 push 절대 금지)
  └─ feature/{TICKET}-{설명}  ← 여기서 작업
       └─ PR 생성 → 1명 approve → squash merge → 브랜치 자동 삭제
```

### 커밋 메시지

Conventional Commits 형식이야:

| prefix | 용도 | 예시 |
|--------|------|------|
| `feat` | 새 기능 | `feat(auth): Google OAuth 로그인 구현` |
| `fix` | 버그 수정 | `fix(note): 위키링크 파싱 NPE 수정` |
| `chore` | 인프라/설정 | `chore(infra): Docker Compose 포트 변경` |
| `docs` | 문서 | `docs: PRD_W1 수용 기준 갱신` |
| `test` | 테스트 | `test(srs): SM-2 경계값 테스트 추가` |
| `refactor` | 리팩토링 | `refactor(graph): 인접 리스트 → 맵 변환` |

### PR 규칙

- **제목**: 커밋 컨벤션과 같은 형식
- **본문**: 최소 `## 변경 사항` + `## 테스트 결과` 포함
- **리뷰어**: CODEOWNERS가 자동 배정 — approve 1명이면 머지 가능
- **머지 방식**: squash merge (커밋 이력 깔끔하게)

### 절대 금지

- ❌ `git push --force` (force push)
- ❌ main 브랜치에 직접 commit
- ❌ `.env`, `*.key`, `*.pem` 파일 commit (시크릿 유출)
- ❌ Classic PAT 사용 (fine-grained PAT만 허용)

---

## 8. 소통 규칙

### 확정

- **매일 데일리 스탠드업** 진행

### 제안 (오늘 킥오프에서 같이 정하자)

| 항목 | 제안 | 이유 |
|------|------|------|
| 데일리 시간 | 오전 10:00, 15분 이내 | 오전에 방향 맞추고 바로 작업 시작 |
| 데일리 형식 | 3줄: 어제 한 것 / 오늘 할 것 / 막히는 것 | 짧게 공유하고, 길면 별도 논의 |
| 이슈 보고 | 30분 이상 혼자 고민 금지 → 바로 공유 | 5주밖에 없어. 시간 낭비 = 팀 전체 지연 |
| 코드 리뷰 | PR 올리면 24시간 이내 리뷰 | 리뷰 병목이 가장 흔한 일정 지연 원인 |
| 긴급 연락 | 카카오톡 or 디스코드? | **지금 정하자** |
| 주간 회고 | 금요일 마지막 15분 | 잘된 것 / 개선할 것 / 다음 주 목표 |

---

## 9. 지금 당장 할 것

### Day 0 — 오늘 (~1시간)

환경 세팅이야. 오늘 안에 끝내자.

- [ ] GitHub 계정 2FA 설정 + [team-project-final](https://github.com/team-project-final) org 합류
- [ ] `gh` CLI 설치 + `gh auth login`
- [ ] 개발 도구 설치:
  - Java 21 (Temurin) — 김해준, 한승완, 김현지, 박은서, 김나경
  - Python 3.11 — 조유지
  - Flutter 3 — 전원
  - Docker Desktop — 전원
- [ ] 자기 서비스 레포 clone: `gh repo clone team-project-final/{레포이름}`
- [ ] 위키 5개 문서 순서대로 읽기:
  1. [18 기술 스택 정의서](https://github.com/team-project-final/documents/wiki) — 전체 스택 파악
  2. [03 아키텍처 정의서](https://github.com/team-project-final/documents/wiki) — 4-서비스 구조
  3. [09 Git 규칙 정의서](https://github.com/team-project-final/documents/wiki) — 브랜치/PR 규칙
  4. [09a Git 워크플로 가이드](https://github.com/team-project-final/documents/wiki) — 실전 시나리오
  5. [17 스케줄 v3.0](https://github.com/team-project-final/documents/wiki) — 너의 트랙 W1~W5
- [ ] [DESIGN.md](../../DESIGN.md) 읽기 — 디자인 시스템/색상/폰트

### Day 1 — 내일 (~3시간)

트랙별 온보딩이야. `docs/onboarding/` 폴더에 가이드가 있어.

- [ ] 공통 가이드 따라가기: [`00-common-day1.md`](onboarding/00-common-day1.md)
- [ ] 자기 트랙 가이드 따라가기:
  - 김해준: [`01-platform-track.md`](onboarding/01-platform-track.md)
  - 한승완: [`02-engagement-track.md`](onboarding/02-engagement-track.md)
  - 김현지, 박은서: [`03-knowledge-track.md`](onboarding/03-knowledge-track.md)
  - 김나경, 조유지: [`04-learning-track.md`](onboarding/04-learning-track.md)
  - 전원: [`05-frontend.md`](onboarding/05-frontend.md)
- [ ] SECRETS.md 확인 + 필요한 credential 수령
- [ ] 첫 PR 올리기 (서비스 골격 → 실제 기능 수준)
- [ ] TASK 파일에서 Step 1 확인 → Status를 "In Progress"로 갱신

---

## 정리

```
┌─────────────────────────────────────────────┐
│                                             │
│  1. 지금 Day 0 체크리스트 시작해              │
│  2. 막히면 30분 이상 고민하지 말고 바로 물어봐  │
│  3. 내일 데일리 때 Day 0 완료 여부 공유        │
│                                             │
└─────────────────────────────────────────────┘
```
