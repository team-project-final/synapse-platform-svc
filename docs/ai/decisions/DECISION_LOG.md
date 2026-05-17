# DECISION LOG

> append-only 문서입니다.
> 결정은 수정하지 않습니다. 번복 시 새 항목을 추가합니다.
> 형식: [D-{순번}] {날짜} — {제목}

---

## [D-005] 2026-05-15 — 모듈 구조 변경 (billing/audit 제거, user/admin 추가)

**결정**: `billing`, `audit` 모듈 제거. `user`, `admin` 모듈 추가. 최종 구조: `auth / user / notification / admin / shared`
**근거**: 팀리더가 제공한 최신 WORKFLOW_platform_W1.md 기준. billing 기능은 Step 4에서 auth 모듈 산하로 통합, audit 기능은 admin 모듈이 담당
**기각된 대안**: billing/audit 유지 — W2/W3 문서에 해당 모듈이 존재하지 않아 제거가 맞다고 판단
**결정자**: Director

---

## [D-006] 2026-05-15 — Refresh Token 저장 방식 변경 (Redis 전용 → DB + Redis 병행)

**결정**: `refresh_tokens` DB 테이블 신설 (token_hash, device_fingerprint, ip_address, expires_at). Redis는 조회 캐시 역할 유지. raw token은 DB/Redis 어디에도 저장 금지 — SHA-256 hash만 DB 저장
**근거**: 새 WORKFLOW §1.4/1.5 명세. device_fingerprint, ip_address 감사 요건 추가. WORKFLOW §1.8의 "Redis CRUD" 표기는 기존 문서 copy 오류로 판단
**기각된 대안**: Redis 전용 유지 — 감사 추적, 디바이스별 토큰 관리 불가; DB 전용 — 조회 성능 저하
**결정자**: Director

---

## [D-007] 2026-05-15 — OAuth access_token 암호화 저장 추가

**결정**: `oauth_identities.access_token_enc` 컬럼 추가. FieldEncryptor(AES-256-GCM) 사용. 기존 "최소 수집 원칙(저장 안 함)" 정책에서 "암호화 저장" 정책으로 변경
**근거**: 새 WORKFLOW §1.5 명세. 향후 OAuth API 재호출 시 토큰 재사용 가능성 대비
**기각된 대안**: 저장 안 함 유지 — 팀리더 명세 위반
**결정자**: Director

---

## [D-008] 2026-05-15 — MFA 테이블/엔티티 구조 변경 (totp_credentials → mfa_credentials)

**결정**: 테이블명 `totp_credentials` → `mfa_credentials`. 컬럼: `type VARCHAR(20) DEFAULT 'totp'`, `secret_enc TEXT` (IV 통합), `is_active BOOLEAN` (구 enabled), `verified_at TIMESTAMPTZ` (신규). `secret_iv` 컬럼 제거 (FieldEncryptor `{iv}:{cipher}` 포맷 유지)
**근거**: 새 WORKFLOW §1.4/1.6 명세. type 필드로 향후 SMS/recovery code 등 MFA 타입 확장 지원
**기각된 대안**: 테이블명 유지 + 컬럼만 수정 — 팀리더 명세와 불일치
**결정자**: Director

## [D-009] 2026-05-15 — Refresh Token 사용자당 1개 active 토큰 정책 확정

**결정**: `save()` 호출 시 `repository.deleteAllByUserId(userId)` 먼저 실행 후 신규 토큰 저장. 사용자당 항상 1개의 active Refresh Token만 유지. `device_fingerprint` / `ip_address`는 감사 목적 필드로만 사용 (멀티-디바이스 별도 토큰 불허)
**근거**: Worker 구현 리뷰에서 HIGH 발견 — save()가 deleteAll 없이 새 row를 추가하여 Redis flush/TTL 만료 후 오래된 DB 토큰이 재활성화될 수 있음. 단순성 + 보안 우선
**기각된 대안**: 디바이스별 다중 active 토큰 — 구현 복잡도 증가, 현재 요구사항에 없음
**결정자**: Director

---

## [D-010] 2026-05-15 — Refresh Token 단일 active 정책 DB 제약 및 Testcontainers 버전 보정

**결정**: `refresh_tokens(user_id)` unique index를 추가하여 사용자당 1개 active Refresh Token 정책을 DB 레벨에서도 강제한다. Docker Engine 29.3.1 환경과의 호환을 위해 Testcontainers를 1.21.4로 업데이트한다.
**근거**: 애플리케이션 레벨의 delete 후 insert만으로는 동시 로그인/동시 refresh 상황에서 중복 row가 생길 수 있다. 또한 Testcontainers 1.19.8은 Docker 29 환경에서 Docker API 탐지 실패가 발생했다.
**기각된 대안**: 애플리케이션 레벨 delete/insert만 유지 — 동시성 상황에서 단일 active 정책 보장 불충분. Testcontainers 1.19.8 유지 — Docker 29 환경에서 통합 테스트 실행 불가.
**결정자**: Director

---

<!-- 결정 발생 시 아래 템플릿 복사 후 추가 -->

<!--
## [D-NNN] YYYY-MM-DD — {결정 제목}

**결정**: {무엇을 결정했는가}
**근거**: {왜 이 결정을 했는가}
**기각된 대안**: {다른 선택지와 기각 이유}
**결정자**: Director
-->
