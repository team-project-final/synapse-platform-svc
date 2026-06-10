CREATE TABLE password_reset_requests (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email              VARCHAR(255) NOT NULL,
    code_hash          VARCHAR(255) NOT NULL,
    reset_token_hash   VARCHAR(64),
    status             VARCHAR(20)  NOT NULL DEFAULT 'pending'
                                      CHECK (status IN ('pending', 'verified', 'used', 'expired')),
    attempts           INT          NOT NULL DEFAULT 0,
    expires_at         TIMESTAMPTZ  NOT NULL,
    verified_at        TIMESTAMPTZ,
    used_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_requests_email_status
    ON password_reset_requests(email, status, created_at DESC);

CREATE UNIQUE INDEX uq_password_reset_requests_reset_token_hash
    ON password_reset_requests(reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;

CREATE TRIGGER trg_password_reset_requests_updated_at
    BEFORE UPDATE ON password_reset_requests FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE mfa_backup_codes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(255) NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_mfa_backup_codes_user_code_hash
    ON mfa_backup_codes(user_id, code_hash);

CREATE INDEX idx_mfa_backup_codes_user_unused
    ON mfa_backup_codes(user_id)
    WHERE used_at IS NULL;
