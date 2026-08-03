ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);

ALTER TABLE order_items
  ADD COLUMN IF NOT EXISTS return_requested_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS return_requested_by UUID,
  ADD COLUMN IF NOT EXISTS return_reason TEXT,
  ADD COLUMN IF NOT EXISTS return_approved_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS return_approved_by UUID,
  ADD COLUMN IF NOT EXISTS return_status VARCHAR(20),
  ADD COLUMN IF NOT EXISTS return_refund_minor BIGINT,
  ADD COLUMN IF NOT EXISTS return_refund_currency VARCHAR(3);