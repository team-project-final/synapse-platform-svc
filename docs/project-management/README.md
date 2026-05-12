# Synapse 프로젝트 관리 문서 체계

> **프로젝트명**: Synapse — 통합 학습-지식 그래프 SaaS  
> **기간**: 2026-05-12 ~ 2026-06-15 (5주 + 발표일, 22 영업일 — 5/25 부처님오신날·6/3 지방선거 제외)  
> **팀 구성**: 팀장 1명 + 팀원 6명

---

## 1. 개요

본 문서 체계는 Synapse 프로젝트의 **작업 정의 → 진행 → 완료**를 추적 가능하게 하는 5종 문서 시스템이다.

---

## 2. 문서 흐름

```
SCOPE (5주 전체 책임 정의)
  ↓ 참조
PRD (주차별 요구사항)
  ↓ 분해
TASK (Step 단위 작업 정의 — 필수 10필드)
  ↓ 세분화
WORKFLOW (Step별 기능개발 10단계)
  ↓ 기록
HISTORY (상태 대시보드 + 날짜별 일지)
```

---

## 3. 문서별 역할

| 문서 | 위치 | 역할 | 작성 시점 |
|------|------|------|-----------|
| SCOPE | `scope/SCOPE_{담당자}.md` | 담당자의 5주 전체 책임/경계 정의 | 프로젝트 시작 시 |
| PRD | `prd/PRD_W{N}.md` | 주차별 기능/비기능 요구사항 | 각 주차 시작 전 |
| TASK | `task/TASK_{담당자}.md` | Step 단위 작업 정의 (필수 10필드) | PRD 확정 후 |
| WORKFLOW | `workflow/WORKFLOW_{담당자}_W{N}.md` | Step을 기능개발 10단계로 세분화 | TASK 확정 후 |
| HISTORY | `history/HISTORY_{담당자}.md` | 상태 추적 + 날짜별 작업 일지 | 매일 업데이트 |

---

## 4. 디렉토리 구조

```
docs/project-management/
├── README.md                              # 본 문서
├── scope/                                 # 작업 스코프 정의
│   ├── SCOPE_team-lead.md
│   ├── SCOPE_platform.md
│   ├── SCOPE_engagement.md
│   ├── SCOPE_knowledge-1.md
│   ├── SCOPE_knowledge-2.md
│   ├── SCOPE_learning-card.md
│   └── SCOPE_learning-ai.md
├── prd/                                   # 주차별 PRD (W1~W5)
│   ├── PRD_W1.md
│   ├── PRD_W2.md
│   ├── PRD_W3.md  # 이벤트 발행자 + RRF + AI 자동 생성
│   ├── PRD_W4.md  # 이벤트 소비자 + 운영
│   └── PRD_W5.md  # E2E + 발표 준비
├── task/                                  # 담당자별 Task
│   ├── TASK_team-lead.md
│   ├── TASK_platform.md
│   ├── TASK_engagement.md
│   ├── TASK_knowledge-1.md
│   ├── TASK_knowledge-2.md
│   ├── TASK_learning-card.md
│   ├── TASK_learning-ai.md
│   └── TASK_frontend.md
├── workflow/                              # 담당자 × 주차
│   ├── WORKFLOW_team-lead_W1.md
│   ├── WORKFLOW_platform_W1.md
│   └── ...
└── history/                               # 담당자별 이력
    ├── HISTORY_team-lead.md
    └── ...
```

---

## 5. 작업 진행/완료 시 업데이트 규칙

| 이벤트 | PRD | TASK | WORKFLOW | HISTORY |
|--------|-----|------|----------|---------|
| **작업 시작** | — | Step Status → `In Progress` | 해당 단계 시작 표시 | 시작일 기록 + 로그 작성 |
| **하위 단계 완료** | — | — | 체크박스 `[x]` 체크 | 로그에 완료 기록 |
| **Step 완료** | — | Step Status → `Done` | 전체 체크박스 완료 | 완료일 기록 + 로그 |
| **주차 종료** | 성공 기준 체크 | — | — | 주간 요약 작성 |
| **이슈 발생** | — | Constraints 갱신 | 해당 단계에 이슈 메모 | 이슈 기록 |
| **스코프 변경** | 요구사항 수정 | Scope 필드 갱신 | — | 변경 사유 기록 |

