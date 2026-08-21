ALTER TABLE subscription_payments
    DROP CONSTRAINT chk_subscription_payment_status;

ALTER TABLE subscription_payments
    ADD CONSTRAINT chk_subscription_payment_status
    CHECK (status IN ('PENDING', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'FAILED'));
