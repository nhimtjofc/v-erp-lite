-- The application has one marketplace owner. All tenant businesses and their
-- orders must therefore be visible to owner@v-erp.com.
UPDATE business b
JOIN users owner_user
    ON owner_user.tenant_id = b.tenant_id
   AND owner_user.email = 'owner@v-erp.com'
SET b.created_by = owner_user.id
WHERE b.tenant_id = 'v-erp-app';

-- Retain a default business for endpoints that create a new product or resolve
-- the owner's primary seller wallet.
UPDATE users owner_user
SET owner_user.business_id = 'b1000000-0000-0000-0000-000000000001'
WHERE owner_user.tenant_id = 'v-erp-app'
  AND owner_user.email = 'owner@v-erp.com';

-- V52 temporarily introduced separate owners for sample stores. They are not
-- valid marketplace owners; deactivate them and remove their business access.
UPDATE users retired_owner
JOIN role customer_role ON customer_role.name = 'customer'
SET retired_owner.role_id = customer_role.id,
    retired_owner.business_id = NULL,
    retired_owner.is_active = FALSE
WHERE retired_owner.tenant_id = 'v-erp-app'
  AND retired_owner.email IN ('owner2@v-erp.com', 'owner3@v-erp.com');
