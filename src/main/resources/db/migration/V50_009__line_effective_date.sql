-- =====================================================================================
-- Balance as of a date: making it correct, and then making it fast. In that order.
--
-- v_bank_attestation_delta answered "what did this account stand at on the statement
-- date" by taking the newest line at or before that date and reading its balance
-- snapshot. The comment above it called that a lookup rather than a fold. It was a
-- lookup, and it was answering a different question than the one being asked.
--
-- The snapshot chain (account_prev_balance_minor -> account_new_balance_minor) runs in
-- POSTING order: account_entry_seq. A line's snapshot is the balance after every line
-- with a lower seq, whatever those lines are dated. So the snapshot only answers an
-- as-of-DATE question while effective_at happens to rise with entry_seq on that account
-- — that is, while nothing is ever backdated.
--
-- Bank reconciliation backdates by design. Booking a fee the bank charged on the 20th,
-- discovered on the 3rd of the following month, is the entire point of the feature, and
-- V0.006's own header says so: "booking the missing transactions with the right
-- effective_at closes the delta by itself." It did not. Worked example, one bank account:
--
--     seq 1..5    January activity        -> balance  900
--     attestation January 31, bank says   -> 1000, delta 100, correct
--     seq 6..10   February activity       -> balance 1500
--     seq 11      the missing fee, effective January 20, +100 -> balance 1600
--
--   Newest line at or before January 31 is now seq 11, whose snapshot is 1600, because
--   the snapshot includes all ten lines posted before it — February included. The delta
--   reads 1000 - 1600 = -600. It was 100, the correct answer afterwards is 0, and the
--   number it actually produced was wrong in both magnitude and sign. Worse, it was wrong
--   in the direction that reads as "the bank is missing money", off a routine correction.
--
-- No snapshot can answer this. A balance ordered by date is a fold over the lines dated
-- at or before it, and that is a different traversal of the same rows, not a cheaper one.
-- So this migration stops pretending otherwise and makes the fold cheap instead:
-- effective_at is denormalised onto the line and indexed with signed_minor, so the fold
-- is a covering index range scan that never touches journal_entries at all.
--
-- Denormalising a fact is normally how two copies start disagreeing. It is safe here for
-- a specific reason rather than a hopeful one: both tables are append-only, the value is
-- immutable on the source row, and the copy is DERIVED BY A TRIGGER rather than supplied
-- by the writer. There is no code path, procedure included, that can insert a line whose
-- effective_at differs from its entry's.
-- =====================================================================================

ALTER TABLE journal_lines
    ADD COLUMN effective_at TIMESTAMP(6) NULL AFTER currency;


DELIMITER //

-- -------------------------------------------------------------------------------------
-- The line takes its date from its entry. Always, and regardless of what was passed.
--
-- BEFORE INSERT rather than a column on the INSERT inside ledger_post_entry(): a value
-- the procedure writes is only as true as the procedure, and this table is reachable by
-- migration, repair script and mysql shell. Deriving it here means the copy cannot be
-- wrong, which is the only condition under which duplicating a fact is worth doing.
-- -------------------------------------------------------------------------------------
CREATE TRIGGER trg_journal_lines_effective_at
BEFORE INSERT ON journal_lines
FOR EACH ROW
BEGIN
    DECLARE v_effective_at TIMESTAMP(6);

    SELECT effective_at INTO v_effective_at
      FROM journal_entries WHERE uuid = NEW.entry_uuid;

    IF v_effective_at IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'A journal line must belong to an entry that already exists';
    END IF;

    SET NEW.effective_at = v_effective_at;
END//


-- -------------------------------------------------------------------------------------
-- Backfilling a column on an append-only table.
--
-- trg_journal_lines_no_update forbids this, correctly. Rather than lift the guard for the
-- length of the backfill, it is replaced by a NARROWER one: every money-bearing column is
-- still frozen, and the single permitted change is effective_at moving from NULL to a
-- value. At no point during this migration can a line's amount, direction, account or
-- balance snapshot be altered — not even by this migration. The full guard is restored
-- immediately afterwards, so no permanent bypass is created; a session variable escape
-- hatch on the real trigger would have left one forever.
-- -------------------------------------------------------------------------------------
DROP TRIGGER trg_journal_lines_no_update//

