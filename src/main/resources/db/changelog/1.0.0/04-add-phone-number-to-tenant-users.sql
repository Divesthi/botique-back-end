-- liquibase formatted sql
-- changeset bqom:04-add-phone-number-to-tenant-users

ALTER TABLE tenant_users ADD COLUMN phone_number VARCHAR(20);
