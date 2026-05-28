CREATE TABLE outbox_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    topic           VARCHAR(200) NOT NULL,
    event_key       VARCHAR(200) NOT NULL,
    event_type      VARCHAR(200) NOT NULL,
    payload         BYTEA        NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_ready
    ON outbox_events (status, next_attempt_at, created_at);

CREATE INDEX idx_outbox_events_event_type
    ON outbox_events (event_type);
