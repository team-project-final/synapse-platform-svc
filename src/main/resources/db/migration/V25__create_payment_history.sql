CREATE TABLE payment_history (
    id                       UUID         PRIMARY KEY,
    tenant_id                UUID         NOT NULL REFERENCES tenants(id),
    subscription_id          UUID         REFERENCES subscriptions(id),
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    amount                   INTEGER      NOT NULL,
    currency                 VARCHAR(3)   NOT NULL DEFAULT 'usd',
    status                   VARCHAR(20)  NOT NULL,
    paid_at                  TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_history_tenant_id       ON payment_history(tenant_id);
CREATE INDEX idx_payment_history_subscription_id ON payment_history(subscription_id);
