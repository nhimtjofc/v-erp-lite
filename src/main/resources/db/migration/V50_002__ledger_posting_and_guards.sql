-- =====================================================================================
-- Posting procedure, guard triggers, and reporting views.
--
-- Everything that must always be true about the ledger is enforced here rather than in
-- Java. The service this replaces kept its invariants in one well-behaved service class
-- and depended on every caller routing through it; that holds until the day something
-- doesn't. A trigger holds against an ORM flush, a migration, a repair script, and a
-- mysql shell.
-- =====================================================================================

DELIMITER //

-- -------------------------------------------------------------------------------------
-- ledger_accounts: balances are writable only from inside ledger_post_entry(), and the
-- balance policy is checked on every write regardless of origin.
-- -------------------------------------------------------------------------------------
CREATE TRIGGER trg_ledger_accounts_guard
BEFORE UPDATE ON ledger_accounts
FOR EACH ROW
BEGIN
    IF (NEW.balance_minor <> OLD.balance_minor OR NEW.entry_seq <> OLD.entry_seq)
       AND COALESCE(@ledger_posting, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Balance is maintained by ledger_post_entry() only; post a journal entry';
    END IF;

    -- Balance policy is checked when a balance MOVES, not when the policy is edited.
    -- Tightening policy on an account that currently breaches it must stay possible —
    -- otherwise an account cannot be wound down until it is already settled, which is
    -- backwards. The next posting is what enforces the new rule.
    IF COALESCE(@ledger_posting, 0) = 1 THEN
        IF NEW.allow_debit_balance = FALSE AND NEW.balance_minor > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
                'Account does not allow a debit balance';
        END IF;

        IF NEW.allow_credit_balance = FALSE AND NEW.balance_minor < 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
                'Account does not allow a credit balance';
        END IF;

        IF NEW.allow_debit_balance = TRUE AND NEW.balance_minor > NEW.credit_limit_minor THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
                'Account debit balance would exceed its credit limit';
        END IF;
    END IF;
END//

-- -------------------------------------------------------------------------------------
-- The journal is append-only. Corrections reverse; nothing is ever edited or removed.
-- -------------------------------------------------------------------------------------
CREATE TRIGGER trg_journal_entries_no_update
BEFORE UPDATE ON journal_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
        'journal_entries is append-only; post a reversing entry instead';
END//

CREATE TRIGGER trg_journal_entries_no_delete
BEFORE DELETE ON journal_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
        'journal_entries is append-only; DELETE is forbidden';
END//

CREATE TRIGGER trg_journal_lines_no_update
BEFORE UPDATE ON journal_lines
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
        'journal_lines is append-only; post a reversing entry instead';
END//

CREATE TRIGGER trg_journal_lines_no_delete
BEFORE DELETE ON journal_lines
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
        'journal_lines is append-only; DELETE is forbidden';
END//

-- An allocation may be withdrawn (DELETE) but never silently rewritten.
CREATE TRIGGER trg_item_allocations_no_update
BEFORE UPDATE ON item_allocations
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
        'item_allocations is immutable; withdraw and re-allocate instead';
END//

-- -------------------------------------------------------------------------------------
-- An allocation may never exceed what is still open on either line it touches. Checking
-- this in the application is a read-then-write race; checking it here is not.
-- -------------------------------------------------------------------------------------
CREATE TRIGGER trg_item_allocations_within_open
BEFORE INSERT ON item_allocations
FOR EACH ROW
BEGIN
    DECLARE v_open_amount   BIGINT;
    DECLARE v_settle_amount BIGINT;
    DECLARE v_open_used     BIGINT;
    DECLARE v_settle_used   BIGINT;
    DECLARE v_open_dir      CHAR(2);
    DECLARE v_settle_dir    CHAR(2);

    SELECT amount_minor, direction INTO v_open_amount, v_open_dir
      FROM journal_lines WHERE uuid = NEW.open_line_uuid;
    SELECT amount_minor, direction INTO v_settle_amount, v_settle_dir
      FROM journal_lines WHERE uuid = NEW.settle_line_uuid;

    IF v_open_dir = v_settle_dir THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'An allocation must pair opposite directions (a DR item against a CR item)';
    END IF;

    SELECT COALESCE(SUM(amount_minor), 0) INTO v_open_used
      FROM item_allocations
     WHERE open_line_uuid = NEW.open_line_uuid OR settle_line_uuid = NEW.open_line_uuid;

    SELECT COALESCE(SUM(amount_minor), 0) INTO v_settle_used
      FROM item_allocations
     WHERE open_line_uuid = NEW.settle_line_uuid OR settle_line_uuid = NEW.settle_line_uuid;

    IF v_open_used + NEW.amount_minor > v_open_amount THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Allocation exceeds the amount still open on the target item';
    END IF;

    IF v_settle_used + NEW.amount_minor > v_settle_amount THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Allocation exceeds the amount still unapplied on the settling item';
    END IF;
END//

-- -------------------------------------------------------------------------------------
-- ledger_post_entry — the only way money moves.
--
-- p_lines is a JSON array:
--   [{"lineUuid":"…","accountUuid":"…","direction":"DR","amountMinor":100,
--     "currency":"VND","dueDate":"2026-09-01","memo":"…"}, …]
--
-- Rejects, in order: fewer than two lines; a non-positive amount; an unbalanced entry;
-- an unknown, cross-tenant, frozen, or currency-mismatched account. Then locks every
-- account it will touch in a deterministic order and applies the lines.
-- -------------------------------------------------------------------------------------
CREATE PROCEDURE ledger_post_entry(
    IN p_entry_uuid      UUID,
    IN p_tenant_code     VARCHAR(64),
    IN p_event_type      VARCHAR(48),
    IN p_document_type   VARCHAR(32),
    IN p_document_id     UUID,
    IN p_provider        VARCHAR(32),
    IN p_provider_ref    VARCHAR(160),
    IN p_idempotency_key VARCHAR(200),
    IN p_effective_at    TIMESTAMP(6),
    IN p_reverses        UUID,
    IN p_memo            TEXT,
    IN p_metadata        JSON,
    IN p_actor           UUID,
    IN p_lines           JSON
)
MODIFIES SQL DATA
BEGIN
    DECLARE v_done          INT DEFAULT 0;
    DECLARE v_bad           INT DEFAULT 0;
    DECLARE v_count         INT DEFAULT 0;
    DECLARE v_lock_uuid     UUID;
    DECLARE v_lock_probe    BIGINT;

    DECLARE v_line_no       SMALLINT;
    DECLARE v_line_uuid     UUID;
    DECLARE v_account_uuid  UUID;
    DECLARE v_direction     CHAR(2);
    DECLARE v_amount        BIGINT;
    DECLARE v_currency      CHAR(3);
    DECLARE v_due           DATE;
    DECLARE v_memo          VARCHAR(500);
    DECLARE v_signed        BIGINT;
    DECLARE v_prev          BIGINT;
    DECLARE v_new           BIGINT;
    DECLARE v_seq           BIGINT;

    DECLARE cur_locks CURSOR FOR
        SELECT DISTINCT account_uuid FROM tmp_post_lines ORDER BY account_uuid;

    DECLARE cur_lines CURSOR FOR
        SELECT line_no, line_uuid, account_uuid, direction, amount_minor,
               currency, due_date, memo, signed_minor
          FROM tmp_post_lines ORDER BY line_no;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @ledger_posting = 0;
        DROP TEMPORARY TABLE IF EXISTS tmp_post_lines;
        RESIGNAL;
    END;

    -- MariaDB does NOT open an implicit transaction for a stored procedure: under
    -- autocommit, every statement below would commit on its own, and a line rejected by
    -- the balance-policy trigger would leave the already-committed journal_entries header
    -- behind with no lines. That is the exact "never write half an entry" failure this
    -- design exists to prevent, so atomicity is demanded rather than assumed. Quarkus
    -- @Transactional satisfies this; opening a transaction here instead would silently
    -- commit any enclosing one.
    -- Unsafe only when the session is in autocommit AND no explicit transaction is open.
    -- A JTA-managed connection sets autocommit = 0 rather than issuing START TRANSACTION, so
    -- @@in_transaction is still 0 at procedure entry even though every statement below is
    -- transactional; checking in_transaction alone would reject the normal application path.
    IF @@autocommit = 1 AND @@in_transaction = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'ledger_post_entry must be called inside a transaction';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_post_lines;
    CREATE TEMPORARY TABLE tmp_post_lines (
        line_no      SMALLINT     NOT NULL PRIMARY KEY,
        line_uuid    UUID         NOT NULL,
        account_uuid UUID         NOT NULL,
        direction    CHAR(2)      NOT NULL,
        amount_minor BIGINT       NOT NULL,
        currency     CHAR(3)      NOT NULL,
        due_date     DATE         NULL,
        memo         VARCHAR(500) NULL,
        signed_minor BIGINT       NOT NULL
    ) ENGINE=InnoDB;

    INSERT INTO tmp_post_lines
        (line_no, line_uuid, account_uuid, direction, amount_minor, currency, due_date, memo, signed_minor)
    SELECT jt.ord, jt.line_uuid, jt.account_uuid, jt.direction, jt.amount_minor,
           jt.currency, jt.due_date, jt.memo,
           CASE WHEN jt.direction = 'DR' THEN jt.amount_minor ELSE -jt.amount_minor END
      FROM JSON_TABLE(p_lines, '$[*]' COLUMNS (
               ord          FOR ORDINALITY,
               line_uuid    CHAR(36)     PATH '$.lineUuid',
               account_uuid CHAR(36)     PATH '$.accountUuid',
               direction    CHAR(2)      PATH '$.direction',
               amount_minor BIGINT       PATH '$.amountMinor',
               currency     CHAR(3)      PATH '$.currency',
               due_date     DATE         PATH '$.dueDate',
               memo         VARCHAR(500) PATH '$.memo'
           )) AS jt;

    SELECT COUNT(*) INTO v_count FROM tmp_post_lines;
    IF v_count < 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'A journal entry needs at least two lines';
    END IF;

    SELECT COUNT(*) INTO v_bad FROM tmp_post_lines WHERE amount_minor <= 0;
    IF v_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Journal line amounts must be positive; direction carries the sign';
    END IF;

    SELECT COUNT(*) INTO v_bad FROM tmp_post_lines WHERE direction NOT IN ('DR', 'CR');
    IF v_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Journal line direction must be DR or CR';
    END IF;

    -- The invariant the whole design exists for.
    SELECT COUNT(*) INTO v_bad FROM (
        SELECT currency FROM tmp_post_lines GROUP BY currency HAVING SUM(signed_minor) <> 0
    ) unbalanced;
    IF v_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Unbalanced entry: debits must equal credits within each currency';
    END IF;

    SELECT COUNT(*) INTO v_bad
      FROM tmp_post_lines l
      LEFT JOIN ledger_accounts a ON a.uuid = l.account_uuid
     WHERE a.uuid IS NULL OR a.tenant_code <> p_tenant_code;
    IF v_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Entry references an account that does not exist in this tenant';
    END IF;

    SELECT COUNT(*) INTO v_bad
      FROM tmp_post_lines l JOIN ledger_accounts a ON a.uuid = l.account_uuid
     WHERE a.currency <> l.currency;
    IF v_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Journal line currency does not match the account currency';
    END IF;

    SELECT COUNT(*) INTO v_bad
      FROM tmp_post_lines l JOIN ledger_accounts a ON a.uuid = l.account_uuid
     WHERE a.frozen = TRUE;
    IF v_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'Entry references a frozen account';
    END IF;

    -- Deadlock avoidance. Two entries touching the same accounts in opposite line order
    -- will deadlock unless every transaction acquires locks in one global order; sorting
    -- by uuid gives that order. Explicit cursor rather than a bare IN (…) FOR UPDATE so
    -- the ordering is a property of this code and not of the optimizer's chosen plan.
    OPEN cur_locks;
    lock_loop: LOOP
        FETCH cur_locks INTO v_lock_uuid;
        IF v_done = 1 THEN
            LEAVE lock_loop;
        END IF;
        SELECT balance_minor INTO v_lock_probe
          FROM ledger_accounts WHERE uuid = v_lock_uuid FOR UPDATE;
    END LOOP;
    CLOSE cur_locks;
    SET v_done = 0;

    INSERT INTO journal_entries
        (uuid, tenant_code, event_type, document_type, document_id, provider, provider_ref,
         idempotency_key, effective_at, reverses_entry_uuid, memo, metadata, created_at, created_by)
    VALUES
        (p_entry_uuid, p_tenant_code, p_event_type, p_document_type, p_document_id, p_provider,
         p_provider_ref, p_idempotency_key, COALESCE(p_effective_at, CURRENT_TIMESTAMP(6)),
         p_reverses, p_memo, p_metadata, CURRENT_TIMESTAMP(6), p_actor);

    OPEN cur_lines;
    line_loop: LOOP
        FETCH cur_lines INTO v_line_no, v_line_uuid, v_account_uuid, v_direction,
                             v_amount, v_currency, v_due, v_memo, v_signed;
        IF v_done = 1 THEN
            LEAVE line_loop;
        END IF;

        -- FOR UPDATE is load-bearing, not belt-and-braces. Under REPEATABLE READ a plain
        -- SELECT reads the transaction's snapshot, which was established before the lock
        -- loop above waited on this row — so it would return a stale entry_seq, and
        -- seq + 1 would collide with a sequence another transaction has already
        -- committed. A locking read always sees the latest committed version. The lock
        -- loop still matters: it fixes the ORDER locks are taken in. This fixes WHAT is
        -- read once they are held.
        SELECT balance_minor, entry_seq INTO v_prev, v_seq
          FROM ledger_accounts WHERE uuid = v_account_uuid FOR UPDATE;

        SET v_new = v_prev + v_signed;
        SET v_seq = v_seq + 1;

        SET @ledger_posting = 1;
        UPDATE ledger_accounts
           SET balance_minor = v_new,
               entry_seq     = v_seq,
               updated_by    = p_actor
         WHERE uuid = v_account_uuid;
        SET @ledger_posting = 0;

        INSERT INTO journal_lines
            (uuid, tenant_code, entry_uuid, account_uuid, direction, amount_minor, signed_minor,
             currency, due_date, line_no, account_prev_balance_minor, account_new_balance_minor,
             account_entry_seq, memo, created_at, created_by)
        VALUES
            (v_line_uuid, p_tenant_code, p_entry_uuid, v_account_uuid, v_direction, v_amount,
             v_signed, v_currency, v_due, v_line_no, v_prev, v_new, v_seq, v_memo,
             CURRENT_TIMESTAMP(6), p_actor);
    END LOOP;
    CLOSE cur_lines;

    DROP TEMPORARY TABLE IF EXISTS tmp_post_lines;
END//

DELIMITER ;

