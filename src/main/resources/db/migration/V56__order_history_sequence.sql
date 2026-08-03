-- Multiple checkout events can occur within the same second. Preserve their
-- insertion order for a deterministic order timeline.
ALTER TABLE order_history
    MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE order_history
    ADD COLUMN IF NOT EXISTS sequence_no BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE KEY IF NOT EXISTS uq_order_history_sequence (sequence_no);
