-- =====================================================================================
-- What each account stood at when the period's door shut.
--
-- journal_lines is append-only and never shrinks, so any verification that folds it costs
-- O(all history) — and history grows forever. Measured at three volumes, median of five:
-- v_account_balance_audit ran 192 / 414 / 880 ms over 100k / 200k / 400k lines. Cleanly
-- linear, which is not the same as bounded: at ~11M lines a year that is ~22 s after year
-- one and ~44 s after year two, every fifteen minutes. Lengthening the interval buys a
-- constant factor and nothing more.
--
-- The observation that fixes it is that re-folding a closed period verifies something that
-- cannot have changed. Two triggers already guarantee that: trg_journal_lines_no_update
-- makes a line immutable, and trg_journal_entries_period_closed refuses any entry dated
-- into a closed period. A closed period's contribution to every balance is therefore frozen
-- by construction, and folding it again on the quarter hour is not caution, it is waste.
--
-- So a close records where every account stood, and the recurring check folds forward from
-- there. Cost becomes O(lines since the last close): bounded by the close cadence rather
-- than by total history, and the same in year ten as in year one.
--
-- WHY THIS IS NOT A CHECKPOINT
--
-- The predecessor's ledger_checkpoints stored a copy of a balance and then "verified" it
-- against itself, so it could never fail. The difference here is where the verification
-- happens and what holds it up afterwards:
--
--   * The number is computed at close by a full fold of the immutable lines, and
--     ClosePeriodUC refuses to seal a period whose accounts do not already reconcile. That
--     is a real check, made at the one moment a complete scan is the right thing to pay for.
--   * It stays true afterwards because the rows behind it cannot change — not by policy but
--     by trigger.
--   * The full fold is kept, still runs, just far less often. A bug in the incremental path
--     cannot hide behind its own shortcut.
--
-- NAMING
--
-- Closing balances, not opening balances. In accounting a period-end close posts entries
-- that zero the temporary accounts — revenue and expense — into equity, so their OPENING
-- balance for the next period is zero. This table holds no such claim: it records what each
-- account stood at, for every class, and nothing is posted and nothing is reset. Calling it
-- an opening balance would tell an accountant that revenue starts from the number in this
-- row, which is exactly wrong.
--
-- This service does not post closing entries at all, deliberately. The accounting equation
-- holds either way — v_position_by_class computes assets - liabilities - equity -
-- (revenue - expense) = 0 whether or not revenue has been swept into equity — and deciding
-- when a fiscal year ends belongs to the general ledger that consumes this one, not here.
-- =====================================================================================

CREATE TABLE period_closing_balances (
    uuid            UUID            NOT NULL PRIMARY KEY,
    tenant_code     VARCHAR(64)     NOT NULL,

    period_uuid     UUID            NOT NULL,

    -- Denormalised from the period so the audit view can partition lines against it without
    -- reaching into closed_periods for every row it considers.
    closed_through  TIMESTAMP(6)    NOT NULL,

    account_uuid    UUID            NOT NULL,

    -- Denormalised so a trial balance can group by currency without joining accounts.
    currency        CHAR(3)         NOT NULL,

    -- The fold of every line for this account dated at or before closed_through.
    balance_minor   BIGINT          NOT NULL,

    -- How many lines that fold covered. Not used by any check — it is what lets a test
    -- assert the balance folded the right ROWS rather than merely arriving at a number that
    -- happens to match.
    line_count      BIGINT          NOT NULL,

    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_period_closing_balances    UNIQUE (period_uuid, account_uuid),
    CONSTRAINT fk_period_closing_period      FOREIGN KEY (period_uuid)  REFERENCES closed_periods (uuid),
    CONSTRAINT fk_period_closing_account     FOREIGN KEY (account_uuid) REFERENCES ledger_accounts (uuid)
);

-- The join the audit view makes: find this tenant's watermark row for this account.
CREATE INDEX idx_period_closing_watermark
    ON period_closing_balances (tenant_code, closed_through, account_uuid);




