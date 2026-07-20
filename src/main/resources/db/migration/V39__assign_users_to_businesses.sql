ALTER TABLE users ADD COLUMN IF NOT EXISTS business_id UUID NULL;

UPDATE users u
JOIN role r ON r.id = u.role_id AND r.name = 'businessOwner'
JOIN business b ON b.created_by = u.id AND b.tenant_id = u.tenant_id
SET u.business_id = b.id
WHERE u.business_id IS NULL;

UPDATE users operator_user
JOIN role operator_role
    ON operator_role.id = operator_user.role_id
    AND operator_role.name = 'operator'
JOIN (
    SELECT tenant_id, business_id
    FROM (
        SELECT owner_user.tenant_id,
               b.id AS business_id,
               ROW_NUMBER() OVER (
                   PARTITION BY owner_user.tenant_id
                   ORDER BY b.created_at, b.id
               ) AS business_rank
        FROM users owner_user
        JOIN role owner_role
            ON owner_role.id = owner_user.role_id
            AND owner_role.name = 'businessOwner'
        JOIN business b
            ON b.created_by = owner_user.id
            AND b.tenant_id = owner_user.tenant_id
    ) ranked_businesses
    WHERE business_rank = 1
) owner_business ON owner_business.tenant_id = operator_user.tenant_id
SET operator_user.business_id = owner_business.business_id
WHERE operator_user.business_id IS NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_business FOREIGN KEY (business_id) REFERENCES business(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_users_business ON users (tenant_id, business_id);
