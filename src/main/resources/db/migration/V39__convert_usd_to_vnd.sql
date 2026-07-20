-- V39: Convert all existing USD data to VND using a 250 multiplier
-- Since USD minor units are cents, and VND minor units are 1 VND, 
-- 1 USD = 25,000 VND -> 100 minor USD = 25,000 minor VND -> Multiplier = 250.

-- 1. Create a temporary table mapping old USD wallets to their corresponding VND wallets (or themselves if no VND wallet exists)
DROP TRIGGER IF EXISTS trg_wallet_txn_no_update;
DROP TRIGGER IF EXISTS trg_wallet_txn_no_delete;

CREATE TEMPORARY TABLE wallet_merges (
    usd_wallet_id UUID,
    target_wallet_id UUID,
    converted_available_minor BIGINT,
    converted_pending_minor BIGINT
);

INSERT INTO wallet_merges
SELECT 
    u.id AS usd_wallet_id,
    COALESCE(v.id, u.id) AS target_wallet_id,
    u.available_balance_minor * 250,
    u.pending_balance_minor * 250
FROM wallets u
LEFT JOIN wallets v 
    ON v.tenant_id = u.tenant_id 
    AND v.owner_type = u.owner_type 
    AND v.owner_id = u.owner_id 
    AND v.currency = 'VND'
WHERE u.currency = 'USD';

-- 2. Update wallet_transactions to point to the target wallet and scale amounts
UPDATE wallet_transactions wt
JOIN wallet_merges wm ON wt.wallet_id = wm.usd_wallet_id
SET 
    wt.wallet_id = wm.target_wallet_id,
    wt.amount_minor = wt.amount_minor * 250;

-- 3. Add converted balances to existing VND wallets
UPDATE wallets w
JOIN wallet_merges wm ON w.id = wm.target_wallet_id
SET 
    w.available_balance_minor = w.available_balance_minor + wm.converted_available_minor,
    w.pending_balance_minor = w.pending_balance_minor + wm.converted_pending_minor
WHERE wm.usd_wallet_id != wm.target_wallet_id;

-- 4. Update escrow_records to point to the target wallet and scale amounts
UPDATE escrow_records er
JOIN wallet_merges wm ON er.seller_wallet_id = wm.usd_wallet_id
SET 
    er.seller_wallet_id = wm.target_wallet_id,
    er.amount_minor = er.amount_minor * 250,
    er.currency = 'VND';

-- 5. Update ledger_checkpoints to point to the target wallet and scale amounts
UPDATE ledger_checkpoints lc
JOIN wallet_merges wm ON lc.wallet_id = wm.usd_wallet_id
SET 
    lc.wallet_id = wm.target_wallet_id,
    lc.available_balance_minor = lc.available_balance_minor * 250,
    lc.pending_balance_minor = lc.pending_balance_minor * 250;

-- 6. Update payout_requests to point to the target wallet and scale amounts
UPDATE payout_requests pr
JOIN wallet_merges wm ON pr.wallet_id = wm.usd_wallet_id
SET 
    pr.wallet_id = wm.target_wallet_id,
    pr.amount_minor = pr.amount_minor * 250,
    pr.currency = 'VND';

-- 7. Delete the USD wallets that were merged into existing VND wallets
DELETE FROM wallets
WHERE id IN (
    SELECT usd_wallet_id 
    FROM wallet_merges 
    WHERE usd_wallet_id != target_wallet_id
);

-- 8. Convert remaining USD wallets to VND
UPDATE wallets
SET 
    available_balance_minor = available_balance_minor * 250,
    pending_balance_minor = pending_balance_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

DROP TEMPORARY TABLE wallet_merges;

-- 9. Convert vouchers
UPDATE vouchers
SET 
    discount_value = CASE WHEN discount_type = 'FLAT' THEN discount_value * 250 ELSE discount_value END,
    min_order_value_minor = min_order_value_minor * 250,
    max_discount_amount_minor = CASE WHEN max_discount_amount_minor IS NOT NULL THEN max_discount_amount_minor * 250 ELSE NULL END,
    currency = 'VND'
WHERE currency = 'USD';

-- 10. Convert products
UPDATE products 
SET base_price_minor = base_price_minor * 250,
    sale_price_minor = sale_price_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

-- 11. Convert order items
UPDATE order_items 
SET unit_price_minor = unit_price_minor * 250,
    subtotal_amount_minor = subtotal_amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

-- 12. Convert orders
UPDATE orders 
SET subtotal_amount_minor = subtotal_amount_minor * 250,
    discount_amount_minor = discount_amount_minor * 250,
    shipping_fee_minor = shipping_fee_minor * 250,
    tax_amount_minor = tax_amount_minor * 250,
    vat_amount_minor = vat_amount_minor * 250,
    final_amount_minor = final_amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

-- 13. Convert payment intents
UPDATE payment_intents 
SET amount_minor = amount_minor * 250, 
    currency = 'VND' 
WHERE currency = 'USD';

-- 14. Re-enable triggers
CREATE TRIGGER trg_wallet_txn_no_update
BEFORE UPDATE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wallet_transactions is append-only; UPDATE is forbidden. Create an adjustment transaction instead.';
END;

CREATE TRIGGER trg_wallet_txn_no_delete
BEFORE DELETE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wallet_transactions is append-only; DELETE is forbidden. Create an adjustment transaction instead.';
END;
