UPDATE tenant_subscriptions
SET started_at = CURRENT_TIMESTAMP,
    current_period_ends_at = CASE
        WHEN billing_period = 'ANNUAL' THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 365 DAY)
        ELSE DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE'
  AND current_period_ends_at IS NULL;
