# TASK — Step 6: Kafka 이벤트 기반 Audit Log 자동 기록

> 출처: TASK_platform.md Step 6

## 상태

- Phase: 구현
- 담당 Agent: Worker (Codex)
- 시작일: 2026-05-28
- 목표 완료일: 2026-05-28

---

## Step Goal

시스템이 Kafka 이벤트를 소비하여 audit_logs에 자동 기록한다.

## Done When

- [ ] Kafka Consumer가 `platform.auth.user-registered-v1` 토픽 이벤트 수신
- [ ] Kafka 메시지는 Avro + Schema Registry 기준으로 발행/소비
- [ ] audit_logs 테이블에 이벤트 자동 저장
- [ ] 관리자가 `GET /api/v1/admin/audit-logs` API로 이력 확인 가능
- [ ] 이벤트 유실 없이 at-least-once 보장 (Producer outbox + event_id UNIQUE 멱등)
- [ ] 통합 테스트 통과 (Kafka + Schema Registry mock/Testcontainers 전략 포함)

## Scope

- In Scope:
  - `platform.auth.user-registered-v1` Producer outbox (OAuth 신규 가입, 이메일 신규 가입)
  - Avro schema 및 Schema Registry 연동 (`synapse-shared` schema PR 선행)
  - audit_logs 테이블 설계 + Flyway V29 마이그레이션
  - outbox_events 테이블 설계 + Flyway V30 마이그레이션
  - Kafka Consumer (audit-consumer-group, Avro 역직렬화)
  - 이벤트 → audit_logs 변환/저장 (멱등)
  - 90일 보존 스케줄러
  - Audit Log 조회 API (관리자 전용, 페이징 + 필터)
  - 통합 테스트
- Out of Scope:
  - billing.subscription-changed Producer/Consumer (별도 태스크)
  - 실시간 스트리밍 대시보드

## Input

- 현재 코드: OAuth2SuccessHandler, EmailPasswordAuthService, CustomOAuth2UserService, CustomOidcUserService, OAuthUserResolver
- Kafka 인프라: KafkaConsumerConfig, KafkaErrorHandlerConfig (DLQ 설정 완료)
- DB: V28까지 마이그레이션 완료
- `synapse-shared` Avro schema: `CloudEventEnvelope`, `UserRegistered` 필요
- 테스트 환경: H2 + ddl-auto:create-drop, Kafka + Schema Registry mock/Testcontainers 전략 필요

## Instructions

1. Flyway V29 — audit_logs 테이블 생성
2. OAuthResolvedUser record 생성 (auth/service/)
3. OAuthUserResolver.resolveUser() 반환 타입 변경
4. CustomOAuth2UserService, CustomOidcUserService — isNewUser/email/tenantId enrichedAttributes 추가
5. `synapse-shared` Avro schema 기준의 UserRegistered model 사용
6. UserEventPublisher 생성 (auth/event/) — Kafka 직접 발행이 아니라 outbox_events 저장
7. KafkaProducerConfig — Avro serializer 기반 eventKafkaTemplate Bean 추가
8. KafkaConsumerConfig — Avro deserializer 기반 auditConsumerFactory + auditKafkaListenerContainerFactory 추가
9. KafkaTopicProperties — userRegistered 토픽명 필드 추가
10. OAuthUserResolver — 신규 OAuth 가입 트랜잭션 안에서 outbox 이벤트 저장
11. OAuth2SuccessHandler — UserRegistered 직접 발행 제거, 토큰/리다이렉트만 담당
12. EmailPasswordAuthService — signup 트랜잭션 안에서 outbox 이벤트 저장
13. OutboxEventPublisher — PENDING outbox_events를 Kafka로 비동기 발행, 성공/실패 상태 기록
14. audit 모듈 구현 (package-info, AuditLog entity, repository, service, consumer, controller, dto)
15. PlatformApplication — @EnableScheduling 추가
16. SecurityConfig — @EnableMethodSecurity 추가
17. AuthRoles — ROLE_ADMIN 상수 추가
18. 기존 테스트 업데이트 (OAuthUserResolverTest, CustomOAuth2UserServiceTest, OAuth2SuccessHandlerTest)
19. AuditKafkaIntegrationTest 작성
20. signup → outbox → Kafka → audit_logs → outbox PUBLISHED 통합 테스트 작성

## Output Format

- audit 모듈 코드 + Kafka Consumer + Producer + 테스트 코드
- `./gradlew test` 전체 통과

## Constraints

- Kafka 메시지 포맷: Avro + Schema Registry MUST
- at-least-once 보장: outbox_events 재시도 + event_id UNIQUE 멱등
- audit_logs 보존: 90일 (@Scheduled 매일 실행)
- 조회 API: ROLE_ADMIN 필수
- 테스트 커버리지: 신규 코드 80% 이상

## Review Follow-up (2026-05-28)

- [x] PostgreSQL Flyway + JPA validate 검증 추가 및 `AuditLog` JSONB 매핑 보정
- [x] `EmailPasswordAuthService.signup()`의 `UserRegistered` 발행을 outbox 저장 방식으로 변경
- [x] `UserEventPublisher`는 outbox 저장만 담당하고 `OutboxEventPublisher`가 `KafkaTemplate.send(...)` 비동기 실패를 기록

## Revision Plan (2026-05-28)

- [x] Avro/Schema Registry 전환: JSON String 임시 예외 제거, producer/consumer serializer를 Avro 기준으로 변경
- [x] Outbox send 동기 예외 처리: `KafkaTemplate.send(...)` 호출 자체 실패도 attempts/last_error/next_attempt_at에 기록
- [x] Outbox 중복 발행 방지: PENDING row claim 후 PUBLISHING 상태 전이로 다중 인스턴스 중복 publish 방지
- [x] End-to-end 검증 보강: outbox publisher enabled 상태에서 outbox → Kafka → audit_logs → outbox PUBLISHED 검증
- [x] `OAuth2SuccessHandler` 정리: 미사용 `UserEventPublisher` 생성자 의존성과 테스트 mock 제거

## Verification (2026-05-28)

- [x] `./gradlew test`
- [x] `./gradlew test --tests "*ModuleStructureTest"`
- [x] `./gradlew check`
- 참고: `synapse-shared` 외부 artifact는 현재 workspace에 없어, Rule 8 schema는 `PlatformAvroEvents`의 단일 schema 정의로 구현했다. 추후 shared generated model이 제공되면 이 유틸을 치환한다.

## Review Fix Follow-up (2026-05-28)

- [x] 만료된 `PUBLISHING` outbox row를 `PENDING`으로 복구하는 lease 기반 재처리 경로 추가
- [x] `claimPending` atomic update가 `PUBLISHING` 상태와 lease 만료 시각을 함께 기록하도록 변경
- [x] Kafka consumer deserializer를 `ErrorHandlingDeserializer` + Avro delegate로 변경
- [x] DLT producer를 Avro `GenericRecord`용과 raw `byte[]`용으로 분리
- [x] `OutboxEventPublisherTest`, `OutboxEventRepositoryTest`, `KafkaConsumerConfigSmokeTest`로 회귀 검증 추가

## Duration

1일
