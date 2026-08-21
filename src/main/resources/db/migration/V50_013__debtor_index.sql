-- =====================================================================================
-- An index for the one account query that had none.
--
-- "Which counterparties owe us money" is a WHERE on balance_minor, and no index covered
-- it, so it was a full scan of ledger_accounts every time it was asked. Measured on a
-- seeded 500,000-account table that is around 60ms — survivable, and linear, and it is the
-- read that gets asked most urgently at exactly the moment the table is largest.
--
-- balance_minor last, after the two equality columns, so the query is a seek on
-- (tenant_code, account_role) followed by a range on the balance. Ordering it any other
-- way would leave the range predicate unable to use the columns after it.
-- =====================================================================================

CREATE INDEX idx_ledger_accounts_debtors
    ON ledger_accounts (tenant_code, account_role, balance_minor);

