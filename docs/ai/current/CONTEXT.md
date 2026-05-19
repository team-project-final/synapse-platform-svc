# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

### 리팩토링 목표 (2026-05-19 확정)

- **패키지 루트**: `io.synapse.platform` → `com.synapse.platform`
- **shared → global**: `shared/` 모듈 → `global/` 로 이름 변경
- **auth 서브패키지 평탄화**: `jwt/`, `mfa/`, `oauth/` 해체 → `controller/`, `service/`, `entity/`, `config/` 재배치
- **domain → entity**: 모든 모듈의 `domain/` 서브패키지 → `entity/`
- **Controller/Service 서브패키지**: `billing/`, `user/` 모듈의 flat 구조 → `controller/`, `service/` 서브패키지로
- **DTO 분리**: `billing/dto/` flat → `billing/dto/request/` + `billing/dto/response/`
- **불변 항목**: `auth/api/`, `user/api/` (Spring Modulith NamedInterface)

### 브랜치

- `refactor/PLAT-REF-001-template-align` (dev에서 생성, 현재 브랜치)

### 참고 템플릿

- `docs/synapse-svc-template-skeleton-platform-w1/` (W1 교육용 스켈레톤)
- README 기준: W2에서 `global/` 추가 → 현재 `shared/` → `global/` 선반영

## 현재 미결 사항

- 없음 (HANDOFF.md 작성 완료, Worker 실행 대기)

## 활성 제약

- 기능 코드 변경 금지 — 순수 구조(패키지/디렉토리) 변경만
- JWT 서명: RS256 고정
- Refresh Token: Redis 전용, DB 저장 금지
- Spring Modulith `auth/api/`, `user/api/` package-info 위치 불변
- 테스트 커버리지: 신규 코드 80% 이상

## 참고할 공식 문서

- docs/ai/current/HANDOFF.md (Worker 실행 스펙)
- docs/synapse-svc-template-skeleton-platform-w1/README.md
