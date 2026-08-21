-- =====================================================================================
-- Two rules that were living in Java, moved to where the rest of them live.
--
-- The pattern this schema follows is that a use case validates first to produce a readable
-- error and the database decides. Both of the guards below were added application-side and
-- left the schema itself willing to accept the row — which is the arrangement the whole
-- service was built to avoid, since it holds exactly until something writes by another
-- route.
--
-- 1. A FEE SCHEDULE VERSION MUST GO FORWARD
--
-- publish() closes the window containing the new version's start date. When no window
-- contains it — a version dated before every existing one — there is nothing to close, and
-- the new row is inserted open-ended alongside the others. Reproduced: publish January,
-- publish March, publish the previous June, and both March and June are in force from March
-- onward, with effectiveAt() choosing between them by version number and no sign that it is
-- choosing.
--
-- Stating it as "a version starts after every version already published for its code" is
-- both the rule an operator would recognise and, for an append-only sequence, sufficient to
-- make overlap unreachable: each window ends where the next begins, and the last is open.
-- v_fee_schedule_overlaps stays as the check on that reasoning rather than the only defence.
--
-- Like trg_item_allocations_within_open, the read below is snapshot-bound and so can
-- under-detect under concurrency. It does not need to catch that case:
-- uq_fee_schedules_version already refuses two versions racing for the same number.
--
-- 2. A PROCESSED EVENT MUST BE ATTRIBUTABLE
--
-- provider_events.tenant_code is deliberately nullable — a webhook arrives belonging to
-- nobody until its reference resolves. But once dispatched it has been posted into some
-- tenant's ledger, so "processed and still nobody's" describes a posting nothing can be
-- traced back to. That is a CHECK, not a convention.
-- =====================================================================================

DELIMITER //

CREATE TRIGGER trg_fee_schedules_forward_only
BEFORE INSERT ON fee_schedules
FOR EACH ROW
BEGIN
    DECLARE v_newest TIMESTAMP(6);

    SELECT MAX(effective_from) INTO v_newest
      FROM fee_schedules
     WHERE tenant_code = NEW.tenant_code
       AND code        = NEW.code;

    IF v_newest IS NOT NULL AND NEW.effective_from <= v_newest THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'A fee schedule version must take effect after every version already published for its code';
    END IF;
END//

DELIMITER ;


ALTER TABLE provider_events
    ADD CONSTRAINT ck_provider_events_attempts
        CHECK (dispatch_attempts >= 0),
    ADD CONSTRAINT ck_provider_events_attributed
        CHECK (processed_at IS NULL OR tenant_code IS NOT NULL);

