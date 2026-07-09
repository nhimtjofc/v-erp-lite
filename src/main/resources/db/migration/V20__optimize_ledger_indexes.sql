-- Add indexes for faster ledger and checkpoint queries
CREATE INDEX IF NOT EXISTS idx_wallet_txns_tenant_wallet_seq ON wallet_transactions(tenant_id, wallet_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_wallet_txns_tenant_created ON wallet_transactions(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_checkpoints_tenant_wallet_seq ON ledger_checkpoints(tenant_id, wallet_id, last_sequence_no);

-- Triggers to enforce append-only nature of the wallet_transactions ledger
DELIMITER //

CREATE TRIGGER trg_wallet_txn_no_update
BEFORE UPDATE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wallet_transactions is append-only; UPDATE is forbidden. Create an adjustment transaction instead.';
END; //

CREATE TRIGGER trg_wallet_txn_no_delete
BEFORE DELETE ON wallet_transactions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wallet_transactions is append-only; DELETE is forbidden. Create an adjustment transaction instead.';
END; //

DELIMITER ;
