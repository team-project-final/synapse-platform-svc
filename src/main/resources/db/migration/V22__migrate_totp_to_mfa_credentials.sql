DROP TABLE IF EXISTS totp_credentials;

CREATE TABLE mfa_credentials (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL DEFAULT 'totp',
    secret_enc  TEXT        NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mfa_credentials_user_id ON mfa_credentials(user_id);
