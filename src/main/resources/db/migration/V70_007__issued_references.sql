-- =====================================================================================
-- References we issue and stamp on transactions we initiate.
--
-- Until now every reference in this service was somebody else's — a provider's, a bank's —
-- handed in by the caller. That leaves the outbound direction unmatched: when a payout is
-- confirmed, returned, or shows up on a statement, nothing in the message identifies which
-- payout it was except an amount and a date, and matching on those is what produces
-- confident wrong answers.
--
-- The obvious identifier does not fit. document_id is a UUID v7, which is 36 characters,
-- and the memo field it would have to travel in is 12 characters in New Zealand, ~19 on
-- VietQR, 18 on UK Faster Payments and Australian BECS. Encoding the UUID does not rescue
-- it: Crockford Base32 of 128 bits is 26 characters and Base62 is 22, both of which fail
-- every one of those rails. So the reference is a separate, short, issued identifier, and
-- this table is the registry that maps it back.
--
-- Uniqueness on `reference` is GLOBAL, not per tenant. A string arriving in a bank memo
-- carries no tenant, and a reference that means two things is not an identifier.
-- =====================================================================================

CREATE TABLE issued_references (
    uuid            UUID            NOT NULL PRIMARY KEY,
    tenant_code     VARCHAR(64)     NOT NULL,

    -- Deliberately wider than the 12 characters generation 1 emits: a later generation
    -- must not require a schema change on a table this size. The index cost is negligible.
    reference       VARCHAR(35)     NOT NULL,

    -- Opaque to this layer, exactly as on journal_entries. The registry maps a string to a
    -- document; what a PAYOUT is, is the caller's business.
    document_type   VARCHAR(32)     NOT NULL,
    document_id     UUID            NOT NULL,

    created_at      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      UUID            NULL,

    -- Global: a memo string has no tenant to disambiguate it with.
    CONSTRAINT uq_issued_references_ref UNIQUE (reference),
    -- One reference per document, so issuing is idempotent and a retried payout does not
    -- mint a second identifier for the same obligation.
    CONSTRAINT uq_issued_references_doc UNIQUE (tenant_code, document_type, document_id)
);

-- "Which reference did we already mint for this document." The idempotency read: issuing twice for
-- one payout must return the first reference, because the first one may already be on a transfer.
CREATE INDEX idx_issued_references_doc ON issued_references (tenant_code, document_type, document_id);

