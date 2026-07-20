ALTER TABLE wallets ADD COLUMN IF NOT EXISTS bank_id UUID NULL;

UPDATE wallets w
JOIN banks b ON UPPER(TRIM(w.bank_name)) IN (UPPER(b.code), UPPER(b.name))
SET w.bank_id = b.id
WHERE w.bank_id IS NULL;

ALTER TABLE wallets
    ADD CONSTRAINT fk_wallets_bank FOREIGN KEY (bank_id) REFERENCES banks(id);

CREATE INDEX IF NOT EXISTS idx_wallets_bank ON wallets (bank_id);
