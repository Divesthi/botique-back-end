-- liquibase formatted sql
-- changeset bqom:02-create-tenant-users

CREATE TABLE tenant_users (
    id            BIGSERIAL PRIMARY KEY,
    supabase_uid  VARCHAR(255) UNIQUE NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    display_name  VARCHAR(100),
    tenant_code   VARCHAR(25)  NOT NULL,
    role          VARCHAR(25)  NOT NULL DEFAULT 'TENANT_USER',
    active        BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_code) REFERENCES tenant(code),
    CONSTRAINT chk_user_role CHECK (role IN ('TENANT_ADMIN', 'TENANT_USER'))
);
