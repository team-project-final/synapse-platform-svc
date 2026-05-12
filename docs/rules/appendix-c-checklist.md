# 부록 C — PR 셀프체크 리스트

> PR 올리기 전에 이 체크리스트를 훑어봐. **[MUST]** 규칙 위반이 있으면 머지 안 돼.
> 전부 체크할 필요는 없고, 해당하는 항목만 확인하면 돼. 근데 [MUST]는 예외 없어.

---

## Security (01)

- [ ] `.env` / `*.key` / `*.pem` 커밋 없음 (§1.1)
- [ ] GitHub PAT는 fine-grained만 사용 (§1.1)
- [ ] 시크릿 로테이션 주기 준수 — Grace Period 동시 허용 (§1.1.1)
- [ ] IDOR 체크 — `userId == currentUser` 리소스 소유자 검증 (§1.2)
- [ ] 401/403 응답 구분 — 미인증 vs 권한 없음 (§1.2)
- [ ] CORS 화이트리스트 방식 — `*` 금지 (§1.3)
- [ ] `localhost` CORS 허용은 dev 프로파일에서만 (§1.3)

---

## Function (02)

- [ ] URI kebab-case + `/api/v1/` prefix (§2.1)
- [ ] URI depth 3~4단계 이내 (§2.1)
- [ ] 리소스명 복수형 명사, 동사 사용 금지 (§2.1)
- [ ] 에러 응답 RFC 7807 형식 준수 (§2.3)
- [ ] 서비스별 에러 코드 prefix 사용 — PLAT/ENGM/KNOW/LRNG (§2.3)
- [ ] 사용자 데이터 Soft Delete — `deleted_at` 타임스탬프 (§2.4)
- [ ] 물리 삭제는 시스템 내부 데이터만 (§2.4)
- [ ] DIP/OCP — 모듈 간 인터페이스 의존만 허용 (§2.5)

---

## Technical (03)

- [ ] N+1 방지 — `fetchJoin()` 또는 `@EntityGraph` 사용 (§3.2)
- [ ] `findAll()` 후 loop에서 lazy 접근 패턴 없음 (§3.2)
- [ ] `@Transactional` 서비스 계층에서만 선언 (§3.3)
- [ ] 읽기 전용 메서드 `@Transactional(readOnly = true)` 적용 (§3.3)
- [ ] 트랜잭션 안에서 외부 API 호출 없음 (§3.3)
- [ ] AOP에 비즈니스 로직 없음 — cross-cutting만 (§3.4)
- [ ] Modulith `allowedDependencies` 설정 (§3.5)
- [ ] 순환 의존 없음 — ArchUnit 테스트 통과 (§3.5)
- [ ] 모듈 간 직접 import 금지 — 이벤트 또는 exposed interface 사용 (§3.5)

---

## Quality (04)

- [ ] 테스트 Given-When-Then 패턴 (§4.1)
- [ ] 테스트 네이밍 BDD 스타일 — `메서드명_상황_should기대결과` (§4.1)
- [ ] 정적 분석 warning 0건 — Checkstyle / SpotBugs / ruff / flutter analyze (§4.4)
- [ ] Unit / Integration / E2E 테스트 구분 작성 (§4.5)

---

## Operation (05)

- [ ] 배포 GitOps 단일 경로 — `kubectl apply` 수동 금지 (§5.1)
- [ ] 롤백 이전 이미지 태그 사용 — `latest` 태그 금지 (§5.2)
- [ ] Health Check liveness + readiness 두 가지 제공 (§5.3)

---

## Auth & Token (06)

- [ ] JWT RS256 서명 — HS256 금지 (§6.1)
- [ ] Access Token TTL 15분, Refresh Token TTL 7일 (§6.1)
- [ ] JWT 헤더에 `kid` 필드 포함 (§6.1)
- [ ] OAuth client-secret 환경 변수 주입 — 하드코딩 금지 (§6.2)
- [ ] 로그아웃 시 Redis 블랙리스트 즉시 등록 (§6.4)
- [ ] JWT 필터에서 블랙리스트 체크 (§6.4)

---

## Platform (07)

- [ ] 의존 방향 `shared <- 도메인 모듈` — 역방향 금지 (§7.0.1)
- [ ] 도메인 모듈 간 직접 참조 금지 — 이벤트로 연결 (§7.0.1)
- [ ] `application-{profile}.yml` 환경별 분리 — local/dev/staging/prod (§7.0.2)
- [ ] prod yml에 평문 비밀번호 금지 — 환경 변수 주입 (§7.0.2)
- [ ] 같은 레포 모듈 경계 준수 — `allowedDependencies` 명시 (§7.0.3)

---

## Event (08)

- [ ] Kafka 메시지 Avro 스키마 레지스트리 사용 (§8.4)
- [ ] 스키마 호환성 BACKWARD 준수 (§8.5)
- [ ] JSON 문자열 직접 전송 금지 — Avro 직렬화 필수 (§8.4)

---

## Observability (09)

- [ ] 구조화 로그 (JSON 형식) 사용 (§9.1)
- [ ] `traceId` 전 구간 전파 (§9.1)
- [ ] 로그에 개인정보 마스킹 — 이메일, 전화번호 등 (§9.1)

---

## Data Sovereignty (11)

- [ ] 민감 데이터 AES-256 암호화 저장 (§11.1)
- [ ] 비밀번호 bcrypt 해싱 (§11.1)
- [ ] 전송 구간 TLS 1.2+ 강제 (§11.2)
- [ ] 디버그 로그에 민감 데이터 포함 금지 (§11.3)

---

## Working Log (12)

- [ ] 커밋 메시지 Conventional Commits 형식 — `type(scope): subject` (§12.1)
- [ ] PR 본문 `## 변경 사항` + `## 테스트 결과` 필수 (§12.2)
- [ ] HISTORY.md 일일 갱신 — 한 일 / 이슈 / 내일 계획 (§12.3)

---

## Task Structure (14)

- [ ] Task 문서 필수 10필드 전부 작성 (§14.1)
- [ ] Step Goal 측정 가능한 문장 — `[주체]가 [대상]에 [행위]를 [결과]한다` (§14.2)
- [ ] Done When은 Step Goal 바로 다음에 배치 (§14.3)
- [ ] Scope — In Scope / Out of Scope 두 블록 고정 (§14.4)

---

> 이 체크리스트는 Synapse Rule 01~14 챕터의 [MUST] 항목을 기반으로 만들었어.
> 규칙 상세 내용은 각 챕터 원문을 참고해.

_마지막 업데이트: 2026-05-12_
