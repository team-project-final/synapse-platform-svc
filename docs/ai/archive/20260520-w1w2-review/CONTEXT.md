# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- dev 브랜치 기준 Step 1~5 모두 Done 처리됨
- 패키지 루트: `com.synapse.platform`
- 실제 모듈: auth, user, billing, notification, global, admin (총 6개)
  - `audit` 모듈 미구현 (W3 Step 6 대상)
  - `shared` 네이밍 → `global`로 변경된 상태
- Flyway 마이그레이션: V1~V3, V16~V27 (총 15개)
- 테스트 파일: 총 30개 (auth 21, billing 5, global 2, notification 2)
- 마지막 커밋: `feat(notification): FCM 디바이스 토큰 등록/해제 API 구현 (Step 5)`

## 현재 미결 사항

- W1~W2 리팩토링 이후 Done When 기준 실제 충족 여부 미검증
  - 멀티모듈 → Spring Modulith 전환 (D-017) 과정에서 변경된 코드 존재
  - 패키지 재편 과정에서 일부 누락 가능성

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token 전략: Redis(활성 세션) + DB refresh_tokens(영속성/감사) 이중 저장
- 모듈 간 순환 의존 금지 (`ApplicationModules.verify()` CI 자동 검증)
- 테스트 커버리지: 신규 코드 80% 이상
- Access Token 만료: 15분 / Refresh Token 만료: 7일

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md (Step 1~5 Done When)
- docs/project-management/workflow/WORKFLOW_platform_W1.md
- docs/project-management/workflow/WORKFLOW_platform_W2.md
