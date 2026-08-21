-- V55: Migrate pending balances for HELD escrow records from SELLER wallets to PLATFORM wallets.
-- The EscrowService logic was recently updated to hold escrow funds in the PLATFORM wallet
-- instead of the SELLER wallet. This script migrates the old data to match the new logic.

-- 1. Deduct pending balance from SELLER wallets
UPDATE wallets w
JOIN escrow_records e ON w.id = e.seller_wallet_id
SET w.pending_balance_minor = w.pending_balance_minor - e.amount_minor
WHERE e.status = 'HELD';

-- 2. Add pending balance to PLATFORM wallets
UPDATE wallets w
JOIN (
    SELECT tenant_id, currency, SUM(amount_minor) as total_held
    FROM escrow_records
    WHERE status = 'HELD'
    GROUP BY tenant_id, currency
) held_totals ON w.tenant_id = held_totals.tenant_id AND w.currency = held_totals.currency
SET w.pending_balance_minor = w.pending_balance_minor + held_totals.total_held
WHERE w.owner_type = 'PLATFORM';
