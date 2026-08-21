-- =====================================================================================
-- The allocation guard could be raced, and was.
--
-- trg_item_allocations_within_open decides whether an allocation fits by summing what has
-- already been allocated against each of the two lines it touches. Every read in it was a
-- plain SELECT, and under REPEATABLE READ a plain SELECT answers from the transaction's
-- own consistent-read snapshot rather than from committed state. So N transactions
-- allocating against the same open item concurrently each read "nothing allocated yet",
-- each conclude there is room, and all of them insert.
--
-- Measured, not theorised: six concurrent allocations of 4,000 against an item with a face
-- value of 10,000 were all accepted, for a total of 24,000 and an open_minor of -14,000.
-- That is the same amount settled more than twice over — a counterparty reported as paid
-- while still owed, and an aging report that cannot be reconciled to the balance.
--
-- It is the same bug the posting procedure carries a FOR UPDATE to prevent, on a different
-- table. It survived because nothing wrote to item_allocations until AllocateItemUC did:
-- the constraints and this trigger were written with the schema and then guarded a feature
-- that did not exist for as long as it took to notice.
--
-- The fix has to be split, because the obvious version is not allowed.
--
-- What this trigger can do is serialise: it locks the two journal_lines rows FOR UPDATE, in
-- uuid order for the reason ledger_post_entry() locks accounts in uuid order — two
-- allocations over the same pair in opposite roles would otherwise take the rows in
-- opposite orders and deadlock. After that, every allocation touching a given item is
-- queued behind whoever got there first.
--
-- What it cannot do is read a fresh total. Serialising is not enough on its own: locking a
-- row does not move the transaction's consistent-read snapshot, so a plain SELECT
-- afterwards still answers from the old one, and the natural fix — making the SUM a
-- locking read — is refused outright:
--
--     Can't update table 'item_allocations' in stored function/trigger because it is
--     already used by statement which invoked this stored function/trigger
--
-- MariaDB counts a locking read as a write, and a trigger may not lock the table it fires
-- for. So the authoritative check moves to AllocateItemUC, which holds the same line locks
-- this trigger takes and can issue the locking read. The sums below stay as a backstop for
-- anything that reaches the table another way: a stale snapshot can only under-count, so
-- the check can wrongly accept but never wrongly reject, which is the right direction for a
-- fallback that is no longer the primary guard.
--
-- base.arch.SingleWritePathTest asserts AllocateItemUC is the only writer, the same way it
-- asserts PostEntryUC is the only caller of the posting procedure.
-- =====================================================================================

DELIMITER //

DROP TRIGGER trg_item_allocations_within_open//

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
    DECLARE v_lock_probe    BIGINT;

    -- Deterministic lock order over the pair, so two allocations that touch the same two
    -- lines in opposite roles cannot deadlock against each other.
    SELECT amount_minor INTO v_lock_probe FROM journal_lines
     WHERE uuid = LEAST(NEW.open_line_uuid, NEW.settle_line_uuid) FOR UPDATE;
    SELECT amount_minor INTO v_lock_probe FROM journal_lines
     WHERE uuid = GREATEST(NEW.open_line_uuid, NEW.settle_line_uuid) FOR UPDATE;

    -- Plain reads are correct here: journal_lines is append-only, so a line's amount and
    -- direction can never differ between the snapshot and committed state.
    SELECT amount_minor, direction INTO v_open_amount, v_open_dir
      FROM journal_lines WHERE uuid = NEW.open_line_uuid;
    SELECT amount_minor, direction INTO v_settle_amount, v_settle_dir
      FROM journal_lines WHERE uuid = NEW.settle_line_uuid;

    IF v_open_amount IS NULL OR v_settle_amount IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'An allocation must reference two journal lines that exist';
    END IF;

    IF v_open_dir = v_settle_dir THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'An allocation must pair opposite directions (a DR item against a CR item)';
    END IF;

    -- Backstop only; see the header. Snapshot-bound, so it under-counts under concurrency.
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

DELIMITER ;

