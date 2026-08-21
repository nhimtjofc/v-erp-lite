-- V51: Seed SELLER wallets with mock bank details for all demo businesses.
-- Businesses b2 and b3 had no SELLER wallet at all -> checkout BANK_TRANSFER threw 400.
-- Business b1 already has a wallet (seeded in V29) but may lack bank info.

-- 1. Create SELLER wallets for businesses that don't have one yet
INSERT IGNORE INTO wallets
    (id, tenant_id, owner_type, owner_id,
     available_balance_minor, pending_balance_minor, currency,
     bank_name, bank_account_number, bank_account_holder, bank_id, version)
SELECT
    UUID(),
    b.tenant_id,
    'SELLER',
    b.id,
    0, 0, 'VND',
    'Techcombank',
    '19034567890123',
    'CUA HANG DEMO',
    (SELECT id FROM banks WHERE code = 'TECHCOMBANK' LIMIT 1),
    0
FROM business b
WHERE b.tenant_id = 'v-erp-app'
  AND b.id != 'b1000000-0000-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM wallets w
      WHERE w.owner_id = b.id AND w.owner_type = 'SELLER' AND w.tenant_id = b.tenant_id
  );

-- 2. Fill bank info for b1's existing wallet if missing
UPDATE wallets
SET bank_name           = 'BIDV',
    bank_account_number = '0375670607',
    bank_account_holder = 'CUA HANG OWNER (DEMO)',
    bank_id             = (SELECT id FROM banks WHERE code = 'BIDV' LIMIT 1)
WHERE tenant_id   = 'v-erp-app'
  AND owner_type  = 'SELLER'
  AND owner_id    = 'b1000000-0000-0000-0000-000000000001'
  AND (bank_account_number IS NULL OR bank_account_number = '');
