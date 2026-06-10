# CONTEXT - PLAT-070: Auth 복구 플로우 보강

## 배경

루트 `docs/BACKEND_GAP_platform.md` A-2는 프론트 auth 화면이 기대하는 비밀번호 재설정과 MFA 백업 코드 API가 없다고 정리한다.

프론트 기대:

- `auth/password_reset_screen.dart`
  - 비밀번호 재설정 코드 발송
  - 코드 검증
  - 새 비밀번호 확정
- `auth/mfa_screen.dart`
  - MFA 백업 코드 검증

현재 platform-svc는 아래까지만 제공한다.

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/mfa/setup`
- `POST /api/v1/auth/mfa/verify`

## 현재 코드 상태

### AuthController

`AuthController` base path는 `/api/v1/auth`.

현재 endpoint:

- `POST /signup`
- `POST /login`
- `POST /refresh`

비밀번호 재설정 관련 endpoint는 없다.

### EmailPasswordAuthService

현재 책임:

- email/password signup
- email/password login
- 로그인 실패 횟수 기록
- 계정 잠금/정지 상태 검증
- JWT/refresh token 발급

비밀번호 재설정 code/token 저장, email 발송, password reset confirm은 없다.

### UserService

이미 구현된 것:

- `changeMyPassword(UUID userId, String currentPassword, String newPassword)`
- 현재 비밀번호 검증 후 password hash 변경
- 변경 후 `UserSessionsRevocationRequested` 발행

주의:

- 이 메서드는 로그인 사용자의 현재 비밀번호 변경용이다.
- 비밀번호 재설정 confirm은 현재 비밀번호 없이 reset token으로 처리해야 하므로 별도 user boundary가 필요하다.

### MfaController / TotpService

현재 endpoint:

- `POST /api/v1/auth/mfa/setup`
- `POST /api/v1/auth/mfa/verify`

현재 `TotpService`:

- TOTP secret 생성/암호화 저장
- TOTP code 검증
- 검증 성공 시 `MfaCredential.activate()`

백업 코드 발급/검증은 없다.

### MfaCredential

현재 테이블:

- `mfa_credentials`
  - `user_id`
  - `type`
  - `secret_enc`
  - `is_active`
  - `verified_at`

백업 코드는 별도 table이 적절하다.

## 설계 방향

### 비밀번호 재설정

3단계로 분리한다.

1. request
   - email 입력
   - 존재하는 active email이면 code 생성/저장/발송
   - 존재하지 않거나 inactive여도 같은 응답

2. verify
   - email + code 검증
   - 성공 시 reset token 원문을 1회 반환
   - reset token hash 저장

3. confirm
   - reset token + newPassword
   - token hash 조회
   - 만료/사용 여부 검증
   - password hash 변경
   - session/refresh 무효화

### Account Enumeration 방지

`request` endpoint는 계정 존재 여부를 노출하지 않는다.

응답은 항상 동일:

```json
{
  "accepted": true
}
```

로그에는 내부적으로 기록할 수 있으나 응답에는 드러내지 않는다.

### Token/Code 저장

원문 저장 금지:

- reset code hash 저장
- reset token hash 저장
- backup code `PasswordEncoder` hash 저장

원문 노출:

- reset code 원문은 notification-send Kafka 발행 경로에서만 사용
- reset token 원문은 verify 응답에서 1회 반환
- backup code 원문은 발급 응답에서 1회 반환

### Email 발송 경계

현재 notification 모듈에는 SES 발송 구현체가 있지만, auth에서 notification 내부 service를 직접 주입하면 모듈 경계가 흐려진다.

선택지:

- notification 공개 API/boundary를 추가한 뒤 email 발송 연결
- Kafka notification event로 발송

문서 기준 추천:

- 발송 boundary가 이미 공개되어 있지 않으면 직접 SES 주입은 하지 않는다.
- 이번 구현은 `NotificationSend` Kafka 이벤트를 발행하고, notification consumer/SES가 실제 email 발송을 처리한다.
- `PASSWORD_RESET_CODE` email은 일반 notification 일일 quota에서 제외해 reset code 발송이 quota로 skip되지 않고 일반 알림 quota도 소모하지 않게 한다.

### MFA 백업 코드

TOTP가 활성화된 사용자만 backup code를 발급한다.

정책:

- 인증 필요
- 발급/재발급 시 미사용 기존 code는 폐기 처리
- TOTP secret 재설정 시 미사용 기존 code는 폐기 처리
- code 원문은 응답 시 1회만 노출
- DB에는 `PasswordEncoder` hash만 저장
- 검증 성공 시 해당 code를 즉시 used 처리

## 구현 위치

후보:

- `com.synapse.platform.auth.controller.AuthController`
- `com.synapse.platform.auth.controller.MfaController`
- `com.synapse.platform.auth.service.PasswordResetService`
- `com.synapse.platform.auth.service.TotpService`
- `com.synapse.platform.auth.entity.PasswordResetRequest`
- `com.synapse.platform.auth.entity.MfaBackupCode`
- `com.synapse.platform.auth.repository.PasswordResetRequestRepository`
- `com.synapse.platform.auth.repository.MfaBackupCodeRepository`
- `com.synapse.platform.user.api.UserApi`
- `com.synapse.platform.user.service.UserService`

## 테스트 포인트

비밀번호 재설정:

- request는 존재하지 않는 email도 200/202 동일 응답
- inactive/deleted user는 code 생성하지 않되 동일 응답
- active user는 code hash 저장
- verify는 올바른 code만 reset token 반환
- verify 실패 횟수 초과 시 실패
- 만료 code는 실패
- confirm은 유효 reset token으로 password hash 변경
- confirm 후 reset token 재사용 실패
- confirm 후 session revocation event 발행

MFA 백업 코드:

- TOTP 미활성 사용자는 backup code 발급 실패
- TOTP 활성 사용자는 backup code 발급 가능
- DB에는 backup code hash만 저장
- backup code 검증 성공 시 used 처리
- 같은 backup code 재사용 실패
- 잘못된 backup code는 `MfaVerificationException`

Security:

- password reset request/verify/confirm은 인증 없이 접근 가능
- MFA backup code 발급/검증은 인증 필요
- `PlatformModuleStructureTest` 통과

## 구현 완료 상태

2026-06-10 기준 PLAT-070 구현은 완료됐다.

완료된 항목:

- password reset 3단계 API 추가
- `password_reset_requests` 테이블 추가
- reset code는 `PasswordEncoder` 결과 저장
- reset token은 SHA-256 hash 저장
- reset code notification-send Kafka 발행
- `PASSWORD_RESET_CODE` email은 notification 일일 quota 예외
- reset code 10분 만료
- reset token 15분 만료
- verify 실패 5회 제한
- request 단계 계정 존재 여부 비노출
- active password-login 사용자만 reset request 저장
- confirm 단계에서 `UserApi.resetPassword(...)` 호출
- reset password 후 failed login lock 해제
- reset password 후 `UserSessionsRevocationRequested` 발행
- MFA backup code 발급/검증 API 추가
- `mfa_backup_codes` 테이블 추가
- TOTP active 사용자만 backup code 발급
- backup code 10개 발급
- 기존 미사용 backup code는 재발급 시 used 처리
- TOTP secret 재설정 시 기존 미사용 backup code는 used 처리
- MFA backup code 재발급 시 `mfa_credentials` row pessimistic lock
- backup code는 `PasswordEncoder` hash 저장
- backup code 검증 성공 시 즉시 used 처리
- password reset endpoint 3개는 permitAll
- MFA backup endpoint는 인증 필요 유지

운영 확인 필요 항목:

- 실제 email 발송은 `synapse.kafka.enabled=true`와 SES 설정이 켜진 환경에서 동작한다.
- 배포 전 notification-send topic, notification consumer, SES 설정을 함께 확인해야 한다.

주의:

- 현재 `POST /api/v1/auth/mfa/backup`은 인증 필요 API다.
- 로그인 전 MFA challenge에서 backup code를 쓰려면 별도 challenge session 모델이 필요하다.

검증 결과:

- controller/service/repository/security 테스트 PASS
- `PlatformModuleStructureTest` PASS
- `clean build` PASS

## 주의 사항

- `TASK_platform.md`는 최초 개발 목록이므로 수정하지 않는다.
- env/profile은 수정하지 않는다.
- gitops/shared는 팀장님 관리 영역이므로 수정하지 않는다.
- notification 내부 구현체를 auth에서 직접 주입하지 않는다.
- 비밀번호 reset request 응답으로 계정 존재 여부를 노출하지 않는다.
- reset code/token/backup code 원문을 DB에 저장하지 않는다.
