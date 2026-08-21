-- =====================================================================================
-- Two names that meant the opposite of what they said.
--
-- 1. CONTROL AND ROLLUP WERE INVERTED
--
-- In accounting a control account is the general-ledger account that summarises a
-- subsidiary ledger: accounts payable control carries the total of every supplier account
-- beneath it, and is never posted to directly — postings go to the subsidiary accounts and
-- the control follows. That is exactly what this schema called ROLLUP.
--
-- Meanwhile AccountRole.CONTROL was documented as "a position held externally, posted to
-- directly", and was used for bank accounts, provider positions and clearing accounts.
-- Those are ordinary general-ledger accounts. Nothing rolls up to them, and being posted
-- to directly is precisely what a control account is not.
--
-- So anyone who knew the term read the enum backwards, and the two properties it was
-- really carrying are recorded elsewhere anyway: "held somewhere external" is
-- AccountClass.ASSET plus external_ref, and "should trend to zero" is is_clearing.
--
--   ROLLUP  -> CONTROL   (summarises a subsidiary ledger; never posted to directly)
--   CONTROL -> GENERAL   (an ordinary account; the class already says what kind)
--
-- The order of the updates below matters: CONTROL must vacate the name before ROLLUP takes
-- it, or the second statement would catch the rows the first just moved.
--
-- 2. AN ASSET NAME ON A LIABILITY
--
-- control:ar:partners was opened as LIABILITY with a credit normal balance and rolls up
-- counterparty accounts. AR means accounts receivable, which is an asset. The name asserted
-- the opposite of the row's own classification column, on the one account whose entire job
-- is to total what we owe.
--
-- Renaming it to accounts payable would be equally wrong half the time — a counterparty who
-- has been clawed back genuinely owes us, and this ledger is built so that can be expressed.
-- The accurate name says what it controls and not which direction it happens to lean.
--
-- 3. THE control: PREFIX
--
-- Once CONTROL means what accounting means, a prefix claiming it on seven accounts that are
-- not control accounts is the same error in the data. Codes now name the thing:
-- asset: for positions we hold, clearing: for money in transit, liability: for what we owe,
-- control: for the one account that actually controls a subsidiary ledger.
-- =====================================================================================

ALTER TABLE ledger_accounts DROP CONSTRAINT ck_ledger_accounts_role;

UPDATE ledger_accounts SET account_role = 'GENERAL' WHERE account_role = 'CONTROL';
UPDATE ledger_accounts SET account_role = 'CONTROL' WHERE account_role = 'ROLLUP';

ALTER TABLE ledger_accounts
    ADD CONSTRAINT ck_ledger_accounts_role CHECK (account_role IN ('SUBLEDGER','CONTROL','GENERAL'));

-- REPLACE rather than CONCAT with a SUBSTRING offset. Hand-counted offsets are how
-- 'control:suspense:unmatched' quietly becomes 'clearing:suspense:nmatched': the prefix is
-- 17 characters and the obvious guess is 19. Each prefix here occurs only at the start, and
-- the WHERE clause anchors it, so a straight substitution cannot be off by one.
UPDATE ledger_accounts SET code = REPLACE(code, 'control:psp:',       'asset:psp:')
 WHERE code LIKE 'control:psp:%';
UPDATE ledger_accounts SET code = REPLACE(code, 'control:bank:',      'asset:bank:')
 WHERE code LIKE 'control:bank:%';
UPDATE ledger_accounts SET code = REPLACE(code, 'control:liquidity:', 'asset:liquidity:')
 WHERE code LIKE 'control:liquidity:%';
UPDATE ledger_accounts SET code = REPLACE(code, 'control:suspense:',  'clearing:suspense:')
 WHERE code LIKE 'control:suspense:%';
UPDATE ledger_accounts SET code = 'clearing:payout:in_flight' WHERE code = 'control:payout:in_flight';
UPDATE ledger_accounts SET code = 'clearing:dispute:held'     WHERE code = 'control:dispute:held';
UPDATE ledger_accounts SET code = 'liability:funds_held'      WHERE code = 'control:funds_held';
UPDATE ledger_accounts SET code = 'control:counterparties'    WHERE code = 'control:ar:partners';


-- ---------------------------------------------------------------------------------------
-- The rollup view, now that only one role can have a subsidiary ledger beneath it.
--
-- It kept both roles in scope because either might have had children. With the meanings
-- corrected only CONTROL can, and a general account with sub-ledgers pointing at it is a
-- setup mistake rather than a shape to accommodate.
-- ---------------------------------------------------------------------------------------
DROP VIEW IF EXISTS v_control_rollup;

CREATE VIEW v_control_rollup AS
SELECT
    c.uuid                              AS control_account_uuid,
    c.tenant_code,
    c.code                              AS control_code,
    c.currency,
    c.balance_minor                     AS control_balance_minor,
    COALESCE(SUM(s.balance_minor), 0)   AS subledger_total_minor,
    c.balance_minor - COALESCE(SUM(s.balance_minor), 0) AS drift_minor,
    COUNT(s.uuid)                       AS subledger_count,
    (SELECT COUNT(*) FROM journal_lines l WHERE l.account_uuid = c.uuid) AS direct_line_count
FROM ledger_accounts c
LEFT JOIN ledger_accounts s ON s.control_account_uuid = c.uuid
WHERE c.account_role = 'CONTROL'
GROUP BY c.uuid, c.tenant_code, c.code, c.currency, c.balance_minor;

