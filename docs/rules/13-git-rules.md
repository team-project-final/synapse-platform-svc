# 13. Git 규칙 — synapse-platform-svc

> 기반 문서: 09_Git_규칙_정의서 v2.0.1 (폴리레포 + GitHub Flow)
> 이 프로젝트 선택: PR 타겟을 `dev` 브랜치로 운영

---

## 13.1 브랜치 전략 [MUST]

```
main  (최종 배포)
  └─ dev  (통합 개발)
       └─ feature/PLAT-{NNN}-{설명}  ← 여기서 작업
            └─ PR → dev → approve 2명 → squash merge
```

- `main` / `dev` 직접 push 절대 금지
- 브랜치 수명: 최대 5일 (초과 시 분할)
- 머지 후 원격 브랜치 자동 삭제
- force push 금지

### 브랜치 명명 규칙

| prefix | 용도 | 예시 |
|--------|------|------|
| `feature/PLAT-NNN-` | 기능 개발 | `feature/PLAT-001-oauth-google` |
| `fix/PLAT-NNN-` | 버그 수정 | `fix/PLAT-003-jwt-expiry` |
| `hotfix/PLAT-NNN-` | 긴급 수정 | `hotfix/PLAT-012-jwt-leak` |
| `chore/PLAT-NNN-` | 설정/인프라 | `chore/PLAT-002-dockerfile` |
| `docs/PLAT-NNN-` | 문서 | `docs/PLAT-004-api-spec` |
| `refactor/PLAT-NNN-` | 리팩토링 | `refactor/PLAT-005-auth-service` |
| `test/PLAT-NNN-` | 테스트 | `test/PLAT-006-jwt-unit` |

---

## 13.2 커밋 메시지 [MUST]

**Conventional Commits** 형식 준수.

```
<type>(<scope>): <subject>

[body — "왜" 이 변경이 필요한지 설명] [SHOULD]

[footer — Closes #N, BREAKING CHANGE]
```

### Type 정의

| type | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `style` | 포맷팅 (로직 변경 없음) |
| `refactor` | 리팩토링 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드, CI, 의존성 등 |
| `perf` | 성능 개선 |
| `ci` | CI/CD 설정 변경 |

### Scope (platform-svc 모듈 기준)

`auth` / `billing` / `notification` / `audit` / `infra` / `shared`

### ✅ Good

```
feat(auth): Google OAuth 로그인 구현

Authorization Code Grant 방식으로 Google OAuth 연동.
신규 사용자 자동 회원가입 + 기존 사용자 email 매핑 포함.

Closes #5
```

### ❌ Bad

```
수정함
fix bug
feat: 여러가지 고침
```

---

## 13.3 PR 규칙 [MUST]

### PR 제목 형식

```
<type>(<scope>): <설명> (#이슈번호)
```

예시: `feat(auth): Google OAuth 로그인 구현 (#5)`

### PR 승인 정책

| 변경 종류 | 필요 승인 |
|----------|----------|
| 일반 feature/fix | `@team-lead` + 트랙 owner (2명) |
| Auth/보안 변경 | `@team-lead` + `@platform-owner` 이중 승인 |
| Hotfix | `@team-lead` 단독 |

### PR 크기 제한

변경 400줄 이하 권장 [SHOULD]. 초과 시 분할.

### PR 본문 템플릿

```markdown
## 변경 사항

-
-

## 변경 유형
- [ ] feat (새 기능)
- [ ] fix (버그 수정)
- [ ] refactor
- [ ] docs
- [ ] test
- [ ] chore

## 관련 이슈
<!-- Closes #이슈번호 -->

## 테스트 방법

1.
2.

## 체크리스트
- [ ] 코드 셀프 리뷰 완료
- [ ] 테스트 추가/수정 완료
- [ ] Breaking change 여부 확인

## 영향 받는 다른 서비스
- [ ] platform-svc
- [ ] engagement-svc
- [ ] knowledge-svc
- [ ] learning-svc
- [ ] frontend
- [ ] (영향 없음)

## 이벤트/스키마 변경 여부
- [ ] 새 Kafka 토픽 추가
- [ ] 기존 토픽 스키마 변경
- [ ] (변경 없음)

## 미러링/GitOps 영향
- [ ] 자동 미러링 정상 확인
- [ ] GitOps image tag 자동 업데이트 정상
- [ ] (해당 없음)
```

---

## 13.4 절대 금지 [MUST]

- `main` / `dev` 브랜치 직접 commit / push
- `git push --force`
- `.env`, `*.key`, `*.pem`, `*.kubeconfig` 파일 커밋
- Classic PAT 사용 (fine-grained PAT만 허용)
- `synapse-mirror` 직접 commit (Action만 write 권한)

---

## 13.5 HISTORY 일일 로그 [MUST]

매일 퇴근 전 `docs/project-management/history/HISTORY_platform.md` 갱신.

```markdown
## YYYY-MM-DD (요일)

**한 일**
- feat(auth): Google OAuth 구현 완료

**이슈**
- Redis 연결 타임아웃 간헐적 발생 → 설정 검토 필요

**내일 계획**
- JWT 발급 로직 구현 시작
```
