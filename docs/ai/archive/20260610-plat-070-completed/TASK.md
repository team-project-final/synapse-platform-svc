# TASK - PLAT-070: Auth 복구 플로우 보강

> 출처: 루트 `docs/BACKEND_GAP_platform.md` A-2. 프론트 `auth/password_reset_screen.dart`, `auth/mfa_screen.dart`가 비밀번호 재설정과 MFA 백업 코드 검증을 기대하지만, platform-svc는 현재 email/password 로그인, refresh, TOTP setup/verify까지만 제공한다.

## Task Metadata

| 필드 | 내용 |
|---|---|
| Task ID | `PLAT-070` |
| Title | Auth 복구 플로우 보강 |
| Owner | platform (김해준) |
| Status | `DONE` |
| Priority | `P1` |
| Step Goal | 사용자가 비밀번호를 잊었거나 TOTP 코드를 사용할 수 없을 때 안전하게 계정 접근을 복구할 수 있다. |
| Done When | 아래 `Done When` 체크리스트 기준 |
| Scope | 아래 `Scope` 기준 |
| Dependencies | `BACKEND_GAP_platform.md` A-2, `users`, `mfa_credentials`, `UserApi`, `PasswordEncoder`, notification/SES 발송 경계 |
| Due Date | 2026-06-12 |

## Step Goal

사용자가 email 기반 비밀번호 재설정 코드를 요청/검증/확정하고, MFA 백업 코드로 TOTP 대체 검증을 수행할 수 있다.

## Done When

- [x] 기존 PLAT-069 current 문서를 archive한다.
- [x] PLAT-070 작업 브랜치를 `dev`에서 생성한다.
- [x] PLAT-070 작업문서를 작성한다.
- [x] 비밀번호 재설정 저장 모델을 추가한다.
- [x] `POST /api/v1/auth/password-reset/request`가 email로 재설정 요청을 접수한다.
- [x] 재설정 요청은 계정 존재 여부를 노출하지 않고 동일 응답을 반환한다.
- [x] `POST /api/v1/auth/password-reset/verify`가 email+code를 검증하고 단기 reset token을 반환한다.
- [x] `POST /api/v1/auth/password-reset/confirm`이 reset token으로 새 비밀번호를 저장한다.
- [x] reset code/token은 원문 저장 없이 hash로 저장한다.
- [x] reset code/token은 만료와 1회 사용을 강제한다.
- [x] 비밀번호 변경 후 기존 refresh/session을 무효화한다.
- [x] MFA 백업 코드 저장 모델을 추가한다.
- [x] TOTP 활성화 사용자가 백업 코드를 발급/재발급할 수 있다.
- [x] `POST /api/v1/auth/mfa/backup`이 백업 코드를 1회성으로 검증한다.
- [x] 백업 코드는 원문 저장 없이 `PasswordEncoder` hash로 저장하고 사용 시 `used_at`을 남긴다.
- [x] controller/service/repository/security 테스트가 통과한다.
- [x] `PlatformModuleStructureTest`와 `clean build`가 통과한다.
- [x] `TASK_platform.md`, env/profile, gitops/shared 프로젝트는 수정하지 않는다.

## Scope

### In Scope

- 비밀번호 재설정 요청/검증/확정 API
- 비밀번호 재설정 code/token 저장 테이블
- reset code/token hash 저장
- reset code notification-send Kafka 발행
- 계정 존재 여부 비노출 응답
- 만료/시도 횟수/1회 사용 정책
- 비밀번호 확정 후 session/refresh 무효화 이벤트
- MFA 백업 코드 발급/재발급/검증 API
- MFA 백업 코드 저장 테이블
- 백업 코드 `PasswordEncoder` hash 저장 및 1회 사용 처리
- controller/service/repository/security 테스트

### Conditional Scope

- email 실제 발송 연결
- reset request rate limit
- MFA 백업 코드 목록 조회

조건:

- notification/SES의 공개 발송 경계가 명확하면 이번 작업에 email 발송 연결까지 포함한다.
- 발송 경계가 모듈 내부 구현체 직접 주입만 가능한 상태라면 notification 내부 구현체를 직접 주입하지 않는다. 이번 구현은 `NotificationSend` Kafka 이벤트 발행으로 연결한다.
- rate limit은 DB 기반 attempt count로 최소 구현 가능하면 포함하고, Redis 기반 분산 제한은 후속으로 분리한다.

### Out of Scope

- 로그인 플로우 전체 MFA challenge 재설계
- OAuth provider 계정 복구
- SMS/전화번호 인증
- WebAuthn/passkey
- 프론트 화면 수정
- notification 내부 service 직접 주입
- `TASK_platform.md` 수정
- env/profile/gitops/shared 수정

## API Contract

