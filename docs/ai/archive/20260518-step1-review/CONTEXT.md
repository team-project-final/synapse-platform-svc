# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- Step 1~3 구현 완료 상태에서 팀장 문서 리뉴얼로 재점검 진행
- Step 1 점검 결과 3건 수정 필요 확정 (아래 미결 참고)
- 신규 TASK_platform.md 기준 Step 1 모듈: auth / audit / billing / notification (4개)
- auth, notification, admin, user, shared — package-info.java + @ApplicationModule 정상
- Dockerfile multi-stage build 정상
- build.gradle.kts Spring Modulith BOM 정상

## 현재 미결 사항

- [FIX-1] audit/package-info.java 삭제됨 → 재생성 필요
- [FIX-2] billing/package-info.java 미생성 → 신규 생성 필요
- [FIX-3] 테스트 클래스명 ModuleStructureTest → ApplicationModulesTest rename 필요
- docs 파일 merge conflict (UU: HISTORY_platform.md, TASK_platform.md, WORKFLOW_platform_W1.md) — Step 1 수정과 별도 처리

## 활성 제약

- JWT 서명: RS256 고정
- Refresh Token raw 원문 저장 금지 (DB token_hash + Redis 캐시, D-006)
- 사용자당 Refresh Token 1개 active (D-009, D-010)
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md
- docs/ai/decisions/DECISION_LOG.md — D-005 ~ D-010
- docs/rules/06-auth-token.md
