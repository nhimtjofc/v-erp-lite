-- V29: Wire the demo seller end-to-end so the wallet dashboard works with the real API.
--
-- The seller dashboard resolves the logged-in businessOwner -> the business they
-- own (business.created_by) -> that business's SELLER wallet (GET /wallets/me).
-- The seeded data below has no such linkage, so this migration:
--   1. Makes owner@v-erp.com the owner (created_by) of a seeded store.
--   2. Seeds that store's SELLER wallet with a starting balance (VND).
--   3. Adds the matching ledger entry so balance + history stay consistent.

-- 1. Link the demo businessOwner to the "Cửa hàng Board Game" store.
UPDATE business
SET created_by = (SELECT id FROM users WHERE email = 'owner@v-erp.com' AND tenant_id = 'v-erp-app')
WHERE id = 'b1000000-0000-0000-0000-000000000001' AND tenant_id = 'v-erp-app';

-- 2. Seed the SELLER wallet for that store (VND has exponent 0, so 5,000,000 minor = 5,000,000 ₫).
INSERT IGNORE INTO wallets
    (id, tenant_id, owner_type, owner_id, available_balance_minor, pending_balance_minor, currency, version)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'v-erp-app', 'SELLER',
     'b1000000-0000-0000-0000-000000000001', 5000000, 0, 'VND', 0);

-- 3. Matching ledger entry (double-entry: the deposit that funded the wallet).
INSERT IGNORE INTO wallet_transactions
    (id, tenant_id, wallet_id, amount_minor, type, transaction_type, sequence_no, description, version)
VALUES
    ('c1000000-0000-0000-0000-000000000001', 'v-erp-app',
     'a1000000-0000-0000-0000-000000000001', 5000000, 'CREDIT', 'DEPOSIT', 1,
     'Nạp số dư khởi tạo (seed demo)', 0);
