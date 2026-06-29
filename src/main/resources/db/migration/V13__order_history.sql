-- Order History: track every status change on an order for the Timeline UI
CREATE TABLE order_history (
    id          UUID         PRIMARY KEY,
    order_id    UUID         NOT NULL,
    tenant_id   VARCHAR(64)  NOT NULL,
    old_status  VARCHAR(50),
    new_status  VARCHAR(50)  NOT NULL,
    note        TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_order_history_order ON order_history(order_id);
CREATE INDEX idx_order_history_tenant ON order_history(tenant_id);
