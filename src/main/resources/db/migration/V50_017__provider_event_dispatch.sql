-- =====================================================================================
-- Room to record why an event was not acted on.
--
-- provider_events could say "stored" and "processed" and nothing in between, which is the
-- state most inbound events will actually be in: verified, kept, and not actionable — a
-- payout.created that carries no instruction, a signal no use case maps to yet, a reference
-- that names a document this service has never issued.
--
-- Leaving those as plain unprocessed rows would make the queue indistinguishable from a
-- backlog nobody is draining. Recording the reason turns "1,412 unprocessed" into a list
-- somebody can read, and the row stays unprocessed on purpose: it is evidence, not an
-- error, and if a later release learns how to act on it the raw payload is still there.
-- =====================================================================================

ALTER TABLE provider_events
    ADD COLUMN dispatch_note     TEXT NULL AFTER processed_at,
    -- Counts tries, not failures. A row climbing this without ever being processed is the
    -- signal that something is being retried forever rather than quietly ignored once.
    ADD COLUMN dispatch_attempts INT  NOT NULL DEFAULT 0 AFTER dispatch_note;

