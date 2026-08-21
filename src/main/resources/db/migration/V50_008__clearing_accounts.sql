-- =====================================================================================
-- Clearing accounts, and the check that makes them worth having.
--
-- The design already models unsettled money the way Stripe's Ledger does: as a position in
-- an intermediate account rather than as a status on a transaction. control:payout:in_flight
-- holds what we told the bank to send and have not been told landed; control:suspense:unmatched
-- holds what arrived that nobody has explained yet. That choice is why this ledger can keep
-- journal_entries strictly append-only — the alternative, a mutable "pending" transaction that
-- is edited into a posted one, would make the immutability triggers conditional, and a
-- conditional invariant is what the predecessor had.
--
-- But the model is two halves and only one was built. Stripe's correctness property is that a
-- clearing account is empty at steady state, and that a query for clearing accounts holding a
-- balance finds a single missing event among billions. We had the accounts and no query:
--
--   * control:payout:in_flight was monitored by nothing at all, even though InstructPayoutUC
--     says in as many words that its age is the earliest signal a bank integration is quietly
--     failing.
--   * v_entry_group_balance, which reconcileGroupsNetToZero reads, sums EVERY line of every
--     entry in a group. Every entry already balances per currency by the posting procedure's
--     own rule, so that sum is structurally always zero and the invariant has never been able
--     to fail. It was reaching for this check and could not express it.
--
-- The fix is to net over the CLEARING accounts a group touches, not over all of its lines. A
-- payout whose instruction was never confirmed leaves in_flight non-zero for its group; a bank
-- item never reclassified leaves suspense non-zero for its group. Both name themselves.
-- =====================================================================================

ALTER TABLE ledger_accounts
    -- "Items posted here are expected to clear individually." Not merely a transit account:
    -- control:psp:* is a real position with a settlement cycle and a standing balance, and
    -- control:funds_held is held for as long as the calling system says. Neither is clearing.
    ADD COLUMN is_clearing             BOOLEAN  NOT NULL DEFAULT FALSE AFTER frozen,
    -- How long an item may sit before it is a problem rather than a process.
    ADD COLUMN clearing_tolerance_days SMALLINT NULL     AFTER is_clearing;

-- Backfill by code, which until now was the only place this property was recorded — and only
-- in the mind of whoever read AccountCode.
UPDATE ledger_accounts
   SET is_clearing = TRUE, clearing_tolerance_days = 2
 WHERE code = 'control:payout:in_flight'
    OR code LIKE 'control:suspense:%';

-- Finds the clearing accounts without a LIKE over codes, which is the entire point of making this
-- a flag: a new clearing account is visible to the checks the moment it is opened.
CREATE INDEX idx_ledger_accounts_clearing ON ledger_accounts (tenant_code, is_clearing);


-- ---------------------------------------------------------------------------------------
-- What a lifecycle still has parked in a clearing account.
--
-- Restricted to clearing accounts, which is the whole difference from v_entry_group_balance:
-- summing every line of a balanced entry can only ever produce zero. Summing the clearing
-- lines produces zero exactly when the lifecycle has finished, which is the question.
--
-- tolerance_days takes the MAX across the accounts a group touches, so a group spanning two
-- clearing accounts is judged by the more patient of them.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_clearing_group_balance AS
SELECT
    e.tenant_code,
    JSON_VALUE(e.metadata, '$.reconcileGroup')  AS reconcile_group,
    l.currency,
    SUM(l.signed_minor)                         AS clearing_net_minor,
    COUNT(DISTINCT e.uuid)                      AS entry_count,
    MIN(e.effective_at)                         AS first_effective_at,
    MAX(e.effective_at)                         AS last_effective_at,
    MAX(COALESCE(a.clearing_tolerance_days, 2)) AS tolerance_days
FROM journal_entries e
JOIN journal_lines   l ON l.entry_uuid = e.uuid
JOIN ledger_accounts a ON a.uuid = l.account_uuid AND a.is_clearing = TRUE
WHERE JSON_VALUE(e.metadata, '$.reconcileGroup') IS NOT NULL
GROUP BY e.tenant_code, JSON_VALUE(e.metadata, '$.reconcileGroup'), l.currency;


-- ---------------------------------------------------------------------------------------
-- Clearing activity carrying no reconcile group at all.
--
-- The group check above can only see lifecycles that named themselves. Something posted into a
-- clearing account without a group is invisible to it, and invisible is the worst state for
-- money nobody has explained. Summed per account and reported only when it does not net out.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_untracked_clearing_items AS
SELECT
    a.tenant_code,
    a.code,
    l.currency,
    SUM(l.signed_minor)                     AS net_minor,
    COUNT(*)                                AS line_count,
    MIN(e.effective_at)                     AS oldest_effective_at,
    COALESCE(a.clearing_tolerance_days, 2)  AS tolerance_days
FROM journal_lines   l
JOIN journal_entries e ON e.uuid = l.entry_uuid
JOIN ledger_accounts a ON a.uuid = l.account_uuid AND a.is_clearing = TRUE
WHERE JSON_VALUE(e.metadata, '$.reconcileGroup') IS NULL
GROUP BY a.tenant_code, a.code, l.currency, a.clearing_tolerance_days
HAVING SUM(l.signed_minor) <> 0;


-- ---------------------------------------------------------------------------------------
-- Account-level view of what is currently parked, for reporting rather than alarming.
--
-- Generalises v_suspense_aging to every clearing account. open_since walks back to the last
-- line that left the account at zero, which is exact for an account that genuinely empties
-- and pessimistic for one under continuous flow — two overlapping payouts can keep in_flight
-- from ever touching zero while every individual payout clears on time. That is why the
-- INVARIANTS are group-based and this view is not one: it answers "what is sitting here",
-- not "what is wrong".
-- ---------------------------------------------------------------------------------------
DROP VIEW IF EXISTS v_suspense_aging;

CREATE VIEW v_clearing_aging AS
SELECT
    a.uuid          AS account_uuid,
    a.tenant_code,
    a.code,
    a.currency,
    a.balance_minor,
    COALESCE(a.clearing_tolerance_days, 2) AS tolerance_days,
    (SELECT MIN(e.effective_at)
       FROM journal_lines l
       JOIN journal_entries e ON e.uuid = l.entry_uuid
      WHERE l.account_uuid = a.uuid
        AND l.account_entry_seq > COALESCE((
                SELECT MAX(z.account_entry_seq)
                  FROM journal_lines z
                 WHERE z.account_uuid = a.uuid
                   AND z.account_new_balance_minor = 0
            ), 0)
    )               AS open_since
FROM ledger_accounts a
WHERE a.is_clearing = TRUE
  AND a.balance_minor <> 0;

