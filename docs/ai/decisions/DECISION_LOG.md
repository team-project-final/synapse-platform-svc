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

## [D-011] 2026-05-19 — billing 모듈 독립 유지 (D-005 보정)

**결정**: billing 모듈(`com.synapse.platform.billing`)을 독립 Spring Modulith 모듈로 유지한다. D-005의 "Step 4에서 auth 산하 통합" 메모는 잘못된 기록으로 무효 처리.
**근거**: WORKFLOW_platform_W2.md Step 4가 "billing 모듈" 기준으로 작성되어 있고, 2026-05-18 W2 재점검 시 billing/package-info.java(@ApplicationModule)가 실제로 재생성된 것이 HISTORY에 기록됨. TASK_platform.md Step 4 엔드포인트도 `/billing/*` 경로 사용.
**기각된 대안**: auth 모듈 산하 통합 — 모듈 경계 불명확, WORKFLOW 기준 불일치
**결정자**: Director

---

## [D-012] 2026-05-19 — tenantId를 JWT Access Token claim에 추가

**결정**: `JwtTokenProvider.createAccessToken()`에 `tenantId` 파라미터를 추가하고 JWT claim으로 포함한다. billing 모듈 Controller는 `authentication.getCredentials()`로 raw token을 획득 후 `JwtTokenProvider.getTenantId()`로 tenantId를 추출한다.
**근거**: 현재 JWT subject는 userId만 포함. billing 모듈이 tenantId를 얻으려면 auth 모듈 내 TenantMemberRepository를 직접 참조해야 하는데, 이는 Modulith 모듈 경계 위반 가능성. JWT claim 추가가 가장 단순하고 성능 부담 없는 해결책.
**기각된 대안**: TenantMemberRepository 직접 참조 — Modulith 경계 위반 위험 / shared 모듈 인터페이스 추출 — 현재 단계에서 과도한 추상화
**결정자**: Director

---

## [D-013] 2026-05-19 — Gradle 멀티모듈 아키텍처로 전면 마이그레이션

**결정**: Spring Modulith 단일 앱 구조를 해체하고, `platform-common` + 4개 독립 Spring Boot 서비스(`auth-service`, `billing-service`, `audit-service`, `notification-service`)로 전환한다. 패키지 루트도 `com.synapse.platform` → `io.synapse.platform`으로 변경한다.
**근거**: 팀 공식 아키텍처 문서(`docs/synapse-platform-svc_ARCHITECTURE.md` v1.0)가 Gradle 멀티모듈 독립 배포 구조로 정의되어 있음. 현재 Modulith 구조로 Steps 5~11을 계속 구현하면 나중 마이그레이션 비용이 급증.
**기각된 대안**: 현재 Modulith 구조 유지 후 나중에 마이그레이션 — Steps 5~11 완료 이후엔 포팅 범위가 지금의 4배 이상
**결정자**: Director

---

## [D-014] 2026-05-19 — feature/PLAT-007-stripe-billing 브랜치 폐기

**결정**: `feature/PLAT-007-stripe-billing`에 구현된 billing 코드를 dev에 머지하지 않고 폐기한다. Stripe billing은 멀티모듈 마이그레이션 완료 후 새 `billing-service` 구조에서 Step 5로 재구현한다.
**근거**: D-013 결정에 따라 프로젝트 구조가 전면 변경되므로 기존 billing 코드 포팅보다 새 구조에서 처음부터 구현하는 것이 기술 부채를 최소화함.
**기각된 대안**: 기존 코드 포팅 — 패키지 rename + 모듈 이동 + API 경로 변경이 동시에 필요해 오류 가능성 높음
**결정자**: Director

---

## [D-015] 2026-05-19 — gRPC 내부 통신 Phase 2로 연기

**결정**: 아키텍처 문서에 정의된 gRPC 내부 통신(`AuthService.Introspect`, `UserService.GetById` 등)은 이번 마이그레이션 범위에서 제외하고 Phase 2(W4 이후)로 연기한다. 현재 단계에서 서비스 간 직접 gRPC 호출이 없으므로 기능적 리스크 없음.
**근거**: gRPC proto 정의 + 서버/클라이언트 설정은 최소 3~4일 작업. 현재 각 서비스가 독립 동작하므로 마이그레이션 리스크 없이 연기 가능.
**기각된 대안**: 이번 마이그레이션에 gRPC 포함 — 일정 리스크 과대
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
