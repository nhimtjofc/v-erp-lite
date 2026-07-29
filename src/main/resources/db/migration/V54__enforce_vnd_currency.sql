-- Complete the legacy USD-to-VND conversion and make VND the only accepted currency.
-- V39 converted the main aggregates at 1 USD cent = 250 VND but did not cover
-- product variants, carts, payment transactions, refunds, or currency defaults.
-- Also catch any products/vouchers/orders inserted after V39 with the old USD default.

UPDATE products
SET base_price_minor = base_price_minor * 250,
    sale_price_minor = sale_price_minor * 250,
    cost_price_minor = cost_price_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE vouchers
SET discount_value = CASE WHEN discount_type = 'FLAT' THEN discount_value * 250 ELSE discount_value END,
    min_order_value_minor = min_order_value_minor * 250,
    max_discount_amount_minor = CASE WHEN max_discount_amount_minor IS NOT NULL THEN max_discount_amount_minor * 250 ELSE NULL END,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE orders
SET subtotal_amount_minor = subtotal_amount_minor * 250,
    discount_amount_minor = discount_amount_minor * 250,
    shipping_fee_minor = shipping_fee_minor * 250,
    tax_amount_minor = tax_amount_minor * 250,
    vat_amount_minor = vat_amount_minor * 250,
    final_amount_minor = final_amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE payment_intents
SET amount_minor = amount_minor * 250, 
    currency = 'VND' 
WHERE currency = 'USD';

UPDATE product_variants
SET price_minor = price_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE cart_items
SET unit_price_minor = unit_price_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE carts
SET subtotal_amount_minor = subtotal_amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE payment_transactions
SET amount_minor = amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE payment_refunds
SET amount_minor = amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE wallet_topups
SET amount_minor = amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE revenue_adjustments
SET amount_minor = amount_minor * 250,
    currency = 'VND'
WHERE currency = 'USD';

UPDATE order_items
SET return_refund_minor = return_refund_minor * 250,
    return_refund_currency = 'VND'
WHERE return_refund_currency = 'USD';

-- Rebuild lifetime spend from already-converted orders instead of guessing whether
-- an existing aggregate was recorded before or after the original conversion.
UPDATE customer c
SET total_spend_minor = COALESCE((
    SELECT SUM(o.final_amount_minor)
    FROM orders o
    WHERE o.customer_id = c.id
      AND o.tenant_id = c.tenant_id
      AND o.status = 'COMPLETED'
      AND o.deleted_at IS NULL
), 0);

UPDATE tenant_config
SET settings = JSON_SET(settings,
    '$.currency.code', 'VND',
    '$.currency.symbol', '₫',
    '$.currency.decimalPlaces', 0);

UPDATE user_settings
SET settings = JSON_SET(settings,
    '$.currency.code', 'VND',
    '$.currency.symbol', '₫',
    '$.currency.decimalPlaces', 0);

ALTER TABLE wallets
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_wallets_currency_vnd CHECK (currency = 'VND');
ALTER TABLE escrow_records
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_escrow_records_currency_vnd CHECK (currency = 'VND');
ALTER TABLE payment_intents
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_payment_intents_currency_vnd CHECK (currency = 'VND');
ALTER TABLE payment_transactions
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_payment_transactions_currency_vnd CHECK (currency = 'VND');
ALTER TABLE payout_requests
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_payout_requests_currency_vnd CHECK (currency = 'VND');
ALTER TABLE products
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_products_currency_vnd CHECK (currency = 'VND');
ALTER TABLE product_variants
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_product_variants_currency_vnd CHECK (currency = 'VND');
ALTER TABLE orders
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_orders_currency_vnd CHECK (currency = 'VND');
ALTER TABLE order_items
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_order_items_currency_vnd CHECK (currency = 'VND'),
    ADD CONSTRAINT chk_order_items_refund_currency_vnd
        CHECK (return_refund_currency IS NULL OR return_refund_currency = 'VND');
ALTER TABLE vouchers
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_vouchers_currency_vnd CHECK (currency = 'VND');
ALTER TABLE carts
    MODIFY currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_carts_currency_vnd CHECK (currency = 'VND');
ALTER TABLE cart_items
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_cart_items_currency_vnd CHECK (currency = 'VND');
ALTER TABLE payment_refunds
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_payment_refunds_currency_vnd CHECK (currency = 'VND');
ALTER TABLE wallet_topups
    MODIFY currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_wallet_topups_currency_vnd CHECK (currency = 'VND');
ALTER TABLE revenue_adjustments
    MODIFY currency CHAR(3) NOT NULL DEFAULT 'VND',
    ADD CONSTRAINT chk_revenue_adjustments_currency_vnd CHECK (currency = 'VND');