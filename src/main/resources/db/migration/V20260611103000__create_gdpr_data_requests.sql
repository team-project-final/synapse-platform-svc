CREATE TABLE gdpr_data_requests (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID          NOT NULL,
    user_email             VARCHAR(255)  NOT NULL,
    user_display_name      VARCHAR(100),
    request_type           VARCHAR(40)   NOT NULL
        CHECK (request_type IN ('DATA_ACCESS', 'DATA_EXPORT', 'DATA_ERASURE')),
    status                 VARCHAR(40)   NOT NULL
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'REJECTED')),
    reason                 VARCHAR(500),
    admin_note             VARCHAR(1000),
    data_summary           TEXT,
    execution_log          TEXT,
    received_at            TIMESTAMPTZ   NOT NULL,
    due_at                 TIMESTAMPTZ   NOT NULL,
    processed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gdpr_data_requests_user_id ON gdpr_data_requests (user_id);
CREATE INDEX idx_gdpr_data_requests_status_received_at ON gdpr_data_requests (status, received_at DESC);
