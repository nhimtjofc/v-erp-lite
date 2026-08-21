-- =====================================================================================
-- Where a verified provider webhook lands.
--
-- The L1 provider platform has been complete and unreachable: PaymentProviderRegistry had
-- no callers, PaymentProvider.parseWebhook had no callers, and there was no HTTP endpoint
-- for a provider to post to. This table is the durable half of wiring it up.
--
-- An inbox rather than direct dispatch, for two reasons that are not about tidiness:
--
--  * A provider retries until it gets a 2xx. If the endpoint verified an event and then
--    did work that failed, we would either return 500 and be redelivered the same event
--    forever, or return 200 and have silently dropped a payment notification. Committing
--    the event first and acting on it afterwards makes redelivery free: the unique
--    constraint below absorbs it.
--  * Redelivery is not an edge case. Stripe redelivers on any non-2xx, including a
--    timeout we caused, so the same event id arriving twice is the normal state of the
--    world rather than a fault to be defended against.
--
-- Nothing consumes this yet. Turning PAYMENT_SUCCEEDED into a posting needs a link from
-- the provider's reference back to the document it pays, and that decision belongs to
-- whoever builds the dispatch, not to the plumbing that makes it possible.
-- =====================================================================================

CREATE TABLE provider_events (
    uuid                UUID            NOT NULL PRIMARY KEY,

    -- The registry id the event arrived under, e.g. 'localstripe'.
    provider            VARCHAR(32)     NOT NULL,

    -- The provider's own id for the event (Stripe: evt_…). The idempotency key for
    -- redelivery, and the reason this table can be written to blindly.
    provider_event_id   VARCHAR(160)    NOT NULL,

    -- ProviderEvent.Signal, already normalised by the adapter. Nothing downstream should
    -- ever have to know what a provider calls its event types. Named event_signal because
    -- SIGNAL is a reserved word in MariaDB — the same statement this schema's guard triggers
    -- raise their errors with.
    event_signal        VARCHAR(32)     NOT NULL,

    -- The reference the event is about, which for anything we initiated is the reference
    -- WE minted (base.l1.platform.reference) and put in the provider's metadata.
    provider_ref        VARCHAR(160)    NULL,

    amount_minor        BIGINT          NULL,
    currency            CHAR(3)         NULL,

    occurred_at         TIMESTAMP(6)    NOT NULL,
    received_at         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- Exactly the bytes that were signed. Kept because a normalisation bug found in six
    -- months can only be re-run against the original, and because it is the evidence when
    -- a provider disputes what it sent.
    raw_payload         LONGTEXT        NOT NULL,

    -- NULLABLE, and not a tenant discriminator. A webhook arrives with no bearer token —
    -- the signature is its authentication — so at the moment of arrival we genuinely do
    -- not know which tenant it belongs to. The tenant is recoverable from provider_ref via
    -- issued_references, which is a lookup the dispatcher does, not the endpoint. Making
    -- this column NOT NULL would have forced the endpoint to guess, and a guessed tenant on
    -- a money notification is worse than an honest null.
    tenant_code         VARCHAR(64)     NULL,

    processed_at        TIMESTAMP(6)    NULL,

    CONSTRAINT uq_provider_events_id UNIQUE (provider, provider_event_id)
);

-- The dispatcher's queue: everything unprocessed, oldest first.
-- The dispatcher's queue: unprocessed events, oldest first. processed_at leads so the scan starts
-- at the NULLs and never walks the history of everything already handled.
CREATE INDEX idx_provider_events_pending ON provider_events (processed_at, received_at);

-- "What has this provider told us about this reference" — the join a reconciliation runs.
CREATE INDEX idx_provider_events_ref     ON provider_events (provider, provider_ref);

