CREATE TABLE subscription_plans (
    code                    VARCHAR(32) PRIMARY KEY,
    name                    VARCHAR(128) NOT NULL,
    product_slice           VARCHAR(32) NOT NULL,
    monthly_price_minor     BIGINT NOT NULL,
    annual_price_minor      BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'VND',
    features                JSON NOT NULL,
    limits_config           JSON NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE tenant_subscriptions (
    id                      UUID PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL UNIQUE,
    plan_code               VARCHAR(32) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    billing_period          VARCHAR(16) NOT NULL,
    price_minor             BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'VND',
    started_at              TIMESTAMP NOT NULL,
    current_period_ends_at  TIMESTAMP NULL,
    trial_ends_at           TIMESTAMP NULL,
    grace_ends_at           TIMESTAMP NULL,
    cancel_at_period_end    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              UUID NULL,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              UUID NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tenant_subscriptions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(code),
    CONSTRAINT fk_tenant_subscriptions_plan
        FOREIGN KEY (plan_code) REFERENCES subscription_plans(code),
    CONSTRAINT chk_tenant_subscriptions_status
        CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    CONSTRAINT chk_tenant_subscriptions_period
        CHECK (billing_period IN ('MONTHLY', 'ANNUAL'))
);

CREATE INDEX idx_tenant_subscriptions_status_end
    ON tenant_subscriptions(status, current_period_ends_at);

INSERT INTO subscription_plans (
    code, name, product_slice, monthly_price_minor, annual_price_minor, currency, features, limits_config
) VALUES
(
    'CORE', 'Core ERP', 'CORE_ERP', 299000, 3049800, 'VND',
    JSON_OBJECT('catalog', TRUE, 'customers', TRUE, 'orders', TRUE, 'inventory', TRUE),
    JSON_OBJECT('maxUsers', 3, 'maxBusinesses', 1, 'maxProducts', 1000, 'maxChannels', 0)
),
(
    'OPERATIONS', 'Operations', 'OPERATIONS', 799000, 8149800, 'VND',
    JSON_OBJECT('catalog', TRUE, 'customers', TRUE, 'orders', TRUE, 'inventory', TRUE,
                'vouchers', TRUE, 'reports', TRUE, 'notifications', TRUE),
    JSON_OBJECT('maxUsers', 10, 'maxBusinesses', 3, 'maxProducts', 10000, 'maxChannels', 0)
),
(
    'OMNICHANNEL', 'Omnichannel & Finance', 'OMNICHANNEL_FINANCE', 1990000, 20298000, 'VND',
    JSON_OBJECT('catalog', TRUE, 'customers', TRUE, 'orders', TRUE, 'inventory', TRUE,
                'vouchers', TRUE, 'reports', TRUE, 'notifications', TRUE,
                'ecommerce', TRUE, 'onlinePayments', TRUE, 'walletPayouts', TRUE),
    JSON_OBJECT('maxUsers', 30, 'maxBusinesses', 10, 'maxProducts', 100000, 'maxChannels', 10)
);

-- Existing tenants retain access while the platform administrator sets their first commercial term.
INSERT INTO tenant_subscriptions (
    id, tenant_id, plan_code, status, billing_period, price_minor, currency, started_at
)
SELECT UUID(), t.code, 'CORE', 'ACTIVE', 'MONTHLY', 299000, 'VND', CURRENT_TIMESTAMP
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_subscriptions s WHERE s.tenant_id = t.code
);
