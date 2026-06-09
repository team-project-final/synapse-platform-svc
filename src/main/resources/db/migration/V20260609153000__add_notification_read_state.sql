ALTER TABLE notifications
    ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX idx_notifications_inbox_user_created_at
    ON notifications (user_id, channel, status, created_at DESC);

CREATE INDEX idx_notifications_inbox_unread
    ON notifications (user_id, channel, status, read_at);
