CREATE INDEX IF NOT EXISTS idx_payment_intents_tenant_order_created
    ON payment_intents (tenant_id, order_id, created_at);

-- Retry intents can be created within the same second. Preserve microseconds so
-- the order lookup can reliably return the newest payment attempt first.
ALTER TABLE payment_intents
    MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- Products created before the multi-vendor migration did not have an owner.
-- Assign the seeded demo catalog to the seeded default business so checkout can
-- allocate escrow instead of failing after a successful payment.
UPDATE products p
JOIN business b
  ON b.id = 'b1000000-0000-0000-0000-000000000001'
 AND b.tenant_id = 'v-erp-app'
SET p.business_id = b.id
WHERE p.tenant_id = 'v-erp-app'
  AND p.business_id IS NULL;

-- Older createBusiness calls stored the Keycloak subject in created_by while
-- seller ownership elsewhere uses the application User ID. Normalize records.
UPDATE business b
JOIN users u
  ON u.tenant_id = b.tenant_id
 AND u.keycloak_id = CAST(b.created_by AS CHAR(36))
SET b.created_by = u.id
WHERE b.created_by <> u.id;
