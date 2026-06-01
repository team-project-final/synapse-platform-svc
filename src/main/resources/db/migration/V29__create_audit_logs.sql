CREATE TABLE audit_logs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID         NOT NULL,
    action        VARCHAR(100) NOT NULL,
    user_id       UUID,
    resource_type VARCHAR(50),
    resource_id   VARCHAR(200),
    old_value     JSONB,
    new_value     JSONB,
    ip_address    VARCHAR(45),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_audit_logs_event_id ON audit_logs (event_id);
CREATE INDEX idx_audit_logs_action     ON audit_logs (action);
CREATE INDEX idx_audit_logs_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
