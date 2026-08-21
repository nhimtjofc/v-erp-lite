ALTER TABLE tenant_subscriptions
    DROP FOREIGN KEY fk_tenant_subscriptions_plan;

UPDATE subscription_plans
SET name = CASE code
        WHEN 'CORE' THEN 'Base'
        WHEN 'OPERATIONS' THEN 'Plus'
        WHEN 'OMNICHANNEL' THEN 'Premium'
        ELSE name
    END,
    product_slice = CASE code
        WHEN 'CORE' THEN 'BASE_ERP'
        WHEN 'OPERATIONS' THEN 'PLUS_OPERATIONS'
        WHEN 'OMNICHANNEL' THEN 'PREMIUM_OMNICHANNEL'
        ELSE product_slice
    END,
    code = CASE code
        WHEN 'CORE' THEN 'BASE'
        WHEN 'OPERATIONS' THEN 'PLUS'
        WHEN 'OMNICHANNEL' THEN 'PREMIUM'
        ELSE code
    END;

UPDATE tenant_subscriptions
SET plan_code = CASE plan_code
    WHEN 'CORE' THEN 'BASE'
    WHEN 'OPERATIONS' THEN 'PLUS'
    WHEN 'OMNICHANNEL' THEN 'PREMIUM'
    ELSE plan_code
END;

ALTER TABLE tenant_subscriptions
    ADD CONSTRAINT fk_tenant_subscriptions_plan
    FOREIGN KEY (plan_code) REFERENCES subscription_plans(code);