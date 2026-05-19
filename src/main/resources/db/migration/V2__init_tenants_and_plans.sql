CREATE TABLE plan_quotas (
    plan                            VARCHAR(50)    PRIMARY KEY,
    display_name                    VARCHAR(100)   NOT NULL,
    price_usd_monthly               NUMERIC(10,2)  NOT NULL DEFAULT 0,
    price_usd_yearly                NUMERIC(10,2),
    max_notes                       INTEGER,
    max_cards                       INTEGER,
    max_storage_bytes               BIGINT,
    max_ai_tokens_monthly           BIGINT,
    max_ai_card_generations_monthly INTEGER,
    max_users_per_tenant            INTEGER,
    features                        JSONB          NOT NULL DEFAULT '{}'::jsonb,
    is_active                       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

INSERT INTO plan_quotas VALUES
('free',       'Free',       0.00,  NULL,   1000,  500,   100000000,   100000,  10,   1,    '{"graphView":false,"semanticSearch":false}', true, NOW()),
('pro',        'Pro',        9.99,  95.88,  50000, 50000, 10000000000, 5000000, 500,  1,    '{"graphView":true,"semanticSearch":true}',  true, NOW()),
('team',       'Team',       19.99, 191.88, NULL,  NULL,  NULL,        20000000,2000, 50,   '{"graphView":true,"semanticSearch":true,"sharedDecks":true}', true, NOW()),
('enterprise', 'Enterprise', 0.00,  NULL,   NULL,  NULL,  NULL,        NULL,    NULL, NULL, '{"graphView":true,"semanticSearch":true,"sharedDecks":true,"ssoEnabled":true,"auditLog":true}', true, NOW());

CREATE TABLE tenants (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    plan        VARCHAR(50)  NOT NULL DEFAULT 'free',
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    tenant_type VARCHAR(20)  NOT NULL DEFAULT 'personal',
    region      VARCHAR(20)  NOT NULL DEFAULT 'ap-northeast-2',
    settings    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT fk_tenants_plan FOREIGN KEY (plan) REFERENCES plan_quotas(plan)
);

CREATE UNIQUE INDEX uq_tenants_slug ON tenants(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status     ON tenants(status) WHERE status != 'active';
CREATE INDEX idx_tenants_plan       ON tenants(plan);

CREATE TABLE tenant_members (
    tenant_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'member',
    invited_by UUID,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT fk_tenant_members_tenant     FOREIGN KEY (tenant_id)  REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_members_invited_by FOREIGN KEY (invited_by) REFERENCES tenants(id)
);

CREATE INDEX idx_tenant_members_tenant_role ON tenant_members(tenant_id, role);
