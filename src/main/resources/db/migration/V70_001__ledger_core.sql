-- =====================================================================================
-- v-payment — counterparty ledger core
--
-- Design notes, because the shape here is a deliberate correction of what came before:
--
--  * Balances are SIGNED and DEBIT-POSITIVE. A credit balance is negative. This is what
--    lets one account express both "we owe this partner" and "this partner owes us",
--    which the wallet ledger this replaces structurally could not (Teardown F-04).
--
--  * Every movement is a balanced journal ENTRY of two or more LINES. The old ledger
--    wrote one row against one account and stopped, so no counter-account existed and no
--    trial balance was possible (Teardown F-02/§03). Balance is enforced in the database,
--    not in Java, so no future code path can write half an entry.
--
--  * Account balances are maintained ONLY by ledger_post_entry(). A trigger rejects any
--    other write to balance_minor. The previous system relied on every caller politely
--    routing through one service class; here the chokepoint is the database.
--
--  * Each line snapshots the account balance before and after it, plus the per-account
--    sequence number it consumed (idea borrowed from pgledger). Historical balances are a
--    lookup rather than a fold, and the ledger_checkpoints table — which stored a copy of
--    a number and then "verified" it against itself — is not needed at all.
-- =====================================================================================

CREATE TABLE ledger_accounts (
    uuid                    UUID            NOT NULL PRIMARY KEY,
    tenant_code             VARCHAR(64)     NOT NULL,
    code                    VARCHAR(160)    NOT NULL,

    -- SUBLEDGER | CONTROL | REVENUE | EXPENSE | EQUITY
    account_kind            VARCHAR(16)     NOT NULL,
    -- DR | CR — the side this account normally carries; presentation only, never enforcement
    normal_side             CHAR(2)         NOT NULL,

    -- Counterparty identity for SUBLEDGER accounts. Free-form owner_type so SELLER,
    -- BUYER, SUPPLIER, PARTNER and TENANT all work without a migration.
    owner_type              VARCHAR(32)     NULL,
    owner_id                UUID            NULL,

    -- Which CONTROL account this sub-ledger rolls up to. The sub-ledger/control tie-out
    -- is the standard month-end close check and is asserted by LedgerInvariantChecker.
    control_account_uuid    UUID            NULL,

    -- Provider or bank this control account mirrors, e.g. 'vnpay', 'bank:operating'.
    external_ref            VARCHAR(120)    NULL,

    currency                CHAR(3)         NOT NULL,

    -- Balance policy. Two flags rather than one: allow_credit_balance = FALSE pins an
    -- account to the debit side, which is how a revenue or expense account is stopped
    -- from drifting the wrong way. Default is a credit-only account (a payable).
    --
    -- Setting BOTH to FALSE is legal and meaningful: it means the balance must be zero,
    -- which is how an account is retired without deleting its history. Any entry that
    -- would leave it non-zero is rejected, while one that zeroes it out still posts.
    -- (This is pgledger's account-freezing idiom; `frozen` below is the stronger form
    -- that refuses to be touched at all, even by a zero-sum entry.)
    allow_debit_balance     BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_credit_balance    BOOLEAN         NOT NULL DEFAULT TRUE,
    -- Ceiling on how far debit (i.e. how much the counterparty may owe us) the balance
    -- may run. Only consulted when allow_debit_balance is TRUE.
    credit_limit_minor      BIGINT          NOT NULL DEFAULT 0,

    payment_terms_days      SMALLINT        NULL,
    frozen                  BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Maintained exclusively by ledger_post_entry(). Debit-positive.
    balance_minor           BIGINT          NOT NULL DEFAULT 0,
    -- Per-account monotonic counter. A UNIQUE constraint on journal_lines makes gaps and
    -- duplicates impossible, rather than relying on SELECT MAX(seq)+1 under a row lock
    -- (Teardown F-17).
    entry_seq               BIGINT          NOT NULL DEFAULT 0,

    created_at              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by              UUID            NULL,
    updated_at              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by              UUID            NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT uq_ledger_accounts_code    UNIQUE (tenant_code, code, currency),
    CONSTRAINT ck_ledger_accounts_kind    CHECK (account_kind IN ('SUBLEDGER','CONTROL','REVENUE','EXPENSE','EQUITY')),
    CONSTRAINT ck_ledger_accounts_side    CHECK (normal_side IN ('DR','CR')),
    CONSTRAINT ck_ledger_accounts_limit   CHECK (credit_limit_minor >= 0),
    CONSTRAINT fk_ledger_accounts_control FOREIGN KEY (control_account_uuid) REFERENCES ledger_accounts (uuid)
);

