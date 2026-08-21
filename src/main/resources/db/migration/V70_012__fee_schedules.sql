-- =====================================================================================
-- Versioned fee schedules, so a rate can be looked up rather than remembered.
--
-- Until now the commission arrived on CaptureRequest.SellerSplit already worked out, and
-- this service took the caller's word for it. The amount lands on revenue:fee, so what was
-- charged is recoverable; the rate that produced it is recorded nowhere. Six months later
-- "why was I charged 8.5% on that order" has no answer here, and worse, no way to tell
-- whether the number was ever right.
--
-- Two properties are what make a schedule worth having, and both come from versioning:
--
--  * A rate is a fact about a period, not a constant. Changing a rate must not restate
--    what was charged before the change, so a schedule is never edited — a new version is
--    added with its own effective_from, and the old one keeps its window. That is the same
--    reason journal_entries is append-only, applied to pricing.
--  * The version is stamped on the entry. Given a posting you can name the schedule that
--    produced it and recompute the figure, without needing to know what the table happened
--    to contain at the time.
--
-- Deliberately not a rules engine. A rate and a fixed amount per version covers what this
-- service actually needs to verify; anything conditional belongs to whoever decides
-- pricing, and would only be re-implemented incorrectly here.
-- =====================================================================================

CREATE TABLE fee_schedules (
    uuid            UUID            NOT NULL PRIMARY KEY,
    tenant_code     VARCHAR(64)     NOT NULL,

    -- Names the arrangement, not the number: 'standard', 'partner-tier-2'. Stable across
    -- versions, which is what lets a caller quote a schedule without quoting a rate.
    code            VARCHAR(64)     NOT NULL,

    -- Monotonic per code. The stamp that goes on the entry.
    version         INT             NOT NULL,

    -- Half-open [effective_from, effective_to). NULL effective_to means "in force". An
    -- instant rather than a date because a rate change at a known moment must not be
    -- ambiguous for anything captured that day.
    effective_from  TIMESTAMP(6)    NOT NULL,
    effective_to    TIMESTAMP(6)    NULL,

    -- rate applied to the gross, in basis points, plus a flat component. Both may be zero;
    -- a schedule charging nothing is a real arrangement and needs to be expressible.
    basis_points    INT             NOT NULL DEFAULT 0,
    fixed_minor     BIGINT          NOT NULL DEFAULT 0,

    -- The currency the fixed component is in. NULL when fixed_minor is 0, so a pure
    -- percentage schedule is not forced to pick one.
    currency        CHAR(3)         NULL,

    note            TEXT            NULL,

    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      UUID            NULL,

    CONSTRAINT uq_fee_schedules_version   UNIQUE (tenant_code, code, version),
    CONSTRAINT ck_fee_schedules_bps       CHECK (basis_points >= 0),
    CONSTRAINT ck_fee_schedules_fixed     CHECK (fixed_minor >= 0),
    CONSTRAINT ck_fee_schedules_window    CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_fee_schedules_currency  CHECK (fixed_minor = 0 OR currency IS NOT NULL)
);

-- "Which version was in force when." Asked once per capture that quotes a schedule, so it is on
-- the settlement path rather than a reporting one.
CREATE INDEX idx_fee_schedules_lookup ON fee_schedules (tenant_code, code, effective_from);


DELIMITER //

-- -------------------------------------------------------------------------------------
-- A schedule version is a historical fact once anything has been priced against it.
--
-- Only the open end may move: closing a version's window is how the next one starts, and
-- that is the one edit that does not restate the past. Changing a rate, a fixed amount or
-- a start instant would silently rewrite what was charged under it.
-- -------------------------------------------------------------------------------------
CREATE TRIGGER trg_fee_schedules_immutable
BEFORE UPDATE ON fee_schedules
FOR EACH ROW
BEGIN
    IF NOT (NEW.basis_points   <=> OLD.basis_points)
    OR NOT (NEW.fixed_minor    <=> OLD.fixed_minor)
    OR NOT (NEW.currency       <=> OLD.currency)
    OR NOT (NEW.effective_from <=> OLD.effective_from)
    OR NOT (NEW.code           <=> OLD.code)
    OR NOT (NEW.version        <=> OLD.version)
    OR NOT (NEW.tenant_code    <=> OLD.tenant_code) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'A fee schedule version is immutable; supersede it with a new version instead';
    END IF;

    IF OLD.effective_to IS NOT NULL AND NOT (NEW.effective_to <=> OLD.effective_to) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'A fee schedule version that has already been closed may not be reopened or moved';
    END IF;
END//

DELIMITER ;


-- ---------------------------------------------------------------------------------------
-- Versions whose windows overlap, which would make "the rate at time T" ambiguous.
--
-- A view rather than a constraint: MariaDB cannot express "no two rows for this code
-- overlap in time" as a CHECK, and enforcing it in a trigger would need a table scan on
-- every insert. LedgerInvariantChecker reads this instead, so an overlap is reported rather
-- than discovered by two captures of the same order being priced differently.
-- ---------------------------------------------------------------------------------------
CREATE VIEW v_fee_schedule_overlaps AS
SELECT
    a.tenant_code,
    a.code,
    a.version        AS version_a,
    b.version        AS version_b,
    a.effective_from AS from_a,
    a.effective_to   AS to_a,
    b.effective_from AS from_b,
    b.effective_to   AS to_b
FROM fee_schedules a
JOIN fee_schedules b
  ON b.tenant_code = a.tenant_code
 AND b.code        = a.code
 AND b.version     > a.version
 AND a.effective_from < COALESCE(b.effective_to, '9999-12-31 23:59:59')
 AND b.effective_from < COALESCE(a.effective_to, '9999-12-31 23:59:59');