### 비밀번호 재설정 코드 요청

`POST /api/v1/auth/password-reset/request`

요청 후보:

```json
{
  "email": "user@example.com"
}
```

응답 후보:

```json
{
  "accepted": true
}
```

정책:

- 인증 불필요
- 계정이 없어도 동일 응답
- 계정이 `active`가 아니면 code 생성/발송하지 않되 동일 응답
- email은 trim + lower-case 정규화
- code 원문은 저장하지 않고 `PasswordEncoder` 결과만 저장
- code 만료 후보: 10분

### 비밀번호 재설정 코드 검증

`POST /api/v1/auth/password-reset/verify`

요청 후보:

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

응답 후보:

```json
{
  "resetToken": "one-time-reset-token",
  "expiresAt": "2026-06-10T10:20:00+09:00"
}
```

정책:

- code가 맞으면 reset token 원문을 1회 반환한다.
- reset token 원문은 DB에 저장하지 않고 hash만 저장한다.
- 검증 실패 횟수 제한 후보: 5회
- 검증 성공 후 code는 `verified` 상태로 전환한다.

### 비밀번호 재설정 확정

`POST /api/v1/auth/password-reset/confirm`

요청 후보:

```json
{
  "resetToken": "one-time-reset-token",
  "newPassword": "Newpass1!"
}
```

응답:

- `204 No Content`

정책:

- reset token 만료/사용 여부 확인
- 새 비밀번호 정책은 기존 email/password 요청 정책과 맞춘다.
- 확정 후 user password hash 업데이트
- 확정 후 reset token used 처리
- 확정 후 `UserSessionsRevocationRequested` 발행으로 refresh/session 무효화

### MFA 백업 코드 발급

`POST /api/v1/auth/mfa/backup-codes`

응답 후보:

```json
{
  "codes": ["ABCD-EFGH", "IJKL-MNOP"]
}
```

정책:

- 인증 필요
- TOTP credential이 active인 사용자만 가능
- 재발급 시 기존 미사용 백업 코드는 폐기 또는 used 처리
- 원문 코드는 응답 시 1회만 노출하고 DB에는 `PasswordEncoder` hash만 저장
- TOTP secret 재설정 시 기존 미사용 백업 코드는 used 처리

### MFA 백업 코드 검증

`POST /api/v1/auth/mfa/backup`

요청 후보:

```json
{
  "code": "ABCD-EFGH"
}
```

응답 후보:

```json
{
  "verified": true
}
```

정책:

- 인증 필요
- 미사용 백업 코드만 허용
- 성공 시 해당 code를 즉시 used 처리
- 실패 시 `MfaVerificationException`과 동일 계열 오류

## Data Model

추가 후보 1: `password_reset_requests`

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL`
- `email VARCHAR(255) NOT NULL`
- `code_hash VARCHAR(255) NOT NULL`
- `reset_token_hash VARCHAR(64)`
- `status VARCHAR(20) NOT NULL`
- `attempts INT NOT NULL DEFAULT 0`
- `expires_at TIMESTAMPTZ NOT NULL`
- `verified_at TIMESTAMPTZ`
- `used_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

