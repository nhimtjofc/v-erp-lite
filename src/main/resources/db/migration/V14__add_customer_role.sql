INSERT INTO role (id, name, description)
SELECT UUID(), 'customer', 'Customer - can browse products and place orders'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'customer');
