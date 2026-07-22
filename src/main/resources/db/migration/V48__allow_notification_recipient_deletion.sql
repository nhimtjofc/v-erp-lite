ALTER TABLE notifications
    DROP FOREIGN KEY fk_notifications_user;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
