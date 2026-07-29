ALTER TABLE order_items
  ADD COLUMN cancel_requested_at TIMESTAMP NULL,
  ADD COLUMN cancel_requested_by UUID,
  ADD COLUMN cancel_status VARCHAR(20),
  ADD COLUMN cancel_approved_at TIMESTAMP NULL,
  ADD COLUMN cancel_approved_by UUID;
