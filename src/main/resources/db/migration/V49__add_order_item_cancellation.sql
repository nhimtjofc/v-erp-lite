ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS cancelled_by UUID NULL,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