-- Listing a tenant's accounts of one kind, which is the chart of accounts as a person reads it.
CREATE INDEX idx_ledger_accounts_tenant       ON ledger_accounts (tenant_code, account_kind);
-- "Find this counterparty's account in this currency." Every settlement flow does this before it
-- can post, so it is the hottest read in the service and the reason owner_id is indexed at all.
CREATE INDEX idx_ledger_accounts_owner        ON ledger_accounts (tenant_code, owner_type, owner_id, currency);
-- Walks the other way: from a control account to the sub-ledgers beneath it, which is how
-- v_control_rollup totals children against their parent.
CREATE INDEX idx_ledger_accounts_control      ON ledger_accounts (control_account_uuid);


-- One row per business event. Idempotency lives here: the unique key means a replayed
-- webhook or a double-clicked button cannot post the same event twice, enforced by the
-- database rather than by a check-then-insert race.
CREATE TABLE journal_entries (
    uuid                    UUID            NOT NULL PRIMARY KEY,
    tenant_code             VARCHAR(64)     NOT NULL,

    -- ORDER_SETTLED, REFUND_ISSUED, PAYOUT_INSTRUCTED, PAYOUT_CONFIRMED, PAYOUT_RETURNED…
    event_type              VARCHAR(48)     NOT NULL,

    document_type           VARCHAR(32)     NULL,
    document_id             UUID            NULL,

    -- Join key for provider settlement files and bank statements.
    provider                VARCHAR(32)     NULL,
    provider_ref            VARCHAR(160)    NULL,

    idempotency_key         VARCHAR(200)    NOT NULL,

    -- When the event happened, which is not when we recorded it. Reversals post into the
    -- period of the entry they reverse.
    effective_at            TIMESTAMP(6)    NOT NULL,

    -- Corrections reverse; nothing is ever edited.
    reverses_entry_uuid     UUID            NULL,

    memo                    TEXT            NULL,

    -- Free-form business context, and the spine of reconciliation. Grouping lines by a
    -- key carried here — payment id, settlement batch, order — and asserting the group
    -- sums to zero detects a whole class of errors that no fixed control account can:
    -- a payment settled short by the provider's fee nets non-zero and names itself.
    -- (Borrowed from pgledger's metadata-rollup pattern.)
    metadata                JSON            NULL,

    created_at              TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by              UUID            NULL,

    CONSTRAINT uq_journal_entries_idem     UNIQUE (tenant_code, idempotency_key),
    CONSTRAINT fk_journal_entries_reverses FOREIGN KEY (reverses_entry_uuid) REFERENCES journal_entries (uuid)
);

-- Everything that happened in a period. Dated by when the event happened, not when it was
-- recorded, so a period query returns the same rows however late something was booked.
CREATE INDEX idx_journal_entries_tenant_eff  ON journal_entries (tenant_code, effective_at);
-- "What did we post for this order / payout / dispute." The audit trail read, from the caller's
-- own identifier back into the ledger.
CREATE INDEX idx_journal_entries_document    ON journal_entries (tenant_code, document_type, document_id);
-- The join key for a provider settlement file or a bank statement: their reference, our entry.
-- Reconciliation is only cheap because this exists; without it every match is a scan.
CREATE INDEX idx_journal_entries_provider    ON journal_entries (tenant_code, provider, provider_ref);


-- Two or more per entry, summing to zero per currency.
CREATE TABLE journal_lines (
    uuid                        UUID            NOT NULL PRIMARY KEY,
    tenant_code                 VARCHAR(64)     NOT NULL,
    entry_uuid                  UUID            NOT NULL,
    account_uuid                UUID            NOT NULL,

    direction                   CHAR(2)         NOT NULL,
    amount_minor                BIGINT          NOT NULL,
    -- Redundant by construction and deliberately so: it lets the trial balance be a plain
    -- SUM() with no CASE, and the CHECK below keeps it honest.
    signed_minor                BIGINT          NOT NULL,
    currency                    CHAR(3)         NOT NULL,

    -- Present on open items. A due date on the line is what turns aging from a feature
    -- into a query (Teardown F-05).
    due_date                    DATE            NULL,

    line_no                     SMALLINT        NOT NULL,

    -- Balance snapshots, in the spirit of pgledger: a statement of account renders from
    -- these directly, and any drift between the snapshot chain and ledger_accounts is a
    -- real detectable error rather than a copy compared against itself.
    account_prev_balance_minor  BIGINT          NOT NULL,
    account_new_balance_minor   BIGINT          NOT NULL,
    account_entry_seq           BIGINT          NOT NULL,

    memo                        TEXT            NULL,

    created_at                  TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by                  UUID            NULL,

    CONSTRAINT uq_journal_lines_entry_line  UNIQUE (entry_uuid, line_no),
    CONSTRAINT uq_journal_lines_account_seq UNIQUE (account_uuid, account_entry_seq),
    CONSTRAINT ck_journal_lines_direction   CHECK (direction IN ('DR','CR')),
    CONSTRAINT ck_journal_lines_amount      CHECK (amount_minor > 0),
    CONSTRAINT ck_journal_lines_signed      CHECK (signed_minor = CASE WHEN direction = 'DR' THEN amount_minor ELSE -amount_minor END),
    CONSTRAINT ck_journal_lines_snapshot    CHECK (account_new_balance_minor = account_prev_balance_minor + signed_minor),
    CONSTRAINT fk_journal_lines_entry       FOREIGN KEY (entry_uuid)   REFERENCES journal_entries (uuid),
    CONSTRAINT fk_journal_lines_account     FOREIGN KEY (account_uuid) REFERENCES ledger_accounts (uuid)
);

-- A statement of account: one account's lines in the order they were posted. Also the access path
-- for the balance snapshot chain, since the snapshots are ordered by exactly this sequence.
CREATE INDEX idx_journal_lines_account_seq ON journal_lines (account_uuid, account_entry_seq);
-- Aging. The due date lives on the line rather than being derived at report time, so the buckets
-- are a range scan instead of a fold over everything with a date computed per row.
CREATE INDEX idx_journal_lines_tenant_due  ON journal_lines (tenant_code, due_date);
-- The lines of one entry, for rendering it and for the "does this header have its lines" check.
CREATE INDEX idx_journal_lines_entry       ON journal_lines (entry_uuid);


-- Open-item accounting: which payment settled which invoice. Without this a partner who
-- pays 8,000,000 against four open invoices cannot be told which are now closed, and no
-- aging report can be trusted (Teardown F-06).
CREATE TABLE item_allocations (
    uuid                UUID            NOT NULL PRIMARY KEY,
    tenant_code         VARCHAR(64)     NOT NULL,
    open_line_uuid      UUID            NOT NULL,
    settle_line_uuid    UUID            NOT NULL,
    amount_minor        BIGINT          NOT NULL,
    allocated_at        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    allocated_by        UUID            NULL,

    CONSTRAINT uq_item_allocations_pair   UNIQUE (open_line_uuid, settle_line_uuid),
    CONSTRAINT ck_item_allocations_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_item_allocations_self   CHECK (open_line_uuid <> settle_line_uuid),
    CONSTRAINT fk_item_allocations_open   FOREIGN KEY (open_line_uuid)   REFERENCES journal_lines (uuid),
    CONSTRAINT fk_item_allocations_settle FOREIGN KEY (settle_line_uuid) REFERENCES journal_lines (uuid)
);

-- Both directions of "what has already been allocated against this line". A line can appear as
-- either side of a pair, so answering it needs both indexes — and it is asked on every allocation,
-- because that sum is what bounds the next one.
CREATE INDEX idx_item_allocations_open   ON item_allocations (open_line_uuid);
CREATE INDEX idx_item_allocations_settle ON item_allocations (settle_line_uuid);

