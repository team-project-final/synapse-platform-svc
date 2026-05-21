# HANDOFF

**FROM**: Director (Claude)  
**TO**: Worker (Codex)  
**DATE**: 2026-05-21  
**SUBJECT**: Step 5 Done When 검증

---

## 요청 내용

Step 5 (FCM 디바이스 등록) 코드가 구현되어 main에 머지된 상태입니다.
그런데 TASK_platform.md의 Done When 체크박스가 비어 있습니다.

아래 Done When 항목을 코드를 읽고 직접 검증한 뒤 결과를 이 파일 하단에 기록해주세요.
신규 코드 작성은 하지 않습니다. 검증만 합니다.

---

## 검증 대상 파일

```
src/main/resources/db/migration/V27__create_device_tokens.sql
src/main/java/com/synapse/platform/notification/entity/DeviceToken.java
src/main/java/com/synapse/platform/notification/entity/Platform.java
src/main/java/com/synapse/platform/notification/controller/DeviceTokenController.java
src/main/java/com/synapse/platform/notification/service/DeviceTokenService.java
src/main/java/com/synapse/platform/notification/repository/DeviceTokenRepository.java
src/test/java/com/synapse/platform/notification/DeviceTokenIntegrationTest.java
src/test/java/com/synapse/platform/notification/DeviceTokenServiceTest.java
```

---

## Done When 체크리스트 (각 항목 PASS / FAIL / PARTIAL 판정)

| # | 기준 | 판정 | 근거 (파일:라인) |
|---|------|------|-----------------|
| 1 | `POST /notifications/devices` 엔드포인트 존재 + JWT 인증 필수 | PASS | `DeviceTokenController.java:18-33` (`/api/v1/notifications/devices`), `NotificationSecurityConfig.java:29-33`, `DeviceTokenIntegrationTest.java:197-202` |
| 2 | `DELETE /notifications/devices/{id}` 엔드포인트 존재 + 본인 소유 검증 | PASS | `DeviceTokenController.java:36-41`, `DeviceTokenService.java:40-45`, `DeviceTokenIntegrationTest.java:206-228` |
| 3 | `device_tokens` 테이블 DDL 완비 (id, user_id, token, platform, is_active, created_at, UNIQUE(token)) | PASS | `V27__create_device_tokens.sql:1-10`, `DeviceToken.java:17-40` |
| 4 | 통합 테스트 실행 결과 PASS (`./gradlew test --tests "*.notification.*"`) | PASS | `build/reports/tests/test/index.html:41-70`, `build/reports/tests/test/index.html:88-108` |

## 추가 확인 항목

| # | 기준 | 판정 | 근거 |
|---|------|------|------|
| A | 한 사용자 최대 5개 디바이스 제한 로직 존재 | PASS | `DeviceTokenService.java:18`, `DeviceTokenService.java:31-33`, `DeviceTokenServiceTest.java:38-49`, `DeviceTokenIntegrationTest.java:164-178` |
| B | platform 값 `ios`/`android`/`web` 소문자 강제 | PARTIAL | DB 저장/제약은 소문자 강제: `V27__create_device_tokens.sql:6`, `Platform.java:7-9`, `PlatformConverter.java:10-12`, `DeviceTokenServiceTest.java:75-80`. 단, API 역직렬화는 `equalsIgnoreCase`라 대문자 입력을 거부하지 않음: `Platform.java:22-26` |
| C | 중복 토큰 등록 시 upsert 또는 409 처리 | PASS | `DeviceTokenRepository.java:20-34`, `DeviceTokenService.java:31-35`, `DeviceTokenIntegrationTest.java:128-162` |

---

## Worker 검증 결과

> 아래에 결과를 채워주세요.

### 테스트 실행 결과

```
> .\gradlew.bat test --tests "*.notification.*"

Note: C:\workspace\team_project_2\synapse-platform-svc\src\main\java\com\synapse\platform\billing\service\BillingService.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: C:\workspace\team_project_2\synapse-platform-svc\src\test\java\com\synapse\platform\billing\BillingServiceTest.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

Exit code: 0

Test report:
- DeviceTokenIntegrationTest: tests=9, failures=0, skipped=0, success=100%
- DeviceTokenServiceTest: tests=7, failures=0, skipped=0, success=100%
- Total: tests=16, failures=0, skipped=0, success=100%
```

### 판정 요약

Done When 4개 항목은 모두 PASS입니다.

추가 확인 항목은 A/C PASS, B PARTIAL입니다. `platform`은 DB CHECK 제약과 JPA converter를 통해 저장값이 `ios`/`android`/`web` 소문자로 강제됩니다. 다만 `Platform.from()`이 `equalsIgnoreCase`를 사용하므로 API 요청에서 `"IOS"` 같은 대문자 입력은 400으로 거부되지 않고 정상 매핑될 수 있습니다.

### Director에게 전달할 사항

Step 5 Done When 자체는 충족으로 판단됩니다.

다만 "platform 소문자 강제"가 API 입력에서도 엄격히 소문자만 허용한다는 의미라면 Fix가 필요합니다. 현재는 `Platform.java:22-26`에서 대소문자 무시 매칭을 사용하므로, 대문자 입력 거부 테스트를 추가하고 `equals` 기반 비교로 바꾸는 후속 작업이 필요합니다.

## 필요한 출력 형식

위 표의 판정 컬럼을 PASS/FAIL/PARTIAL로 채우고, 테스트 실행 결과를 붙여넣어 주세요.

## 첨부할 파일

- docs/ai/agent/worker.md
- docs/ai/current/CONTEXT.md

## 기한

2026-05-21