추가 후보 2: `mfa_backup_codes`

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL`
- `code_hash VARCHAR(255) NOT NULL`
- `used_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ NOT NULL`

인덱스 후보:

- `idx_password_reset_requests_email_status`
- `idx_password_reset_requests_token_hash`
- `idx_mfa_backup_codes_user_id`
- `uq_mfa_backup_codes_code_hash`

## Design Notes

- 비밀번호 재설정은 `auth` 모듈 책임으로 둔다.
- 실제 password hash 변경은 user aggregate 소유이므로 `UserApi` 공개 계약 확장 또는 user service boundary를 통해 처리한다.
- 기존 `UserService.changeMyPassword(...)`는 로그인 사용자의 현재 비밀번호 검증용이므로 reset confirm에 그대로 쓰지 않는다.
- reset request는 계정 enumeration 방지를 위해 항상 동일 응답을 반환한다.
- MFA backup code는 `mfa_credentials`와 같은 auth 영역에 둔다.
- email 발송은 notification 내부 service를 직접 주입하지 않는다. 이번 구현은 auth에서 `NotificationSend` Kafka 이벤트를 발행하고, notification consumer/SES 설정이 켜진 환경에서 실제 email 발송을 수행한다.
- `PASSWORD_RESET_CODE` email은 일반 알림 일일 발송 quota와 분리해 reset code 발송이 조용히 skip되지 않고 일반 알림 quota도 소모하지 않게 한다.

## Implementation Checklist

- [x] 기존 PLAT-069 current 문서 archive 완료
- [x] PLAT-070 작업 브랜치 생성
- [x] PLAT-070 작업문서 작성
- [x] 비밀번호 재설정 migration 추가
- [x] MFA 백업 코드 migration 추가
- [x] 비밀번호 재설정 entity/repository 추가
- [x] MFA 백업 코드 entity/repository 추가
- [x] password reset request/response DTO 추가
- [x] MFA backup request/response DTO 추가
- [x] `AuthController` password reset endpoint 추가
- [x] `MfaController` backup endpoint 추가
- [x] password reset service 추가
- [x] MFA backup service 추가 또는 `TotpService` 보강
- [x] `UserApi` password reset용 계약 보강
- [x] 계정 존재 비노출 테스트 추가
- [x] code/token hash/만료/1회 사용 테스트 추가
- [x] backup code 1회 사용 테스트 추가
- [x] password reset email quota bypass 테스트 추가
- [x] security integration test 추가
- [x] `PlatformModuleStructureTest` 통과
- [x] `clean build` 통과

## Implementation Result

- `V20260610123000__create_auth_recovery_tables.sql`
  - `password_reset_requests`
  - `mfa_backup_codes`
- `AuthController`
  - `POST /api/v1/auth/password-reset/request`
  - `POST /api/v1/auth/password-reset/verify`
  - `POST /api/v1/auth/password-reset/confirm`
- `MfaController`
  - `POST /api/v1/auth/mfa/backup-codes`
  - `POST /api/v1/auth/mfa/backup`
- `PasswordResetService`
  - active password-login 사용자만 reset request 저장
  - request 응답 account enumeration 방지
  - code TTL 10분
  - reset token TTL 15분
  - 실패 시도 5회 제한
  - reset code는 `PasswordEncoder` 결과 저장
  - reset token은 SHA-256 hash 저장
  - 트랜잭션 커밋 후 reset code sender 호출
  - confirm 후 `UserApi.resetPassword(...)`
- `KafkaPasswordResetCodeSender`
  - `NotificationSend` EMAIL 이벤트 발행
  - notification 내부 `SesEmailService` 직접 주입 없음
  - `PASSWORD_RESET_CODE` email은 notification 일일 quota 예외
- `TotpService`
  - TOTP active 사용자만 backup code 발급
  - backup code 10개 발급
  - 기존 미사용 backup code 사용 처리 후 재발급
  - TOTP secret 재설정 시 기존 미사용 backup code 사용 처리
  - 재발급 시 `mfa_credentials` row pessimistic lock
  - backup code는 `PasswordEncoder` hash 저장
  - 성공 검증 시 즉시 `used_at` 처리
- `UserApi` / `UserService`
  - reset password 전용 boundary 추가
  - password hash 변경
  - failed login lock 해제
  - `UserSessionsRevocationRequested` 발행
- `SecurityConfig`
  - password reset 3개 endpoint permitAll
  - MFA backup endpoint는 인증 필요 유지

## Known Follow-up

- 실제 email 발송은 `synapse.kafka.enabled=true`와 SES 설정이 켜진 환경에서 동작한다.
- 운영/스테이징 배포 전 notification-send topic, notification consumer, SES 설정을 함께 확인해야 한다.
- 현재 `POST /api/v1/auth/mfa/backup`은 인증 필요 endpoint다. 로그인 전 MFA challenge에서 backup code를 쓰려면 별도 challenge session 모델이 필요하다.

## Verification Plan

```powershell
.\gradlew.bat test --tests "*PasswordReset*"
.\gradlew.bat test --tests "*Mfa*"
.\gradlew.bat test --tests "*AuthControllerTest"
.\gradlew.bat test --tests "*PlatformModuleStructureTest"
.\gradlew.bat clean build
```

## Verification Result

- `.\gradlew.bat test --tests "*PasswordResetServiceTest" --tests "*TotpServiceTest" --tests "*AuthControllerTest" --tests "*MfaControllerTest" --tests "*UserServiceTest" --tests "*PasswordResetRequestRepositoryLockingTest" --tests "*MfaBackupCodeRepositoryLockingTest"`: PASS
- `.\gradlew.bat test --tests "*PasswordResetServiceTest" --tests "*KafkaPasswordResetCodeSenderTest" --tests "*TotpServiceTest" --tests "*MfaCredentialRepositoryLockingTest" --tests "*MfaBackupCodeRepositoryLockingTest" --tests "*PasswordResetRequestRepositoryLockingTest"`: PASS
- `.\gradlew.bat test --tests "*AuthRecoverySecurityIntegrationTest"`: PASS
- `.\gradlew.bat test --tests "*PlatformModuleStructureTest"`: PASS
- `.\gradlew.bat clean build`: PASS
- Windows Kafka temp directory deletion warning appears during shutdown, but Gradle result is `BUILD SUCCESSFUL`.
