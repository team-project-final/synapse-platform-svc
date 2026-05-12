# 11. 데이터 주권 RULE — Data Sovereignty

> **참조**: [전체 Rule 목록](../rules/) | [준수 체크리스트](appendix-c-checklist.md)

---

## 11.1 개인정보 보호 \[MUST\] / \[SHOULD\]

개인정보는 저장 시 반드시 암호화해. 비밀번호는 **bcrypt** \[MUST\], 이메일/이름은 **AES-256** \[SHOULD\]야.

| 필드 | 암호화 방식 | 준수 레벨 | 비고 |
|---|---|---|---|
| 비밀번호 | bcrypt (cost 12) | \[MUST\] | 단방향, 복호화 불가 |
| 이메일 | AES-256-GCM | \[SHOULD\] | 검색 필요 시 해시 인덱스 병행 |
| 이름 | AES-256-GCM | \[SHOULD\] | 표시용 복호화 필요 |

```java
// ✅ Good — bcrypt로 비밀번호 해싱
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

```java
// ❌ Bad — 평문 저장 또는 MD5/SHA-1
user.setPassword(rawPassword);                    // 평문!
user.setPassword(DigestUtils.md5Hex(rawPassword)); // 레인보우 테이블 공격에 취약
```

### AES-256 암호화 예시

```java
// ✅ Good — AES-256-GCM으로 이메일 암호화
@Component
public class FieldEncryptor {
    private final SecretKey key; // 환경변수에서 로딩

    public String encrypt(String plainText) {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = SecureRandom.getInstanceStrong().generateSeed(12);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(UTF_8));
        return Base64.getEncoder().encodeToString(
            ByteBuffer.allocate(iv.length + encrypted.length)
                      .put(iv).put(encrypted).array());
    }
}
```

> **이유**: 비밀번호는 단방향 해싱이 업계 표준이야. bcrypt는 cost factor로 연산 시간을 조절해서 brute-force에 강해. AES-256은 복호화가 필요한 필드에 적합해.

---

## 11.2 데이터 보존 \[SHOULD\]

삭제 요청 시 즉시 물리 삭제하지 말고 **soft delete** 처리해. 90일 후에 **익명화** 처리해.

### 보존 정책

| 단계 | 기간 | 처리 |
|---|---|---|
| 활성 | — | 정상 접근 가능 |
| soft delete | 0 ~ 90일 | `deleted_at` 세팅, API 응답에서 제외 |
| 익명화 | 90일 이후 | 개인정보 필드 NULL 또는 해시 치환 |

```java
// ✅ Good — soft delete 엔티티
@Entity
@SQLDelete(sql = "UPDATE member SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Member {
    @Column
    private LocalDateTime deletedAt;
}
```

```java
// ❌ Bad — 즉시 물리 삭제
memberRepository.deleteById(memberId); // 복구 불가, 감사 추적 불가
```

> **이유**: 즉시 삭제하면 실수로 삭제했을 때 복구가 안 돼. 90일 보존 후 익명화하면 개인정보 보호법 준수와 데이터 분석 양쪽을 충족할 수 있어.

---

## 11.3 로그 마스킹 \[MUST\]

로그에 개인정보가 찍히면 안 돼. **자동 마스킹 패턴**을 logback에 등록해서 실수로 찍혀도 마스킹되게 해.

### 마스킹 대상 필드

| 필드 | 마스킹 결과 |
|---|---|
| 이메일 | `u***@example.com` |
| 전화번호 | `010-****-5678` |
| 비밀번호 | `********` |

### logback 마스킹 설정 예시

```xml
<!-- ✅ Good — logback에서 자동 마스킹 (MaskingConverter 사용) -->
<configuration>
  <conversionRule conversionWord="maskedMsg"
    converterClass="com.synapse.common.logging.MaskingConverter" />
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{ISO8601} [%thread] %-5level %logger - %maskedMsg%n</pattern>
    </encoder>
  </appender>
</configuration>
```

`MaskingConverter`에서 이메일은 `u***@example.com`, 전화번호는 `010-****-5678` 형태로 정규식 치환해.

```java
// ❌ Bad — 개인정보 평문 로깅
log.info("회원가입 완료: email={}, phone={}", email, phone);
```

> **이유**: 로그는 개발자뿐 아니라 모니터링 시스템, 로그 수집기 등 여러 곳으로 흘러가. 한 번 찍힌 개인정보는 회수가 불가능하니까 출력 시점에서 마스킹해야 해.
