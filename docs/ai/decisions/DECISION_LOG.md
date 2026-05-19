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

## [D-016] 2026-05-19 — JWT 검증 로직 platform-common 추출

**결정**: JWT 검증(파싱 + AuthenticatedUser 추출 + SecurityContext 설정)을 `platform-common/security/` 패키지로 추출한다. 구체적으로 `JwtTokenValidator`(일반 클래스), `JwtAuthenticationFilter`(OncePerRequestFilter), `AuthenticatedUser`(record)를 platform-common에 추가한다. JWT **발급/서명**(`JwtTokenProvider.create*Token()`)은 auth-service에 유지한다.
**근거**: billing-service(Step 4), notification-service(Step 7), audit-service(Step 6) 모두 JWT 검증이 필요하다. 서비스마다 독립 구현하면 코드 중복 + 검증 로직 불일치 위험. 아키텍처 문서 §2가 이미 `platform-common/auth/` 아래 `JwtUtils, RoleEnum`을 명시하고 있다.
**기각된 대안**: 서비스별 독립 구현 — 검증 로직 중복 발생, RS256 공개키 처리 각자 구현 필요 / auth-service에서 gRPC Introspect로 검증 위임 — D-015에서 Phase 2로 연기됨
**결정자**: Director

---

## [D-017] 2026-05-19 — D-013 번복: Spring Modulith 단일 앱으로 재전환

**결정**: D-013(Gradle 멀티모듈)을 번복하고 `ARCHITECTURE_v2.md` 기준 Spring Modulith 단일 Spring Boot 앱으로 재전환한다. 모듈 구조: `auth`, `user`, `notification`, `admin`, `shared`. billing은 v2에서 제외(알려진 갭).
**근거**: `ARCHITECTURE_v2.md`(v2.0, 2026-05-18)가 실제 레포 구조 기반으로 재작성됨. 루트 Dockerfile과 docker-compose.yml이 이미 단일 앱 기준으로 작성되어 있어 멀티모듈 구조가 실제로 완성되지 않은 상태였음. 단일 앱 유지 시 Steps 4~10의 모듈 간 통신을 Spring ApplicationEvent로 처리 가능 → 서비스 간 네트워크 호출 불필요.
**기각된 대안**: D-013 멀티모듈 유지 — Dockerfile/docker-compose가 단일 앱 기준이고 나머지 3개 서비스가 placeholder 상태이므로 멀티모듈 완성 비용이 더 큼
**결정자**: Director

---

## [D-018] 2026-05-19 — Spring Boot 4.0 유지 + Spring Modulith 호환 버전 확인

