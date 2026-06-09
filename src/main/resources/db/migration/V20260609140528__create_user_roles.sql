CREATE TABLE user_roles (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    role       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_USER'
FROM users
WHERE deleted_at IS NULL
ON CONFLICT (user_id, role) DO NOTHING;
