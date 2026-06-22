CREATE TABLE tenant (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    owner_id    UUID,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE role (
    id          UUID PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255)
);

INSERT INTO role (id, name, description) VALUES
    (UUID(), 'admin', 'Full system access'),
    (UUID(), 'operator', 'Operational access'),
    (UUID(), 'businessOwner', 'Tenant owner');

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    first_name      VARCHAR(128),
    last_name       VARCHAR(128),
    keycloak_id     VARCHAR(255) NOT NULL,
    role_id         UUID REFERENCES role(id),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP NULL DEFAULT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID,
    UNIQUE (tenant_id, email),
    UNIQUE (tenant_id, keycloak_id)
);

CREATE TABLE customer (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    first_name      VARCHAR(128),
    last_name       VARCHAR(128),
    email           VARCHAR(255),
    phone           VARCHAR(32),
    address         TEXT,
    birth_date      DATE,
    notes           TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID
);

CREATE TABLE business (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    address         TEXT,
    phone           VARCHAR(32),
    email           VARCHAR(255),
    tax_id          VARCHAR(64),
    logo_url        VARCHAR(512),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID
);
