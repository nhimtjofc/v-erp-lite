UPDATE subscription_plans
SET features = JSON_OBJECT(
    'catalog', TRUE,
    'customers', TRUE,
    'orders', TRUE,
    'inventory', TRUE,
    'onlinePayments', TRUE
)
WHERE code = 'BASE';
