-- =====================================================================================
-- Reporting and invariant views.
--
-- Each of these is either something the old system could not answer at all, or something
-- it answered by comparing a number against a copy of itself.
-- =====================================================================================

-- The trial balance. net_minor MUST be zero for every tenant and currency; a non-zero
-- row means something wrote to journal_lines outside ledger_post_entry().
CREATE VIEW v_trial_balance AS
SELECT
    l.tenant_code,
    l.currency,
    SUM(CASE WHEN l.signed_minor > 0 THEN  l.signed_minor ELSE 0 END) AS debit_minor,
    SUM(CASE WHEN l.signed_minor < 0 THEN -l.signed_minor ELSE 0 END) AS credit_minor,
    SUM(l.signed_minor)                                               AS net_minor
FROM journal_lines l
GROUP BY l.tenant_code, l.currency;


-- Every account's stored balance against the sum of its own lines. drift_minor must be
-- zero. Unlike the checkpoint table this replaces, the two sides are produced by
-- different mechanisms — a running UPDATE versus an aggregate over immutable rows — so
-- this can actually fail when something is wrong.
CREATE VIEW v_account_balance_audit AS
SELECT
    a.uuid                                        AS account_uuid,
    a.tenant_code,
    a.code,
    a.currency,
    a.account_kind,
    a.balance_minor                               AS stored_balance_minor,
    COALESCE(SUM(l.signed_minor), 0)              AS derived_balance_minor,
    a.balance_minor - COALESCE(SUM(l.signed_minor), 0) AS drift_minor,
    a.entry_seq                                   AS stored_entry_seq,
    COALESCE(MAX(l.account_entry_seq), 0)         AS max_line_seq
FROM ledger_accounts a
LEFT JOIN journal_lines l ON l.account_uuid = a.uuid
GROUP BY a.uuid, a.tenant_code, a.code, a.currency, a.account_kind, a.balance_minor, a.entry_seq;


-- Open-item view. A sub-ledger line is "open" for whatever part of it has not yet been
-- allocated against an opposing item. Computed from the allocation table rather than
-- stored on the line, so journal_lines stays append-only and the two cannot drift.
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
JOIN ledger_accounts a  ON a.uuid = l.account_uuid AND a.account_kind = 'SUBLEDGER'
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


-- Aging. Buckets are a query because the due date is on the line (Teardown F-05).
--
-- Ages the amount still OPEN, not the amount originally posted: a 500,000 invoice with
-- 300,000 allocated against it is 200,000 overdue, not 500,000. Sign is carried over from
-- the line so a credit item ages as a negative.
CREATE VIEW v_aging AS
SELECT
    o.tenant_code,
    o.account_uuid,
    o.account_code,
    o.owner_type,
    o.owner_id,
    o.currency,
    SUM(CASE WHEN o.due_date IS NULL OR o.due_date >= CURDATE()
             THEN o.open_signed_minor ELSE 0 END)                                AS current_minor,
    SUM(CASE WHEN o.due_date <  CURDATE() AND o.due_date >= CURDATE() - INTERVAL 30 DAY
             THEN o.open_signed_minor ELSE 0 END)                                AS overdue_1_30_minor,
    SUM(CASE WHEN o.due_date <  CURDATE() - INTERVAL 30 DAY AND o.due_date >= CURDATE() - INTERVAL 60 DAY
             THEN o.open_signed_minor ELSE 0 END)                                AS overdue_31_60_minor,
    SUM(CASE WHEN o.due_date <  CURDATE() - INTERVAL 60 DAY AND o.due_date >= CURDATE() - INTERVAL 90 DAY
             THEN o.open_signed_minor ELSE 0 END)                                AS overdue_61_90_minor,
    SUM(CASE WHEN o.due_date <  CURDATE() - INTERVAL 90 DAY
             THEN o.open_signed_minor ELSE 0 END)                                AS overdue_90_plus_minor,
    SUM(o.open_signed_minor)                                                     AS total_minor
FROM v_open_items o
WHERE o.open_minor > 0
GROUP BY o.tenant_code, o.account_uuid, o.account_code, o.owner_type, o.owner_id, o.currency;


-- Sub-ledger to control rollup. Control accounts that carry children are pure rollups and
-- must never be posted to directly; the invariant checker asserts direct_line_count = 0.
-- Control accounts with no children (a bank or PSP position) are ordinary posted accounts
-- and are excluded from that assertion.
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
WHERE c.account_kind = 'CONTROL'
GROUP BY c.uuid, c.tenant_code, c.code, c.currency, c.balance_minor;


-- Metadata rollup reconciliation (pgledger's pattern, generalised).
--
-- Any business grouping that ought to net to zero once it completes — a payment fully
-- received, a refund fully sent, a settlement batch fully applied — can be checked by
-- grouping on a metadata key and looking for a non-zero sum. This is what catches a
-- provider settling 49.50 against a 50.00 payment: the group names itself and states the
-- shortfall, without needing a dedicated control account per concern.
CREATE VIEW v_entry_group_balance AS
SELECT
    e.tenant_code,
    JSON_VALUE(e.metadata, '$.reconcileGroup') AS reconcile_group,
    l.currency,
    SUM(l.signed_minor)                        AS net_minor,
    COUNT(DISTINCT e.uuid)                     AS entry_count,
    MIN(e.effective_at)                        AS first_effective_at,
    MAX(e.effective_at)                        AS last_effective_at
FROM journal_entries e
JOIN journal_lines   l ON l.entry_uuid = e.uuid
WHERE JSON_VALUE(e.metadata, '$.reconcileGroup') IS NOT NULL
GROUP BY e.tenant_code, JSON_VALUE(e.metadata, '$.reconcileGroup'), l.currency;


-- A journal entry always has two or more lines. Anything less means a header was written
-- without its lines — which the transaction guard in ledger_post_entry() is there to make
-- impossible, so a non-empty result here indicates the guard was bypassed.
CREATE VIEW v_incomplete_entries AS
SELECT
    e.uuid AS entry_uuid,
    e.tenant_code,
    e.event_type,
    e.idempotency_key,
    e.created_at,
    COUNT(l.uuid) AS line_count
FROM journal_entries e
LEFT JOIN journal_lines l ON l.entry_uuid = e.uuid
GROUP BY e.uuid, e.tenant_code, e.event_type, e.idempotency_key, e.created_at
HAVING COUNT(l.uuid) < 2;

