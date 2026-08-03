ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS user_id UUID NULL;

ALTER TABLE notifications
    MODIFY COLUMN customer_id UUID NULL;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_notifications_user
    ON notifications (tenant_id, user_id, read_at);
