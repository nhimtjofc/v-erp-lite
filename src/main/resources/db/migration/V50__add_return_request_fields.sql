ALTER TABLE orders ADD COLUMN payment_method VARCHAR(20);

ALTER TABLE order_items
  ADD COLUMN return_requested_at TIMESTAMP NULL,
  ADD COLUMN return_requested_by UUID,
  ADD COLUMN return_reason TEXT,
  ADD COLUMN return_approved_at TIMESTAMP NULL,
  ADD COLUMN return_approved_by UUID,
  ADD COLUMN return_status VARCHAR(20),
  ADD COLUMN return_refund_minor BIGINT,
  ADD COLUMN return_refund_currency VARCHAR(3);