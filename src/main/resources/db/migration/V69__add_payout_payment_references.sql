ALTER TABLE payout_requests
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(35) NULL;

CREATE UNIQUE INDEX uk_payout_requests_payment_reference
    ON payout_requests (payment_reference);