CREATE TRIGGER trg_journal_lines_no_update
BEFORE UPDATE ON journal_lines
FOR EACH ROW
BEGIN
    IF OLD.effective_at IS NOT NULL OR NEW.effective_at IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'journal_lines is append-only; only the one-time effective_at backfill may write here';
    END IF;

    IF NOT (NEW.uuid                       <=> OLD.uuid)
    OR NOT (NEW.tenant_code                <=> OLD.tenant_code)
    OR NOT (NEW.entry_uuid                 <=> OLD.entry_uuid)
    OR NOT (NEW.account_uuid               <=> OLD.account_uuid)
    OR NOT (NEW.direction                  <=> OLD.direction)
    OR NOT (NEW.amount_minor               <=> OLD.amount_minor)
    OR NOT (NEW.signed_minor               <=> OLD.signed_minor)
    OR NOT (NEW.currency                   <=> OLD.currency)
    OR NOT (NEW.due_date                   <=> OLD.due_date)
    OR NOT (NEW.line_no                    <=> OLD.line_no)
    OR NOT (NEW.account_prev_balance_minor <=> OLD.account_prev_balance_minor)
    OR NOT (NEW.account_new_balance_minor  <=> OLD.account_new_balance_minor)
    OR NOT (NEW.account_entry_seq          <=> OLD.account_entry_seq)
    OR NOT (NEW.memo                       <=> OLD.memo)
    OR NOT (NEW.created_at                 <=> OLD.created_at)
    OR NOT (NEW.created_by                 <=> OLD.created_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'journal_lines is append-only; post a reversing entry instead';
    END IF;
END//

DELIMITER ;


UPDATE journal_lines l
  JOIN journal_entries e ON e.uuid = l.entry_uuid
   SET l.effective_at = e.effective_at
 WHERE l.effective_at IS NULL;


DELIMITER //

-- The guard as it was, restored verbatim.
DROP TRIGGER trg_journal_lines_no_update//

CREATE TRIGGER trg_journal_lines_no_update
BEFORE UPDATE ON journal_lines
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
        'journal_lines is append-only; post a reversing entry instead';
END//

DELIMITER ;


-- Nullable only for the length of the backfill above. Making it NOT NULL now is what turns the
-- insert trigger's guarantee into one the schema states rather than one the trigger merely upholds.
ALTER TABLE journal_lines
    MODIFY COLUMN effective_at TIMESTAMP(6) NOT NULL;

-- Fully covering for the fold: seek on (tenant, account), scan the dated range, sum a
-- column that is already in the index. Nothing reads a row and nothing joins to
-- journal_entries.
--
-- tenant_code leads rather than being appended because it is not there for the optimizer.
-- StatementQuery.balanceAsOf takes accountUuid from a path parameter, so the tenant
-- predicate is the access control on that endpoint, not a filter — an index that made the
-- query fast only when the predicate was dropped would be an invitation to drop it.
CREATE INDEX idx_journal_lines_account_effective
    ON journal_lines (tenant_code, account_uuid, effective_at, signed_minor);


-- ---------------------------------------------------------------------------------------
-- The book against the bank, now asking the question it meant to ask.
--
-- book_balance_minor is a fold, and the comment says fold because that is what it is. It
-- is bounded by the account's lines up to the statement date and served entirely from
-- idx_journal_lines_account_effective, which is a different thing from cheap-because-we
-- -stored-the-answer. Storing the answer is what would break it: an as-of-date balance
-- that was materialised at attestation time would not move when a backdated correction
-- landed, and the delta would go stale exactly when it was doing its job.
-- ---------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_bank_attestation_delta AS
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
            SELECT SUM(l.signed_minor)
              FROM journal_lines l
             WHERE l.tenant_code   = t.tenant_code
               AND l.account_uuid  = t.account_uuid
               AND l.effective_at  < t.statement_date + INTERVAL 1 DAY
        ), 0)               AS book_balance_minor
    FROM bank_statement_attestations t
    JOIN ledger_accounts a ON a.uuid = t.account_uuid
) d;


-- ---------------------------------------------------------------------------------------
-- Clearing aging, reading the date off the line.
--
-- Same result, one join fewer: open_since no longer walks into journal_entries for a
-- column the line now carries. Semantics are unchanged — still the earliest line posted
-- since the account was last at zero, still pessimistic under continuous flow, still
-- reporting rather than an invariant for exactly that reason.
-- ---------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_clearing_aging AS
SELECT
    a.uuid          AS account_uuid,
    a.tenant_code,
    a.code,
    a.currency,
    a.balance_minor,
    COALESCE(a.clearing_tolerance_days, 2) AS tolerance_days,
    (SELECT MIN(l.effective_at)
       FROM journal_lines l
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

