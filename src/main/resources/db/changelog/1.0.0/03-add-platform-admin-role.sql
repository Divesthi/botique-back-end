-- liquibase formatted sql
-- changeset bqom:03-add-platform-admin-role

-- Update CHECK constraint to include PLATFORM_ADMIN role
ALTER TABLE tenant_users DROP CONSTRAINT chk_user_role;
ALTER TABLE tenant_users ADD CONSTRAINT chk_user_role
    CHECK (role IN ('PLATFORM_ADMIN', 'TENANT_ADMIN', 'TENANT_USER'));

-- Make tenant_code nullable for PLATFORM_ADMIN (platform admin is not tied to a specific tenant)
ALTER TABLE tenant_users ALTER COLUMN tenant_code DROP NOT NULL;
