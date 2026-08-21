-- =====================================================================================
-- Manual bank reconciliation.
--
-- The bank control account is the only account whose correct value is defined OUTSIDE
-- this system. Every other balance is a consequence of entries we posted; this one is a
-- claim about what a third party holds. That asymmetry is the whole problem: the book can
-- be internally perfect — trial balance zero, no drift on any account — and still be
-- wrong about the bank, because a transaction we never heard about is missing from both
-- sides of the entry at once. Double-entry catches entries that are wrong. It cannot
-- catch entries that are absent.
--
-- So the bank account needs an external witness, and that is what an attestation is: the
-- closing balance the bank itself states for a date. The delta against our own balance on
-- that date is, by construction, exactly the activity we have not booked.
--
-- Two deliberate choices:
--
--  * The attestation stores ONLY the bank's fact — date, balance, reference. It does not
--    store our balance or the delta. Both are computed live, so that booking the missing
--    transactions with the right effective_at closes the delta by itself. A stored delta
--    would be a copy of a number compared against itself, which is the error this service
--    was extracted to correct.
--
--  * Attestations are mutable and are NOT journal data. A bank reissuing a corrected
--    statement should overwrite the row. Nothing in the ledger depends on the previous
--    value, and the append-only guarantee protects journal history, not observations.
-- =====================================================================================

CREATE TABLE bank_statement_attestations (
    uuid                    UUID            NOT NULL PRIMARY KEY,
    tenant_code             VARCHAR(64)     NOT NULL,

    -- The ledger account this statement is a witness for. One real bank account per
    -- ledger account: blending two banks into one code makes both unreconcilable.
    account_uuid            UUID            NOT NULL,

    statement_date          DATE            NOT NULL,

    -- As the bank states it. Debit-positive like every other balance here, which for an
    -- asset account means positive = funds held, so it compares to balance_minor directly.
    closing_balance_minor   BIGINT          NOT NULL,
    currency                CHAR(3)         NOT NULL,

    statement_ref           VARCHAR(160)    NULL,
    note                    TEXT            NULL,

    created_at              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by              UUID            NULL,
    updated_at              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by              UUID            NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT uq_bank_attestation_date UNIQUE (tenant_code, account_uuid, statement_date),
    CONSTRAINT fk_bank_attestation_acct FOREIGN KEY (account_uuid) REFERENCES ledger_accounts (uuid)
);

-- Attestations newest first, which is the order the reconciliation screen wants them: the
-- statement someone is working through is nearly always the most recent one.
CREATE INDEX idx_bank_attestation_tenant ON bank_statement_attestations (tenant_code, statement_date);


-- ---------------------------------------------------------------------------------------
-- The book against the bank.
--
-- book_balance_minor is a lookup, not a fold: every journal line already snapshots the
-- balance it produced, so "what did this account stand at on that date" is the newest
-- line at or before it. delta_minor <> 0 means activity on the real account that is not
-- in our books — which for a platform-dedicated sub-account should be a short, nameable
-- list rather than a mystery.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_bank_attestation_delta AS
SELECT
    d.attestation_uuid,
    d.tenant_code,
    d.account_uuid,
    d.account_code,
    d.currency,
    d.statement_date,
    d.statement_ref,
    d.closing_balance_minor,
    d.book_balance_minor,
    d.closing_balance_minor - d.book_balance_minor AS delta_minor
FROM (
    SELECT
        t.uuid              AS attestation_uuid,
        t.tenant_code,
        t.account_uuid,
        a.code              AS account_code,
        t.currency,
        t.statement_date,
        t.statement_ref,
        t.closing_balance_minor,
        COALESCE((
            SELECT l.account_new_balance_minor
              FROM journal_lines l
              JOIN journal_entries e ON e.uuid = l.entry_uuid
             WHERE l.account_uuid = t.account_uuid
               AND e.effective_at < t.statement_date + INTERVAL 1 DAY
             ORDER BY l.account_entry_seq DESC
             LIMIT 1
        ), 0)               AS book_balance_minor
    FROM bank_statement_attestations t
    JOIN ledger_accounts a ON a.uuid = t.account_uuid
) d;


-- ---------------------------------------------------------------------------------------
-- How long money has sat unexplained.
--
-- Suspense is where a transaction lands the moment it is seen and before anyone knows what
-- it was. That is the right trade — the bank balance becomes correct immediately, and the
-- classification catches up — but only if the account drains. A standing suspense balance
-- is money we cannot explain, so its AGE is the number that matters, not its size.
--
-- open_since is the effective date of the earliest line posted since the account was last
-- at zero. The per-line balance snapshots make that a lookup too: the last line whose
-- account_new_balance_minor was 0 is the point everything before it netted out.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_suspense_aging AS
SELECT
    a.uuid          AS account_uuid,
    a.tenant_code,
    a.code,
    a.currency,
    a.balance_minor,
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
WHERE a.code LIKE 'control:suspense:%'
  AND a.balance_minor <> 0;