### 핵심 원칙

> **작업 진행과 작업 완료 시, PRD / TASK / WORKFLOW / HISTORY 문서를 모두 확인·체크·업데이트 해야 한다.**

---

## 6. Task 문서 작성 규칙

### 6.1 용어 정의

| 용어 | 의미 |
|------|------|
| TASK | 문서 단위. `TASK_SERVER.md` 등 하나의 md 파일이 하나의 TASK 문서 |
| Step | TASK 문서 내 개별 작업 단위. 필수 필드로 정의되는 하나의 작업 블록 |

### 6.2 필수 필드 (10개)

| # | 필드 | 설명 |
|---|------|------|
| 1 | **Step Name** | 단계 이름 |
| 2 | **Step Goal** | 측정 가능한 목표 문장 (§6.3 참조) |
| 3 | **Done When** | 성공 기준 (**Step Goal 바로 다음 고정 배치**) |
| 4 | **Scope** | In Scope / Out of Scope (§6.5 참조) |
| 5 | **Input** | 필요한 입력 (문서·코드·환경 등) |
| 6 | **Instructions** | 수행할 작업 목록 |
| 7 | **Output Format** | 산출물 형태·위치·형식 |
| 8 | **Constraints** | Step 고유 규칙·제약 |
| 9 | **Duration** | 예상 작업일 (1명 기준) |
| 10 | **RULE Reference** | 참조 문서 위치 |
| (선택) | **Assignee** | 담당자 |
| (선택) | **Reviewer** | 리뷰어 |

### 6.3 Step Goal — 측정 가능 문장 강제

형식: **[주체]가 [대상]에 대해 [행위]를 [결과]한다.**

| 부적절 | 적절 |
|--------|------|
| 게시글 좋아요 기능 구현 | 로그인 사용자가 게시글에 좋아요를 최대 1회만 등록할 수 있다. |
| 회원 관리 기능 | 관리자가 회원을 상태별로 검색하고 일괄 정지할 수 있다. |
| API 개발 | 비로그인 사용자가 게시글 목록을 페이지네이션으로 조회할 수 있다. |

### 6.4 Done When — 고정 배치 (강제)

**Step Name → Step Goal → Done When → Scope → Input → ...**

Done When은 Step Goal 바로 다음에 반드시 배치한다.

### 6.5 Scope — In/Out 고정 구조

```markdown
- **Scope**:
  - In Scope:
    - 포함 항목 1
    - 포함 항목 2
  - Out of Scope:
    - 제외 항목 1
    - 향후 고려: ...
```

### 6.6 Constraints vs RULE Reference 구분

| 필드 | 의미 |
|------|------|
| Constraints | 이번 Step 고유 제약 (예: "인증 필수", "N+1 방지") |
| RULE Reference | 참조 문서 위치 (예: "공통_개발_규칙.md §3.2") |

### 6.7 강제 사항

- 신규 Step 추가 시 **10개 필수 필드 모두 작성**
- 필드 누락 시 코드 리뷰에서 보완 요청 대상
- Step Goal이 측정 불가능한 문장이면 반려

---

## 7. 기능 개발 Workflow (10단계)

TASK 문서의 각 Step을 실행할 때, 아래 10단계를 순서대로 따른다. WORKFLOW 문서에 체크박스로 추적한다.

```
TASK 시작  ← Step Goal/Done When/Scope/Input/Duration 확인
    ↓
요구사항 분석  ← Instructions 초안, Constraints
    ↓
Security 1차 검토  ← 인증/권한/공개API 체크
    ↓
ERD 설계  ← Duration(final) 갱신
    ↓
Security 2차 검토  ← 암호화/SoftDelete/행단위접근 체크
    ↓
DTO/Entity 설계 (API First: DTO → Entity)
    ↓
Repository 구현
    ↓
Service + Test (병행)
    ↓
Controller + Test (병행, 401/403 포함)
    ↓
View + Test (Smoke 1건 이상 필수)
```

