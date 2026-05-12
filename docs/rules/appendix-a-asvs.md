# 부록 A — ASVS 4.0 매핑

> OWASP ASVS (Application Security Verification Standard) 4.0 중에서 Synapse 프로젝트에 해당하는 항목만 골라서 매핑했어.
> 전체 ASVS를 다 커버하는 건 아니고, 우리 서비스 특성에 맞는 항목만 추렸으니까 참고해.

---

## V2 — Authentication

JWT, OAuth, MFA 등 Synapse 인증 체계에 해당하는 항목이야.

| ASVS 항목 | 설명 | Synapse Rule 참조 | 상태 |
|---|---|---|---|
| V2.1.1 | 비밀번호 최소 길이 8자 이상 강제 | 06-auth-token.md §6.1 | 적용 |
| V2.1.5 | 비밀번호 변경 시 현재 비밀번호 확인 | 06-auth-token.md §6.1 | 적용 |
| V2.2.1 | 안티 자동화 — 로그인 brute-force 방지 | 06-auth-token.md §6.1 | 적용 |
| V2.4.1 | 비밀번호 저장 시 bcrypt/scrypt/argon2 사용 | 06-auth-token.md §6.1 | 적용 |
| V2.5.2 | 비밀번호 복구 시 현재 비밀번호 노출 금지 | 06-auth-token.md §6.2 | 적용 |
| V2.7.1 | OTP/TOTP 사용 시 시간 기반 검증 | 06-auth-token.md §6.3 | 적용 |
| V2.8.1 | OAuth2 인가 코드 사용 시 PKCE 적용 | 06-auth-token.md §6.2 | 적용 |
| V2.8.3 | OAuth2 redirect_uri 화이트리스트 검증 | 06-auth-token.md §6.2 | 적용 |

---

## V3 — Session Management

Redis 블랙리스트 기반 세션 관리에 해당하는 항목이야.

| ASVS 항목 | 설명 | Synapse Rule 참조 | 상태 |
|---|---|---|---|
| V3.1.1 | 세션 토큰 URL 파라미터로 전송 금지 | 06-auth-token.md §6.4 | 적용 |
| V3.2.1 | 로그아웃 시 세션 즉시 무효화 | 06-auth-token.md §6.4 | 적용 |
| V3.2.3 | 비밀번호 변경 시 기존 세션 전부 무효화 | 06-auth-token.md §6.4 | 적용 |
| V3.3.1 | 세션 타임아웃 설정 (Access 15분, Refresh 7일) | 06-auth-token.md §6.4 | 적용 |
| V3.5.1 | JWT 토큰에 kid 헤더 포함, RS256 서명 | 06-auth-token.md §6.1 | 적용 |
| V3.5.3 | 토큰 블랙리스트로 즉시 폐기 가능 | 06-auth-token.md §6.4 | 적용 |

---

## V4 — Access Control

IDOR 방지, CORS 화이트리스트 등 접근 제어 항목이야.

| ASVS 항목 | 설명 | Synapse Rule 참조 | 상태 |
|---|---|---|---|
| V4.1.1 | 모든 API에 접근 제어 적용 (인증 필수) | 01-security.md §1.2 | 적용 |
| V4.1.2 | 역할 기반 접근 제어 (RBAC) 적용 | 01-security.md §1.2 | 적용 |
| V4.1.3 | IDOR 방지 — 리소스 소유자 검증 | 01-security.md §1.2 | 적용 |
| V4.2.1 | 민감 데이터 접근 시 추가 인증 요구 | 01-security.md §1.2 | 계획 |
| V4.3.1 | CORS 화이트리스트 방식 적용 | 01-security.md §1.3 (CORS) | 적용 |

---

## V5 — Validation, Sanitization and Encoding

입력 검증, XSS 방지, SQL Injection 방지 관련 항목이야.

| ASVS 항목 | 설명 | Synapse Rule 참조 | 상태 |
|---|---|---|---|
| V5.1.3 | 모든 입력 서버 사이드 검증 | 01-security.md §1.4 | 적용 |
| V5.2.1 | HTML 입력 sanitize (OWASP HTML Sanitizer) | 01-security.md §1.4 | 적용 |
| V5.2.4 | 마크다운 XSS 필터링 (`<script>`, `onerror` 등) | 01-security.md §1.4 | 적용 |
| V5.3.4 | SQL Injection 방지 — JPA 파라미터 바인딩 필수 | 01-security.md §1.4 | 적용 |
| V5.3.10 | Avro 스키마 레지스트리로 메시지 구조 검증 | 01-security.md §1.4 | 적용 |

---

## V8 — Data Protection

데이터 암호화, 개인정보 보호 관련 항목이야.

| ASVS 항목 | 설명 | Synapse Rule 참조 | 상태 |
|---|---|---|---|
| V8.1.1 | 민감 데이터 저장 시 AES-256 암호화 | 11-data-sovereignty.md §11.1 | 계획 |
| V8.1.2 | 비밀번호 bcrypt 해싱 (cost factor 12+) | 11-data-sovereignty.md §11.1 | 계획 |
| V8.2.1 | 전송 구간 TLS 1.2+ 강제 | 11-data-sovereignty.md §11.2 | 계획 |
| V8.3.1 | 로그에 개인정보 마스킹 (이메일, 전화번호) | 11-data-sovereignty.md §11.3 | 계획 |
| V8.3.4 | 디버그 로그에 민감 데이터 포함 금지 | 11-data-sovereignty.md §11.3 | 계획 |

---

## V13 — API and Web Service

REST API 설계 및 보안 관련 항목이야.

| ASVS 항목 | 설명 | Synapse Rule 참조 | 상태 |
|---|---|---|---|
| V13.1.1 | 모든 API 엔드포인트 인증 적용 | 02-function.md §2.1 | 적용 |
| V13.1.3 | API 응답에 불필요한 내부 정보 포함 금지 | 02-function.md §2.3 | 적용 |
| V13.2.1 | RESTful 설계 원칙 준수 (HTTP Method 활용) | 02-function.md §2.1 | 적용 |
| V13.2.2 | 에러 응답 RFC 7807 형식 준수 | 02-function.md §2.3 | 적용 |
| V13.2.5 | Content-Type 검증으로 MIME sniffing 방지 | 02-function.md §2.1 | 적용 |
| V13.3.1 | API Rate Limiting 적용 | 02-function.md §2.2 | 계획 |

---

## 상태 범례

| 상태 | 의미 |
|---|---|
| 적용 | Rule에 반영 완료, 구현 가이드 있음 |
| 계획 | Rule 문서 작성 예정 또는 구현 대기 |

---

_마지막 업데이트: 2026-05-12_
