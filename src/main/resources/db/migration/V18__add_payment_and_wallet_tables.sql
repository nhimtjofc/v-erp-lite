CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(50) NOT NULL,
    owner_id UUID NOT NULL,
    available_balance_minor BIGINT NOT NULL DEFAULT 0,
    pending_balance_minor BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, owner_type, owner_id, currency)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    wallet_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL, -- CREDIT, DEBIT
    transaction_type VARCHAR(50) NOT NULL, -- DEPOSIT, WITHDRAWAL, COMMISSION, ESCROW_HOLD, ESCROW_RELEASE, ESCROW_REFUND, PAYOUT
    reference_type VARCHAR(50),
    reference_id UUID,
    correction_of_transaction_id UUID,
    sequence_no BIGINT NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE TABLE IF NOT EXISTS ledger_checkpoints (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    wallet_id UUID NOT NULL,
    last_transaction_id UUID NOT NULL,
    last_sequence_no BIGINT NOT NULL,
    available_balance_minor BIGINT NOT NULL,
    pending_balance_minor BIGINT NOT NULL,
    checkpointed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE TABLE IF NOT EXISTS escrow_records (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    seller_wallet_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL, -- HELD, RELEASED, REFUNDED
    release_due_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (seller_wallet_id) REFERENCES wallets(id)
);

CREATE TABLE IF NOT EXISTS payment_intents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL, -- REQUIRES_PAYMENT_METHOD, PROCESSING, SUCCEEDED, FAILED, CANCELLED
    idempotency_key VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    payment_intent_id UUID NOT NULL,
    gateway VARCHAR(50) NOT NULL, -- VNPAY, STRIPE, MOCK
    gateway_transaction_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, SUCCESS, FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(id),
    UNIQUE (tenant_id, gateway, gateway_transaction_id)
);

CREATE TABLE IF NOT EXISTS payout_requests (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    wallet_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, APPROVED, REJECTED
    bank_name VARCHAR(255) NOT NULL,
    bank_account_number VARCHAR(100) NOT NULL,
    bank_account_holder VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE TABLE IF NOT EXISTS payment_idempotency (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PROCESSING, SUCCESS, FAILED
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, idempotency_key)
);
