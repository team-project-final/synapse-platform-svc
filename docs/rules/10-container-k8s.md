# 10. 컨테이너 + K8s RULE — Container & Kubernetes

> **참조**: [전체 Rule 목록](../rules/) | [준수 체크리스트](appendix-c-checklist.md)

---

## 10.1 Dockerfile \[MUST\]

Dockerfile은 **multi-stage build**로 작성해. 빌드 도구가 최종 이미지에 포함되면 안 돼.
실행 유저는 **non-root**, `.dockerignore`는 필수야.

### Spring Boot Multi-Stage Dockerfile

```dockerfile
# ✅ Good — multi-stage + non-root
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN groupadd -r app && useradd -r -g app app
COPY --from=builder /build/build/libs/*.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`.dockerignore`에 `.git`, `.gradle`, `build/`, `.env*` 반드시 포함해.

> **이유**: multi-stage를 안 쓰면 JDK + Gradle 캐시가 이미지에 들어가서 용량이 3배 이상 커져. root로 실행하면 컨테이너 탈출 시 호스트 권한 탈취 위험이 있어.

---

## 10.2 이미지 태그 \[MUST\]

이미지 태그는 **git SHA**(short 7자리)를 사용해. `latest` 태그는 **절대 금지**야.

```yaml
# ✅ Good — git SHA 태그
image: ghcr.io/synapse/card-service:a1b2c3d

# ❌ Bad — latest 태그
image: ghcr.io/synapse/card-service:latest
```

> **이유**: `latest`는 어떤 버전이 배포됐는지 추적이 안 돼. 롤백할 때도 "어디로 돌릴지" 특정이 불가능해.

---

## 10.3 Kustomize \[MUST\]

K8s 매니페스트는 **Kustomize base/overlay** 구조로 관리해. 환경별 차이는 overlay에서 패치해.

### 디렉토리 구조

구조: `k8s/base/` (공통) + `k8s/overlays/{dev,staging,prod}/` (환경별 패치).

```yaml
# ✅ Good — base에 공통 리소스, overlay에서 환경별 패치
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - deployment.yaml
  - service.yaml
commonLabels:
  app.kubernetes.io/part-of: synapse
```

> **이유**: base/overlay 없이 환경별 YAML을 따로 관리하면 공통 변경 시 전부 수동 수정해야 해. 빠짐없이 반영하는 게 거의 불가능해져.

---

## 10.4 ArgoCD \[MUST\]

ArgoCD 동기화 정책은 환경별로 다르게 설정해. dev만 autoSync, 나머지는 수동이야.

| 환경 | syncPolicy | prune | selfHeal |
|---|---|---|---|
| dev | automated | true | true |
| staging | 수동 | false | false |
| prod | 수동 | false | false |

```yaml
# ✅ Good — dev만 autoSync
spec:
  syncPolicy:
    automated: { prune: true, selfHeal: true }
```

> **이유**: prod에 autoSync 걸면 잘못된 커밋이 머지되는 순간 바로 배포돼. 수동 승인 게이트가 있어야 실수를 잡을 수 있어. 상세 배포 정책은 [05-operation.md](05-operation.md) 참고.

---

## 10.5 리소스 \[MUST\]

모든 Pod에 **requests/limits**를 명시해. 안 쓰면 노드 리소스를 무한 점유해서 다른 Pod에 영향 줘.

### deployment.yaml resources 예시

```yaml
# ✅ Good — requests/limits 명시
spec:
  containers:
    - name: card-service
      resources:
        requests:
          cpu: 200m
          memory: 512Mi
        limits:
          cpu: 500m
          memory: 1Gi
```

```yaml
# ❌ Bad — resources 미설정 → OOMKill 또는 노드 과부하 위험
spec:
  containers:
    - name: card-service
      image: ghcr.io/synapse/card-service:a1b2c3d
```

> **이유**: requests가 없으면 스케줄러가 적절한 노드를 못 고르고, limits가 없으면 한 Pod이 노드 전체 메모리를 먹어버릴 수 있어.
