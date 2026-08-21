-- =====================================================================================
-- Splits account_kind into the two orthogonal things it was conflating.
--
-- account_kind was SUBLEDGER | CONTROL | REVENUE | EXPENSE | EQUITY. Three of those are
-- classifications and two are structures, which meant the chart of accounts could not
-- answer the first question anyone asks of a ledger: is this an asset or a liability?
-- Standard double-entry classifies every account into exactly five — asset, liability,
-- equity, revenue, expense — because that is the accounting equation and it is what
-- decides which statement an account appears on. A trial balance proves the entries sum
-- to zero; it says nothing about what the balances mean.
--
--   account_class   ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE   (the equation)
--   account_role    SUBLEDGER | CONTROL | ROLLUP | GENERAL           (the structure)
--
-- The two are independent, which is precisely why one column could not carry both: a
-- control account may be an asset (a bank position) or a liability (funds in flight), and
-- knowing it is a control tells you nothing about which.
--
-- Two-way accounts are classified by their NORMAL side, the standard treatment. A partner
-- sub-ledger is a LIABILITY because we normally owe partners; one carrying a debit balance
-- is reclassified to receivables for presentation, not moved to another account.
-- =====================================================================================

-- Views are rebuilt at the end; they must be dropped before the column they read goes.
DROP VIEW IF EXISTS v_account_balance_audit;
DROP VIEW IF EXISTS v_open_items;
DROP VIEW IF EXISTS v_control_rollup;

ALTER TABLE ledger_accounts
    ADD COLUMN account_class VARCHAR(16) NULL AFTER account_kind,
    ADD COLUMN account_role  VARCHAR(16) NULL AFTER account_class;

-- Backfill. Revenue, expense and equity carry over unchanged; the control accounts are
-- classified by what the code names, which is the only evidence the old schema retained.
UPDATE ledger_accounts SET account_class = CASE
    WHEN account_kind = 'REVENUE' THEN 'REVENUE'
    WHEN account_kind = 'EXPENSE' THEN 'EXPENSE'
    WHEN account_kind = 'EQUITY'  THEN 'EQUITY'
    -- Positions we hold and can draw on.
    WHEN code LIKE 'control:bank:%'      THEN 'ASSET'
    WHEN code LIKE 'control:psp:%'       THEN 'ASSET'
    WHEN code LIKE 'control:liquidity:%' THEN 'ASSET'
    -- Everything else on the books is something we owe: partner sub-ledgers, funds held,
    -- payouts in flight, and receipts we cannot yet explain.
    ELSE 'LIABILITY'
END;

UPDATE ledger_accounts SET account_role = CASE
    WHEN account_kind = 'SUBLEDGER' THEN 'SUBLEDGER'
    WHEN account_kind = 'CONTROL'   THEN 'CONTROL'
    ELSE 'GENERAL'
END;

-- A control account that something rolls up into is a ROLLUP and must never be posted to
-- directly. The extra derived table is not decoration: MariaDB refuses a subquery that
-- names the table being updated unless it is materialised first.
UPDATE ledger_accounts
   SET account_role = 'ROLLUP'
 WHERE account_role = 'CONTROL'
   AND uuid IN (SELECT child_control FROM (
           SELECT DISTINCT control_account_uuid AS child_control
             FROM ledger_accounts
            WHERE control_account_uuid IS NOT NULL
       ) AS parents);

