CREATE TABLE subscriptions (
    id                     UUID         PRIMARY KEY,
    tenant_id              UUID         NOT NULL REFERENCES tenants(id),
    plan_code              VARCHAR(20)  NOT NULL,
    stripe_customer_id     VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    current_period_start   TIMESTAMPTZ,
    current_period_end     TIMESTAMPTZ,
    canceled_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_subscriptions_tenant_active
    ON subscriptions(tenant_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_subscriptions_stripe_sub_id
    ON subscriptions(stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;
CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id);
