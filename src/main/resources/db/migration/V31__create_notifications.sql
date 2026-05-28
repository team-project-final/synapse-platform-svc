CREATE TABLE notifications (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id          UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel           VARCHAR(20)  NOT NULL CHECK (channel IN ('FCM', 'EMAIL')),
    title             VARCHAR(500),
    body              TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts          INT          NOT NULL DEFAULT 0,
    error_message     TEXT,
    sent_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_notifications_user_id    ON notifications (user_id);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
CREATE INDEX idx_notifications_status     ON notifications (status);