-- ---------------------------------------------------------------------------------------
-- The same audit, folding forward from the newest usable close.
--
-- The watermark is the newest close a tenant has that is not currently reopened. It needs
-- no invalidation cascade, which is worth explaining because the opposite looks obvious:
-- reopening January seems to spoil the closing balances of every later period, since an
-- entry dated into January also falls before February's close. It does not, because
-- trg_journal_entries_period_closed rejects an entry if ANY unreopened close covers its
-- date. While February stays closed, nothing can be posted into January at all, so both
-- periods' numbers remain true. To actually change January you must reopen every close that
-- covers it — and at that point the watermark falls back on its own.
--
-- With no close at all the watermark is NULL, every line is "after" it, and this degenerates
-- to exactly v_account_balance_audit. The incremental view is therefore correct from the
-- first day, before anyone has closed anything.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_ledger_watermark AS
SELECT tenant_code, MAX(closed_through) AS closed_through
  FROM closed_periods
 WHERE reopened_at IS NULL
 GROUP BY tenant_code;


-- The audit itself: stored balance against the closing balance plus everything dated since. Same
-- question and same columns as v_account_balance_audit, asked over the part of history that can
-- still have changed.
CREATE VIEW v_account_balance_audit_incremental AS
SELECT
    a.uuid                                        AS account_uuid,
    a.tenant_code,
    a.code,
    a.currency,
    a.account_class,
    a.account_role,
    a.balance_minor                               AS stored_balance_minor,
    COALESCE(c.balance_minor, 0)
        + COALESCE(SUM(l.signed_minor), 0)        AS derived_balance_minor,
    a.balance_minor - (COALESCE(c.balance_minor, 0)
        + COALESCE(SUM(l.signed_minor), 0))       AS drift_minor,
    w.closed_through                              AS folded_from,
    COALESCE(c.line_count, 0)                     AS lines_before_watermark,
    COUNT(l.uuid)                                 AS lines_since_watermark
FROM ledger_accounts a
LEFT JOIN v_ledger_watermark w
       ON w.tenant_code = a.tenant_code
LEFT JOIN period_closing_balances c
       ON c.tenant_code = a.tenant_code
      AND c.account_uuid = a.uuid
      AND c.closed_through = w.closed_through
-- Two details here decide whether this is bounded work or a full scan wearing a filter.
--
-- tenant_code is in the join because idx_journal_lines_account_effective leads with it, and an
-- index whose first column is absent from the predicate cannot be used at all. Without it
-- MariaDB falls back to (account_uuid, account_entry_seq), fetches every line the account ever
-- had, and applies the date as an afterthought — measured at rows=1988 per account with "Using
-- where", which is the whole history this view exists to avoid reading.
--
-- And the floor is a COALESCE rather than "w.closed_through IS NULL OR ...", because a
-- disjunction over another table's column is not a range predicate. Same outcome: no range
-- scan. The sentinel is below TIMESTAMP's own minimum of 1970-01-01 00:00:01, so with no close
-- every line is still after it and the view degenerates to the full fold as intended.
--
-- A version driving from the date instead — one aggregate over "everything since the
-- watermark", joined back to accounts — is the better shape on paper and measured slower at
-- every volume: MariaDB materialises the intermediate aggregate for each query and cannot push
-- the account down into it. The simpler join wins here, on the measurement rather than on the
-- argument.
LEFT JOIN journal_lines l
       ON l.tenant_code = a.tenant_code
      AND l.account_uuid = a.uuid
      AND l.effective_at > COALESCE(w.closed_through, TIMESTAMP'1970-01-01 00:00:00')
GROUP BY a.uuid, a.tenant_code, a.code, a.currency, a.account_class, a.account_role,
         a.balance_minor, c.balance_minor, c.line_count, w.closed_through;


-- ---------------------------------------------------------------------------------------
-- The trial balance, folded forward the same way.
--
-- Only net_minor: the debit/credit split cannot be reconstructed from a net closing balance,
-- and nothing reads it from here — trialBalanceIsZero asks whether the net is zero. The full
-- view keeps the split for anyone presenting a trial balance rather than checking one.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_trial_balance_incremental AS
SELECT tenant_code, currency, SUM(part_minor) AS net_minor
FROM (
    SELECT c.tenant_code, c.currency, SUM(c.balance_minor) AS part_minor
      FROM period_closing_balances c
      JOIN v_ledger_watermark w
        ON w.tenant_code = c.tenant_code AND w.closed_through = c.closed_through
     GROUP BY c.tenant_code, c.currency

    UNION ALL

    SELECT l.tenant_code, l.currency, SUM(l.signed_minor) AS part_minor
      FROM journal_lines l
      LEFT JOIN v_ledger_watermark w
        ON w.tenant_code = l.tenant_code
     WHERE l.effective_at > COALESCE(w.closed_through, TIMESTAMP'1970-01-01 00:00:00')
     GROUP BY l.tenant_code, l.currency
) parts
GROUP BY tenant_code, currency;

