ALTER TABLE payment_idempotency
ADD COLUMN request_fingerprint VARCHAR(255),
ADD COLUMN lease_expires_at TIMESTAMP;
