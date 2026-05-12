# 5. 운영 RULE — Operation

> **참조**: [전체 Rule 목록](../rules/) | [준수 체크리스트](appendix-c-checklist.md)

---

## 5.1 배포 전략 \[MUST\]

배포는 **GitOps 단일 경로**로만 진행해. ArgoCD가 Git 상태를 보고 클러스터를 동기화하는 구조야.
수동으로 `kubectl apply` 치는 거 절대 하지 마.

| 환경 | 동기화 방식 | 승인 |
|---|---|---|
| dev | autoSync | 불필요 (PR 머지 시 자동) |
| staging | 수동 Sync | 테크 리드 승인 |
| prod | 수동 Sync | 테크 리드 + PM 승인 |

```yaml
# ✅ Good — ArgoCD Application에서 dev autoSync 설정
apiVersion: argoproj.io/v1alpha1
kind: Application
spec:
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

```bash
# ❌ Bad — 수동 kubectl로 직접 배포
kubectl apply -f deployment.yaml --context=prod
```

> **이유**: GitOps 단일 경로를 지켜야 Git이 유일한 진실 공급원(Single Source of Truth)이 돼. 수동 배포하면 Git과 클러스터 상태가 어긋나서 추적이 안 돼.

---

## 5.2 롤백 \[MUST\]

이상 징후 발생 시 **60초 이내**에 판단하고, 이전 이미지 태그로 복원해.
새 코드를 고치는 게 아니라 **검증된 이전 버전으로 되돌리는 게 우선**이야.

### 롤백 절차

1. **감지** — 에러율 급증 또는 헬스체크 실패 확인 (60초 이내 판단)
2. **ArgoCD Rollback** — 이전 커밋의 이미지 태그로 GitOps 매니페스트 복원
3. **Sync** — ArgoCD에서 수동 Sync 실행
4. **검증** — 헬스체크 + 핵심 API 스모크 테스트
5. **공유** — Slack `#ops-alert` 채널에 롤백 사실과 원인 공유

```yaml
# ✅ Good — 이전 검증된 이미지 태그로 복원
image: ghcr.io/synapse/card-service:abc1234
```

```yaml
# ❌ Bad — latest 태그로 롤백 시도
image: ghcr.io/synapse/card-service:latest
```

> **이유**: 장애 상황에서 코드를 고치려고 하면 시간만 날려. 검증된 이전 버전으로 먼저 복원하고, 원인 분석은 그 다음에 해.

---

## 5.3 Health Check \[MUST\]

모든 서비스는 **liveness**와 **readiness** 두 가지 헬스체크를 반드시 제공해야 해.

| 스택 | Liveness | Readiness |
|---|---|---|
| Spring Boot | `/actuator/health/liveness` | `/actuator/health/readiness` |
| FastAPI | `/health` | `/health/ready` |

```yaml
# ✅ Good — K8s probe 설정
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

> **이유**: liveness는 프로세스가 살아있는지, readiness는 트래픽을 받을 준비가 됐는지 확인해. 둘을 분리해야 재시작 루프 없이 graceful한 트래픽 제어가 가능해.

---

## 5.4 장애 대응 \[SHOULD\]

장애가 발생하면 아래 순서대로 대응해. 혼자 해결하려고 하지 말고 **즉시 공유**가 핵심이야.

### 장애 대응 플로우

1. **발생** — 알림 수신 또는 수동 감지
2. **공유** — Slack `#ops-alert`에 현상 + 영향 범위 공유 (5분 이내)
3. **격리** — 장애 서비스 트래픽 차단 또는 Circuit Breaker 활성화
4. **롤백/핫픽스** — 5.2 롤백 절차 우선, 불가능 시 핫픽스 브랜치
5. **포스트모템** — 48시간 이내 원인 분석 + 재발 방지책 문서화

### 포스트모템 템플릿

```markdown
## 장애 포스트모템 — YYYY-MM-DD
- **영향 시간**: HH:MM ~ HH:MM (총 N분)
- **영향 범위**: 서비스명, 사용자 수
- **근본 원인**: (5 Whys 기법 적용)
- **타임라인**: 분 단위 기록
- **재발 방지**: 액션 아이템 + 담당자 + 기한
```

> **이유**: 포스트모템은 비난이 아니라 학습이야. 48시간 안에 써야 기억이 생생할 때 정확한 원인을 잡을 수 있어.
