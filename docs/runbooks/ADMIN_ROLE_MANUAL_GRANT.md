# Admin Role Manual Grant

## 목적

platform-svc는 운영 환경에서 최초 어드민 계정을 자동 생성하지 않는다.
어드민 권한은 승인된 사용자가 정상 가입한 뒤, 승인된 DB 작업으로 `ROLE_ADMIN`을 추가한다.

## 전제

- 대상 사용자는 platform-svc에 이미 가입되어 있어야 한다.
- 운영에서는 dummy admin 계정을 만들지 않는다.
- 삭제된 사용자(`deleted_at IS NOT NULL`)에게는 role을 부여하지 않는다.
- role 부여 후 사용자는 다시 로그인해야 새 JWT access token에 `ROLE_ADMIN`이 포함된다.

## 대상 사용자 확인

```sql
SELECT id, email, status, deleted_at
FROM users
WHERE email = 'admin@example.com';
```

확인 기준:

- `status = 'active'`
- `deleted_at IS NULL`
- email이 승인된 실제 대상 계정과 일치

## ROLE_ADMIN 부여

```sql
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN'
FROM users
WHERE email = 'admin@example.com'
  AND deleted_at IS NULL
ON CONFLICT (user_id, role) DO NOTHING;
```

## 부여 결과 확인

```sql
SELECT u.email, ur.role
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
WHERE u.email = 'admin@example.com'
ORDER BY ur.role;
```

기대 결과:

- `ROLE_USER`
- `ROLE_ADMIN`

## 로컬 프론트 연동 테스트

로컬에서도 별도 seed/profile을 만들지 않는다.
프론트에서 사용할 테스트 계정을 일반 가입 경로로 만든 뒤 위 SQL로 `ROLE_ADMIN`을 부여한다.
