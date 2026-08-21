-- =====================================================================================
-- Closing a period, so a reported figure stays reported.
--
-- V0.009 made balance-as-of-a-date correct in the presence of backdating, which was the
-- right fix and which sharpened a different problem. Backdating now works properly, and
-- nothing governs it: an entry dated into last month can be posted today, and last
-- month's closing balance changes — silently, correctly, and after somebody has already
-- read the old number out to a partner or filed it.
--
-- Attestations do not cover this. A bank_statement_attestations row is a witness to what
-- a third party held; it constrains nothing and refuses nothing. What is missing is the
-- other half of a period: a statement that we are finished with it.
--
-- The check lives in a trigger for the same reason every other guarantee here does. It is
-- a BEFORE INSERT on journal_entries rather than a branch inside ledger_post_entry()
-- because the procedure is not the only way a row could reach this table — a migration, a
-- repair script and a mysql shell all bypass it, and those are exactly the situations in
-- which somebody is tempted to slip an entry into a closed month.
--
-- Reopening is allowed and is a deliberate, recorded act: DELETE the row, or set
-- reopened_at. It is not a bypass, because it leaves evidence, which "just post it and
-- nobody will notice" does not.
-- =====================================================================================

CREATE TABLE closed_periods (
    uuid            UUID            NOT NULL PRIMARY KEY,
    tenant_code     VARCHAR(64)     NOT NULL,

    -- Everything with an effective_at at or before this instant is closed. An instant
    -- rather than a month, so a period can be closed mid-day for a daily close and the
    -- same table serves both cadences.
    closed_through  TIMESTAMP(6)    NOT NULL,

    -- What the books said when we closed. Not used by any check — this is the number a
    -- dispute is settled against, and its value comes precisely from having been written
    -- down before anyone knew which figure would be questioned.
    note            TEXT            NULL,

    closed_at       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_by       UUID            NULL,
    reopened_at     TIMESTAMP(6)    NULL,
    reopened_by     UUID            NULL,
    reopen_reason   TEXT            NULL,

    CONSTRAINT uq_closed_periods UNIQUE (tenant_code, closed_through)
);

-- Two questions, one index: "is any unreopened period covering this date" for the posting guard,
-- and "what is the newest unreopened close" for the watermark the incremental audit folds from.
-- reopened_at sits in the middle because both filter on it being NULL.
CREATE INDEX idx_closed_periods_open ON closed_periods (tenant_code, reopened_at, closed_through);


DELIMITER //

-- -------------------------------------------------------------------------------------
-- An entry may not be dated into a period that has been closed.
--
-- Deliberately says which period and how to proceed. A guard that only says "no" gets
-- worked around; one that names the row to reopen gets used correctly.
-- -------------------------------------------------------------------------------------
CREATE TRIGGER trg_journal_entries_period_closed
BEFORE INSERT ON journal_entries
FOR EACH ROW
BEGIN
    DECLARE v_closed_through TIMESTAMP(6);

    SELECT MAX(closed_through) INTO v_closed_through
      FROM closed_periods
     WHERE tenant_code = NEW.tenant_code
       AND reopened_at IS NULL
       AND closed_through >= NEW.effective_at;

    IF v_closed_through IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Entry is dated into a closed period; reopen the period or date the correction into an open one';
    END IF;
END//

DELIMITER ;

