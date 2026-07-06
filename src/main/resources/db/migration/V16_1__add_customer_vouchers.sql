CREATE TABLE customer_voucher (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    voucher_id      UUID NOT NULL,
    collected_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_used         BOOLEAN NOT NULL DEFAULT FALSE,
    tenant_id       VARCHAR(64) NOT NULL,
    CONSTRAINT fk_cv_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_cv_voucher  FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT uq_cv_customer_voucher UNIQUE (customer_id, voucher_id)
);

CREATE INDEX idx_cv_customer ON customer_voucher(customer_id);
CREATE INDEX idx_cv_voucher  ON customer_voucher(voucher_id);
CREATE INDEX idx_cv_tenant   ON customer_voucher(tenant_id);
