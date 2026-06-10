# HANDOFF - PLAT-070: Auth 복구 플로우 보강

## 한줄 요약

루트 `docs/BACKEND_GAP_platform.md` A-2 작업이다. 비밀번호 재설정 3단계 API와 MFA 백업 코드 발급/검증 API 구현 및 검증이 완료됐다.

## 현재 상태

- 작업 브랜치 생성 완료
- PLAT-069 current 문서 archive 완료
- PLAT-070 작업문서 작성 완료
- 비밀번호 재설정 API 구현 완료
- MFA 백업 코드 발급/검증 API 구현 완료
- controller/service/repository/security 테스트 완료
- `PlatformModuleStructureTest` 완료
- `clean build` 완료

## 작업 위치

- Repo: `synapse-platform-svc`
- Branch: `feature/PLAT-070-auth-recovery`
- Base: `dev`
- 작업문서: `docs/ai/current`
- 이전 current archive: `docs/ai/archive/20260610-plat-069-completed`

## 구현 결과

1. 비밀번호 재설정 API
   - `POST /api/v1/auth/password-reset/request`
   - `POST /api/v1/auth/password-reset/verify`
   - `POST /api/v1/auth/password-reset/confirm`
   - code/token hash 저장
   - 계정 존재 여부 비노출
   - 만료/시도 횟수/1회 사용
   - password 변경 후 session revocation

2. MFA 백업 코드 API
   - `POST /api/v1/auth/mfa/backup-codes`
   - `POST /api/v1/auth/mfa/backup`
   - TOTP active 사용자만 발급
   - backup code `PasswordEncoder` hash 저장
   - 검증 성공 시 1회 사용 처리
   - TOTP secret 재설정 시 기존 미사용 backup code 폐기

3. 발송 경계
   - notification 내부 SES service 직접 주입 금지
   - `NotificationSend` Kafka 이벤트 발행으로 reset code 전달
   - notification consumer/SES 설정이 켜진 환경에서 실제 email 발송
   - `PASSWORD_RESET_CODE` email은 일반 notification 일일 quota 예외 및 quota 집계 제외

## 주요 파일

- `src/main/resources/db/migration/V20260610123000__create_auth_recovery_tables.sql`
- `src/main/java/com/synapse/platform/auth/entity/PasswordResetRequest.java`
- `src/main/java/com/synapse/platform/auth/entity/MfaBackupCode.java`
- `src/main/java/com/synapse/platform/auth/repository/PasswordResetRequestRepository.java`
- `src/main/java/com/synapse/platform/auth/repository/MfaBackupCodeRepository.java`
- `src/main/java/com/synapse/platform/auth/service/PasswordResetService.java`
- `src/main/java/com/synapse/platform/auth/service/PasswordResetCodeSender.java`
- `src/main/java/com/synapse/platform/auth/service/KafkaPasswordResetCodeSender.java`
- `src/main/java/com/synapse/platform/auth/service/NoopPasswordResetCodeSender.java`
- `src/main/java/com/synapse/platform/auth/service/TotpService.java`
- `src/main/java/com/synapse/platform/auth/controller/AuthController.java`
- `src/main/java/com/synapse/platform/auth/controller/MfaController.java`
- `src/main/java/com/synapse/platform/user/api/UserApi.java`
- `src/main/java/com/synapse/platform/user/service/UserService.java`

테스트:

- `src/test/java/com/synapse/platform/auth/controller/AuthControllerTest.java`
- `src/test/java/com/synapse/platform/auth/controller/MfaControllerTest.java`
- `src/test/java/com/synapse/platform/auth/service/PasswordResetServiceTest.java`
- `src/test/java/com/synapse/platform/auth/service/KafkaPasswordResetCodeSenderTest.java`
- `src/test/java/com/synapse/platform/auth/service/TotpServiceTest.java`
- `src/test/java/com/synapse/platform/auth/repository/PasswordResetRequestRepositoryLockingTest.java`
- `src/test/java/com/synapse/platform/auth/repository/MfaCredentialRepositoryLockingTest.java`
- `src/test/java/com/synapse/platform/auth/repository/MfaBackupCodeRepositoryLockingTest.java`
- `src/test/java/com/synapse/platform/auth/config/AuthRecoverySecurityIntegrationTest.java`
- `src/test/java/com/synapse/platform/PlatformModuleStructureTest.java`

## API 후보

비밀번호 재설정 요청:

```http
POST /api/v1/auth/password-reset/request
```

```json
{
  "email": "user@example.com"
}
```

비밀번호 재설정 코드 검증:

```http
POST /api/v1/auth/password-reset/verify
```

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

비밀번호 재설정 확정:

```http
POST /api/v1/auth/password-reset/confirm
```

```json
{
  "resetToken": "one-time-reset-token",
  "newPassword": "Newpass1!"
}
```

MFA 백업 코드 발급:

```http
POST /api/v1/auth/mfa/backup-codes
Authorization: Bearer <token>
```

MFA 백업 코드 검증:

```http
POST /api/v1/auth/mfa/backup
Authorization: Bearer <token>
```

```json
{
  "code": "ABCD-EFGH"
}
```

## 검증 명령

```powershell
.\gradlew.bat test --tests "*PasswordResetServiceTest" --tests "*TotpServiceTest" --tests "*AuthControllerTest" --tests "*MfaControllerTest" --tests "*UserServiceTest" --tests "*PasswordResetRequestRepositoryLockingTest" --tests "*MfaBackupCodeRepositoryLockingTest"
.\gradlew.bat test --tests "*PasswordResetServiceTest" --tests "*KafkaPasswordResetCodeSenderTest" --tests "*TotpServiceTest" --tests "*MfaCredentialRepositoryLockingTest" --tests "*MfaBackupCodeRepositoryLockingTest" --tests "*PasswordResetRequestRepositoryLockingTest"
.\gradlew.bat test --tests "*NotificationServiceTest"
.\gradlew.bat test --tests "*AuthRecoverySecurityIntegrationTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

결과:

- 위 명령 모두 PASS
- `clean build`는 `BUILD SUCCESSFUL`
- Windows Kafka temp directory deletion warning은 shutdown 중 출력되지만 빌드 실패 원인은 아님

## 후속 작업

- 운영/스테이징에서 notification-send topic, notification consumer, SES 설정 확인
- 로그인 전 MFA backup challenge가 필요하면 별도 challenge session 모델 설계 필요

## 금지/주의

- `TASK_platform.md` 수정 금지
- env/profile 수정 금지
- gitops/shared 수정 금지
- notification 내부 service를 auth에서 직접 주입하지 말 것
- password reset request에서 계정 존재 여부를 노출하지 말 것
- reset code/token/backup code 원문을 DB에 저장하지 말 것
- PR 생성 전 `docs/rules/13-git-rules.md` 전체 확인
- PR 본문 작성 시 UTF-8 body file 사용
