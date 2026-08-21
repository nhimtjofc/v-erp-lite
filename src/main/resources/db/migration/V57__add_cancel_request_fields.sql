ALTER TABLE order_items
  ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS cancel_requested_by UUID,
  ADD COLUMN IF NOT EXISTS cancel_status VARCHAR(20),
  ADD COLUMN IF NOT EXISTS cancel_approved_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS cancel_approved_by UUID;
