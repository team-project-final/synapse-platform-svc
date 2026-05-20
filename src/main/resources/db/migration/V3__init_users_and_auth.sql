CREATE TABLE users (
    id                  UUID         PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    username            VARCHAR(50)  NOT NULL,
    password_hash       VARCHAR(255),
    display_name        VARCHAR(100),
    avatar_url          VARCHAR(500),
    email_verified_at   TIMESTAMPTZ,
    mfa_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    failed_login_count  INTEGER      NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    default_tenant_id   UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    anonymized_at       TIMESTAMPTZ,
    CONSTRAINT fk_users_default_tenant FOREIGN KEY (default_tenant_id) REFERENCES tenants(id)
);

CREATE UNIQUE INDEX uq_users_email    ON users(email)    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_username ON users(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_locked         ON users(locked_until) WHERE locked_until IS NOT NULL;

CREATE TABLE oauth_identities (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL,
    provider         VARCHAR(50)  NOT NULL,
    provider_id      VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    access_token_enc TEXT,
    metadata         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_oauth_identities_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_oauth_provider_user ON oauth_identities(provider, provider_id);
CREATE INDEX idx_oauth_user_id             ON oauth_identities(user_id);

CREATE TABLE user_settings (
    user_id               UUID        PRIMARY KEY,
    locale                VARCHAR(10) NOT NULL DEFAULT 'ko-KR',
    theme                 VARCHAR(20) NOT NULL DEFAULT 'system',
    srs_config            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    editor_config         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    notification_prefs    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    pii_redaction_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    marketing_opt_in      BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE tenant_members
    ADD CONSTRAINT fk_tenant_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
