-- =====================================================================================
-- Renames two account codes that carried marketplace vocabulary into the ledger core.
--
-- `revenue:commission` and `control:escrow` name what one particular caller was doing
-- with the money rather than what the account is. A commission is a fee; escrow is one
-- kind of hold among several. The ledger should not learn a word from its client, or the
-- next client inherits a vocabulary that does not describe it.
--
-- A plain UPDATE is correct and sufficient here. `code` is not `balance_minor` and not
-- `entry_seq`, so trg_ledger_accounts_guard permits it; no balance moves, no journal row
-- is touched, and history keeps saying what it said at the time — which is the point of
-- an append-only journal. Memo text on existing lines still reads "commission", and that
-- is not a defect to repair: it is what was written when it happened.
-- =====================================================================================

UPDATE ledger_accounts SET code = 'revenue:fee'         WHERE code = 'revenue:commission';
UPDATE ledger_accounts SET code = 'control:funds_held'  WHERE code = 'control:escrow';

