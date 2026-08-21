UPDATE subscription_plans
SET features = JSON_OBJECT(
    'catalog', TRUE,
    'customers', TRUE,
    'orders', TRUE,
    'inventory', TRUE
)
WHERE code = 'BASE';

UPDATE subscription_plans
SET features = JSON_OBJECT(
    'catalog', TRUE,
    'customers', TRUE,
    'orders', TRUE,
    'inventory', TRUE,
    'vouchers', TRUE,
    'reports', TRUE,
    'multiBusiness', TRUE,
    'teamCollaboration', TRUE
)
WHERE code = 'PLUS';

UPDATE subscription_plans
SET features = JSON_OBJECT(
    'catalog', TRUE,
    'customers', TRUE,
    'orders', TRUE,
    'inventory', TRUE,
    'vouchers', TRUE,
    'reports', TRUE,
    'multiBusiness', TRUE,
    'teamCollaboration', TRUE,
    'ecommerce', TRUE,
    'onlinePayments', TRUE,
    'walletPayouts', TRUE
)
WHERE code = 'PREMIUM';
