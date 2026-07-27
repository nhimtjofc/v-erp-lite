-- Multiple checkout events can occur within the same second. Preserve their
-- insertion order for a deterministic order timeline.
ALTER TABLE order_history
    MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN sequence_no BIGINT NOT NULL AUTO_INCREMENT UNIQUE;
