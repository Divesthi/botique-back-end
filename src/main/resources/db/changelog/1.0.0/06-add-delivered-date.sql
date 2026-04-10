-- liquibase formatted sql
-- changeset bqom:06-add-delivered-date

ALTER TABLE order_details ADD COLUMN IF NOT EXISTS delivered_date TIMESTAMP WITH TIME ZONE;