### Security 1차 체크리스트
- [ ] 인증 필요 여부
- [ ] 권한 종류 (일반/관리자/특정 권한)
- [ ] 공개 API 여부 → Rate Limiting 필요성

### Security 2차 체크리스트
- [ ] 민감 정보 암호화 여부
- [ ] Soft Delete vs 물리 삭제
- [ ] 행 단위 접근 제어(Row-level) 필요 여부

### 설계 철학

본 프로젝트는 **API First**를 기본으로 한다: DTO 설계 → Entity 순서. 도메인 복잡도가 높은 기능은 팀 합의로 Entity 우선을 허용한다.

---

## 8. 담당자 매핑표

| Handle | 트랙 | 서비스 | 모듈 | 파일 접미사 |
|--------|------|--------|------|------------|
| `@team-lead` | 팀장 | Gateway / 인프라 | Schema Registry, ArgoCD, CI/CD | `team-lead` |
| `@platform-owner` | A | synapse-platform-svc | auth, audit, billing, notification | `platform` |
| `@engagement-owner` | B | synapse-engagement-svc | community, gamification | `engagement` |
| `@knowledge-owner-1` | C-1 | synapse-knowledge-svc | note, graph | `knowledge-1` |
| `@knowledge-owner-2` | C-2 | synapse-knowledge-svc | chunking, 검색, Modulith | `knowledge-2` |
| `@learning-card-owner` | D-1 | synapse-learning-svc | card, srs (Java) | `learning-card` |
| `@learning-ai-owner` | D-2 | synapse-learning-svc | ai (Python/FastAPI) | `learning-ai` |
| 전체 협업 | — | synapse-frontend | Flutter UI | `frontend` |

---

## 9. GitHub Repositories

| 레포지토리 | 가시성 | 용도 | 담당 |
|------------|:------:|------|------|
| [synapse-platform-svc](https://github.com/team-project-final/synapse-platform-svc) | public | auth · audit · billing · notification | @platform-owner |
| [synapse-engagement-svc](https://github.com/team-project-final/synapse-engagement-svc) | public | community · gamification | @engagement-owner |
| [synapse-knowledge-svc](https://github.com/team-project-final/synapse-knowledge-svc) | public | note · graph · chunking | @knowledge-owner-1, @knowledge-owner-2 |
| [synapse-learning-svc](https://github.com/team-project-final/synapse-learning-svc) | public | card · srs (Java) + ai (Python) | @learning-card-owner, @learning-ai-owner |
| [synapse-frontend](https://github.com/team-project-final/synapse-frontend) | public | Flutter (web/mobile) | 전체 협업 |
| [synapse-shared](https://github.com/team-project-final/synapse-shared) | public | Avro 스키마 + 공통 라이브러리 | @team-lead |
| [synapse-mirror](https://github.com/team-project-final/synapse-mirror) | private | Tier 1 자동 미러 (읽기 전용) | 자동 (Actions) |
| [synapse-gitops](https://github.com/team-project-final/synapse-gitops) | private | K8s manifest + ArgoCD ApplicationSet | @team-lead |
| [syn](https://github.com/team-project-final/syn) | public | 부트스트랩 스크립트 + 스펙 + 플랜 | @team-lead |
| [documents](https://github.com/team-project-final/documents) | public | 프로젝트 관리 문서 (본 레포) | 전체 |

---

## 참조 문서

| 문서 | 위치 |
|------|------|
| 스케줄 | [wiki 17_스케줄](https://github.com/Public-Project-Area-Oragans/syn/wiki/17_스케줄) |
| 아키텍처 | [wiki 03_아키텍처_정의서](https://github.com/Public-Project-Area-Oragans/syn/wiki/03_프로젝트_아키텍처_정의서) |
| Git 규칙 | [wiki 09_Git_규칙_정의서](https://github.com/Public-Project-Area-Oragans/syn/wiki/09_Git_규칙_정의서) |
| 공통 개발 규칙 | [docs/공통_개발_규칙.md](../공통_개발_규칙.md) |
| Spring 컨벤션 | [docs/Spring_개발_컨벤션.md](../Spring_개발_컨벤션.md) |
| Flutter 컨벤션 | [docs/Flutter_개발_컨벤션.md](../Flutter_개발_컨벤션.md) |