ALTER TABLE ledger_accounts
    MODIFY COLUMN account_class VARCHAR(16) NOT NULL,
    MODIFY COLUMN account_role  VARCHAR(16) NOT NULL,
    ADD CONSTRAINT ck_ledger_accounts_class
        CHECK (account_class IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    ADD CONSTRAINT ck_ledger_accounts_role
        CHECK (account_role  IN ('SUBLEDGER','CONTROL','ROLLUP','GENERAL'));

DROP INDEX idx_ledger_accounts_tenant ON ledger_accounts;

ALTER TABLE ledger_accounts
    DROP CONSTRAINT ck_ledger_accounts_kind,
    DROP COLUMN account_kind;

-- Rebuilt on the column that replaced account_kind: a tenant's accounts by what statement they
-- land on, which is what v_position_by_class groups.
CREATE INDEX idx_ledger_accounts_tenant ON ledger_accounts (tenant_code, account_class);
-- And by structural role, so "every sub-ledger" or "every control account" is a range rather than
-- a scan. v_open_items and v_control_rollup both start here.
CREATE INDEX idx_ledger_accounts_role   ON ledger_accounts (tenant_code, account_role);


-- ---------------------------------------------------------------------------------------
-- Views rebuilt against the new columns. Bodies are otherwise unchanged.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_account_balance_audit AS
SELECT
    a.uuid                                        AS account_uuid,
    a.tenant_code,
    a.code,
    a.currency,
    a.account_class,
    a.account_role,
    a.balance_minor                               AS stored_balance_minor,
    COALESCE(SUM(l.signed_minor), 0)              AS derived_balance_minor,
    a.balance_minor - COALESCE(SUM(l.signed_minor), 0) AS drift_minor,
    a.entry_seq                                   AS stored_entry_seq,
    COALESCE(MAX(l.account_entry_seq), 0)         AS max_line_seq
FROM ledger_accounts a
LEFT JOIN journal_lines l ON l.account_uuid = a.uuid
GROUP BY a.uuid, a.tenant_code, a.code, a.currency, a.account_class, a.account_role,
         a.balance_minor, a.entry_seq;

CREATE VIEW v_open_items AS
SELECT
    l.uuid                                          AS line_uuid,
    l.tenant_code,
    l.account_uuid,
    a.code                                          AS account_code,
    a.owner_type,
    a.owner_id,
    l.entry_uuid,
    e.event_type,
    e.document_type,
    e.document_id,
    l.direction,
    l.amount_minor,
    l.signed_minor,
    l.currency,
    l.due_date,
    e.effective_at,
    COALESCE(al.allocated_minor, 0)                 AS allocated_minor,
    l.amount_minor - COALESCE(al.allocated_minor, 0) AS open_minor,
    (l.amount_minor - COALESCE(al.allocated_minor, 0))
        * CASE WHEN l.direction = 'DR' THEN 1 ELSE -1 END AS open_signed_minor
FROM journal_lines l
JOIN ledger_accounts a  ON a.uuid = l.account_uuid AND a.account_role = 'SUBLEDGER'
JOIN journal_entries e  ON e.uuid = l.entry_uuid
LEFT JOIN (
    SELECT line_uuid, SUM(amt) AS allocated_minor
    FROM (
        SELECT open_line_uuid   AS line_uuid, amount_minor AS amt FROM item_allocations
        UNION ALL
        SELECT settle_line_uuid AS line_uuid, amount_minor AS amt FROM item_allocations
    ) both_sides
    GROUP BY line_uuid
) al ON al.line_uuid = l.uuid;

-- Now that ROLLUP is its own role, the "has children" test the old view had to infer is
-- recorded on the row. Both roles stay in scope: the invariant that a rollup carries no
-- direct postings is expressed by subledger_count > 0, exactly as before.
CREATE VIEW v_control_rollup AS
SELECT
    c.uuid                              AS control_account_uuid,
    c.tenant_code,
    c.code                              AS control_code,
    c.currency,
    c.balance_minor                     AS control_balance_minor,
    COALESCE(SUM(s.balance_minor), 0)   AS subledger_total_minor,
    COUNT(s.uuid)                       AS subledger_count,
    (SELECT COUNT(*) FROM journal_lines dl WHERE dl.account_uuid = c.uuid) AS direct_line_count
FROM ledger_accounts c
LEFT JOIN ledger_accounts s ON s.control_account_uuid = c.uuid
WHERE c.account_role IN ('CONTROL', 'ROLLUP')
GROUP BY c.uuid, c.tenant_code, c.code, c.currency, c.balance_minor;


-- ---------------------------------------------------------------------------------------
-- The thing the old chart of accounts could not produce.
--
-- v_trial_balance proves the entries sum to zero, which is a statement about arithmetic.
-- This is a statement about meaning: what we hold, what we owe, what we earned. It is a
-- plain GROUP BY and it was impossible before, because nothing recorded whether an account
-- was an asset or a liability.
--
-- signed_minor keeps the ledger's debit-positive convention. natural_minor flips the
-- classes that normally carry credit, so each line reads the way it would be presented.
-- Summing natural_minor across all five classes gives assets - liabilities - equity -
-- (revenue - expense) = 0, which is the accounting equation and the same fact the trial
-- balance asserts — now legible per statement rather than as one number.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_position_by_class AS
SELECT
    a.tenant_code,
    a.currency,
    a.account_class,
    COUNT(*)                                 AS account_count,
    SUM(a.balance_minor)                     AS signed_minor,
    CASE WHEN a.account_class IN ('ASSET', 'EXPENSE')
         THEN  SUM(a.balance_minor)
         ELSE -SUM(a.balance_minor)
    END                                      AS natural_minor
FROM ledger_accounts a
GROUP BY a.tenant_code, a.currency, a.account_class;

