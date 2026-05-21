# CONTEXT

> 현재 판단에 필요한 상태만 기록합니다.
> 히스토리, 과정, 설명은 포함하지 않습니다.
> 태스크 완료 시 archive로 이동 후 이 파일을 초기화합니다.

## 현재 확정된 것

- Step 5 코드가 `feature/PLAT-005-fcm-device` 브랜치에서 구현되어 main PR #24로 머지 완료
- `V27__create_device_tokens.sql` Flyway 마이그레이션 파일 존재
- notification 모듈 파일 목록:
  - `DeviceTokenController.java`
  - `DeviceTokenService.java`
  - `DeviceTokenRepository.java`
  - `DeviceToken.java` (entity)
  - `Platform.java` (enum)
  - `DeviceTokenIntegrationTest.java`
  - `DeviceTokenServiceTest.java`
- TASK_platform.md: Done When 체크박스 미체크 / Status 줄은 Done 표기 (불일치 상태)

## 현재 미결 사항

- Done When 5개 항목 실제 충족 여부 미확인 (Worker 검증 필요)
- 통합 테스트 실제 PASS 여부 미확인

## 활성 제약

- JWT 서명: RS256 고정
- 모듈 간 순환 의존 금지
- 테스트 커버리지: 신규 코드 80% 이상
- 한 사용자 최대 5개 디바이스
- platform 값: `ios`, `android`, `web` (소문자)

## 참고할 공식 문서

- docs/project-management/task/TASK_platform.md (Step 5)
- docs/rules/07-platform.md
