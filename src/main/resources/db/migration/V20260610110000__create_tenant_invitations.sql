CREATE TABLE tenant_invitations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('admin', 'member', 'viewer')),
    token_hash  VARCHAR(64)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending', 'accepted', 'expired', 'cancelled')),
    invited_by  UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenant_invitations_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_invitations_invited_by
        FOREIGN KEY (invited_by) REFERENCES users(id)
);

CREATE UNIQUE INDEX uq_tenant_invitations_token_hash
    ON tenant_invitations(token_hash);

CREATE UNIQUE INDEX uq_tenant_invitations_pending_email
    ON tenant_invitations(tenant_id, email)
    WHERE status = 'pending';

CREATE INDEX idx_tenant_invitations_tenant_status
    ON tenant_invitations(tenant_id, status);

CREATE INDEX idx_tenant_invitations_email
    ON tenant_invitations(email);

CREATE TRIGGER trg_tenant_invitations_updated_at
    BEFORE UPDATE ON tenant_invitations FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