**결정**: Spring Boot 4.0.0은 유지한다. HANDOFF에 명시한 `spring-modulith-bom:1.3.0`은 Boot 3.4 기준이므로 제거. Worker가 공식 호환표(https://docs.spring.io/spring-modulith/reference/appendix.html)에서 Boot 4.0 호환 Modulith 버전을 확인 후 지정한다. 예상 버전: `2.0.x`.
**근거**: 프로젝트 전체가 Boot 4.0.0으로 이미 구현·테스트 완료. 다운그레이드 시 전체 코드 재검증 필요 → 비용 과대. 버전 미스매치로 빌드 실패가 발생하면 이슈가 즉시 드러나므로 Worker가 확인 후 적용하는 방식이 안전.
**기각된 대안**: Boot 3.4/3.5로 다운그레이드 — 전체 재검증 비용 과대
**결정자**: Director

---

## [D-019] 2026-05-19 — UserApi 설계: @NamedInterface + DTO 반환 + createForOAuth 추가

**결정**: 
1. `user/api/package-info.java`에 `@NamedInterface("api")` 추가 — auth 모듈이 `user.api` 패키지에 접근 가능
2. `UserApi` 반환 타입은 `User` 엔티티가 아닌 `UserInfo` record (same `user.api` 패키지) — `user.domain`이 `@NamedInterface` 없이도 경계 안전 유지
3. `UserApi` 메서드: `findById`, `findByEmail`, `createForOAuth(OAuthUserCreateCommand)` — `OAuthUserResolver.signUp()`이 User+UserSettings를 생성하는 책임을 user 모듈로 이전
4. `OAuthUserResolver`는 Tenant+OAuthIdentity+TenantMember 생성 책임만 유지, userApi.createForOAuth() 호출
**근거**: Spring Modulith 기본 규칙상 subpackage는 internal이므로 `user.api.UserApi`는 `@NamedInterface` 없으면 auth에서 접근 불가. `User` 엔티티를 외부에 노출하면 `user.domain`도 `@NamedInterface` 필요 → 엔티티 노출은 나쁜 경계 설계. `OAuthUserResolver.signUp()`은 이미 UserRepository + UserSettingsRepository를 직접 사용하므로 user 모듈로 이동이 자연스럽다.
**기각된 대안**: `user.domain`에도 `@NamedInterface` 추가 — 도메인 엔티티를 외부 모듈에 직접 노출하는 설계 안티패턴
**결정자**: Director

---

## [D-020] 2026-05-19 — D-012 무효 처리: tenantId를 JWT 대신 UserApi로 조회

**결정**: D-012(tenantId를 JWT claim에 추가)를 무효 처리한다. billing 모듈은 `UserApi.findById(userId).defaultTenantId()`로 tenantId를 조회한다.
**근거**: D-017 이후 현재 아키텍처는 Spring Modulith 단일 앱. JwtTokenProvider는 auth 모듈 내부 클래스로 @NamedInterface 미설정 → billing이 직접 호출 불가. UserApi는 이미 @NamedInterface("api")로 공개되어 있고 defaultTenantId 필드도 포함. UserApi 호출 방식이 모듈 경계를 준수하는 유일한 방법.
**기각된 대안**: D-012 유지(JWT claim 추가) — auth 모듈 수정 범위 확대 + billing이 JWT 파싱에 의존하는 순환 구조 위험
**결정자**: Director

---

## [D-021] 2026-05-19 — billing 모듈 패키지 신규 생성 (D-017 갭 해소)

**결정**: `io.synapse.platform.billing` 패키지를 신규 생성한다. D-017 재전환 시 billing은 포함되지 않았으므로 이번 Step 4에서 처음 구현한다.
**근거**: ARCHITECTURE_v2.md에 billing이 언급되지 않은 것은 "알려진 갭"이고, TASK_platform.md Step 4가 billing 모듈 구현 목표. `/api/v1/billing/*` 엔드포인트 경로가 TASK에 명시되어 있음.
**기각된 대안**: auth 모듈 내 billing 기능 통합 — 모듈 경계 불명확, 단일 책임 원칙 위반
**결정자**: Director

---

## [D-022] 2026-05-19 — TenantApi @NamedInterface 신규 추가 (auth 모듈)

**결정**: `auth/api/TenantApi.java`를 @NamedInterface로 추가한다. billing이 tenant.plan을 업데이트할 때 이 인터페이스를 통한다. 구현체 `TenantService`는 auth 모듈 내부에서 TenantRepository를 감싼다.
**근거**: billing이 tenant.plan 업데이트를 위해 auth 모듈의 TenantRepository를 직접 주입하면 Spring Modulith 모듈 경계 위반. UserApi 패턴(@NamedInterface + DTO)을 동일하게 적용하는 것이 일관성 있는 설계.
**기각된 대안**: Spring ApplicationEvent 발행 — 비동기 처리 복잡도 증가, 트랜잭션 경계 관리 어려움
**결정자**: Director

---

## [D-023] 2026-05-19 — Stripe Webhook 멱등성 전략: payment_intent_id UNIQUE + DB 체크

**결정**: `payment_history.stripe_payment_intent_id`에 UNIQUE 제약을 걸고, 처리 전 `existsByStripePaymentIntentId()` 체크로 중복 이벤트를 early return 처리한다.
**근거**: Stripe는 네트워크 오류 시 동일 이벤트를 재전송. DB UNIQUE 제약은 애플리케이션 레이어 체크가 실패해도 중복 저장을 방지하는 최후 방어선. checkout.session.completed에는 payment_intent_id가 포함되어 있어 멱등 키로 사용 가능.
**기각된 대안**: Redis 기반 분산 락 — 인프라 의존성 증가, UNIQUE 제약으로 충분
**결정자**: Director

---

## [D-024] 2026-05-19 — payment_history 저장 시점: checkout.session.completed → invoice.paid 이동

**결정**:
1. `checkout.session.completed`: Subscription 생성/활성화 + tenantApi.activatePlan() 전담. payment_history 저장 없음.
2. `invoice.paid`: payment_history 저장 전담. 멱등 키는 `invoice.paymentIntent`.
3. DDL status 기본값과 partial unique index를 `'ACTIVE'`(UPPERCASE)로 통일 — Java `@Enumerated(EnumType.STRING)` enum 값과 일치.

**근거**: Stripe subscription mode에서 `checkout.session.completed`의 `payment_intent`는 null일 수 있음. 결제 완료의 공식 확인 지점은 `invoice.paid`. DDL lowercase vs Java enum UPPERCASE 불일치는 partial unique index 오동작을 유발.

**기각된 대안**: checkout.session.completed에서 payment_history 저장 — subscription mode에서 payment_intent 부재 시 저장 누락 위험

**결정자**: Director (Worker 리뷰 반영)

---

## [D-025] 2026-05-19 — 스파이크 결과 반영: SDK 32.1.0 + StripeClient Bean + processed_events 멱등성

**결정**:
1. Stripe SDK: `26.4.0` → `32.1.0` (Spring Boot 4 / Jakarta EE 11 호환 샘플링 검증 완료)
2. 초기화: `Stripe.apiKey` 정적 setter → `StripeClient.builder()` Spring Bean (v32.x 표준)
3. Session 생성: `Session.create()` 정적 → `stripeClient.checkout().sessions().create()`
4. Webhook 수신: `@RequestBody String` → `@RequestBody byte[]` + UTF-8 변환 (샘플링 검증 완료)
5. 멱등성: D-023의 `payment_history.stripe_payment_intent_id` UNIQUE 전략 → `processed_events` 테이블(V26) + `event.id` ON CONFLICT DO NOTHING으로 전체 이벤트 통합 처리

**근거**: docs/spike/billing/ 샘플링 결과. processed_events 방식이 모든 이벤트 타입에 일관 적용되고, payment_intent 없는 이벤트에도 안전.
**기각된 대안**: D-023 payment_intent_id UNIQUE 유지 — invoice.paid 외 이벤트에 적용 불가
**결정자**: Director (스파이크 결과 반영)

---

<!-- 결정 발생 시 아래 템플릿 복사 후 추가 -->

<!--
## [D-NNN] YYYY-MM-DD — {결정 제목}

**결정**: {무엇을 결정했는가}
**근거**: {왜 이 결정을 했는가}
**기각된 대안**: {다른 선택지와 기각 이유}
**결정자**: Director
-->
