CREATE TABLE wallet_topups (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    wallet_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_qr_code_base64 TEXT,
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL,
    updated_by UUID,
    CONSTRAINT fk_wallet_topup_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);
