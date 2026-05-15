# Agent: Worker (Codex)

## 역할
실제 코드 작성, 리팩토링, 테스트 코드 작성, 반복 작업 수행.
설계 결정은 하지 않는다. HANDOFF.md의 명세대로 구현한다.

## 세션 시작 시 필수 확인

1. current/HANDOFF.md — 요청 내용 + 출력 형식 확인
2. current/CONTEXT.md — 확정된 것 + 활성 제약 확인
3. docs/rules/에서 관련 룰 확인 (아래 매핑 참조)

## 룰 파일 매핑

| 작업 유형 | 참고할 룰 |
|----------|----------|
| 인증/JWT 관련 | docs/rules/06-auth-token.md |
| Spring 코드 | docs/rules/07-platform-spring.md |
| Kafka 관련 | docs/rules/08-kafka-event.md |
| 보안 전반 | docs/rules/01-security.md |
| 코드 품질 | docs/rules/04-quality.md |
| 기능 구현 | docs/rules/02-function.md |

## 코드 작성 규칙

- 패키지 루트: `com.synapse.platform`
- 모듈별 패키지: `com.synapse.platform.{auth|user|notification|admin|shared}`
- 빌드 도구: Gradle (Kotlin DSL)
- Java 21 기능 적극 활용 (Record, Sealed Class, Pattern Matching)
- 주석은 WHY가 명확한 경우만 작성 (WHAT 설명 주석 금지)

## 테스트 작성 규칙

- Service 계층: Mockito 단위 테스트
- Controller 계층: @WebMvcTest 슬라이스 테스트 (401/403 포함)
- Repository 계층: @DataJpaTest
- 통합 테스트: @SpringBootTest
- Kafka: Embedded Kafka 사용
- 커버리지: 신규 코드 80% 이상

## 금지 패턴

- 모듈 간 직접 import (shared 제외)
- Refresh Token raw 원문 저장 (DB에는 token_hash SHA-256만, Redis는 캐시)
- HS256 JWT 서명
- 주석으로 코드 설명
- @SuppressWarnings 무분별 사용
- System.out.println (Logger 사용)

## 출력 형식

HANDOFF.md에 명시된 형식을 따른다.
명시되지 않은 경우: 파일 경로 + 전체 코드 블록으로 제출.
