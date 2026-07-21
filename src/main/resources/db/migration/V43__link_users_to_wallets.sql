ALTER TABLE wallets ADD COLUMN IF NOT EXISTS user_id UUID NULL;

UPDATE wallets w
JOIN business b ON w.owner_type = 'SELLER'
    AND w.owner_id = b.id
    AND w.tenant_id = b.tenant_id
JOIN users u ON b.created_by = u.id
    AND b.tenant_id = u.tenant_id
SET w.user_id = u.id
WHERE w.user_id IS NULL;

UPDATE wallets w
JOIN users u ON w.owner_type = 'USER'
    AND w.owner_id = u.id
    AND w.tenant_id = u.tenant_id
SET w.user_id = u.id
WHERE w.user_id IS NULL;

INSERT IGNORE INTO wallets
    (id, tenant_id, owner_type, owner_id, user_id, available_balance_minor,
     pending_balance_minor, currency, created_by, version)
SELECT UUID(), u.tenant_id, 'USER', u.id, u.id, 0, 0, 'VND', u.id, 0
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM wallets w
    WHERE w.tenant_id = u.tenant_id
      AND w.user_id = u.id
      AND w.currency = 'VND'
);

ALTER TABLE wallets
    ADD CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_wallets_user ON wallets (tenant_id, user_id, currency);
