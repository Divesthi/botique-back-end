-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Add `preferences` JSONB column to the existing `tenant` table.
--    Default is an empty JSON object so existing rows get a valid value.
--    The column is nullable-safe: all reads should treat NULL the same as '{}'.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS preferences JSONB NOT NULL DEFAULT '{}';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Create `tenant_telegram_config` table.
--
--    Design notes:
--    • `bot_token` and `chat_id` are stored AES-256-GCM encrypted at the
--      service layer — the DB never sees plaintext credentials.
--    • `tenant_code` is the FK to `tenant(code)` — consistent with every
--      other table in the schema.
--    • `active` flag lets admins disable a config without deleting it.
--    • `created_at` / `updated_at` for auditing.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tenant_telegram_config (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_code VARCHAR(25)  NOT NULL,
    bot_token   TEXT         NOT NULL,          -- AES-256-GCM encrypted
    chat_id     VARCHAR(255) NOT NULL,          -- AES-256-GCM encrypted
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_telegram_cfg_tenant
        FOREIGN KEY (tenant_code) REFERENCES tenant(code)
        ON DELETE CASCADE,

    CONSTRAINT uq_telegram_cfg_tenant
        UNIQUE (tenant_code)               -- one active config per tenant
);

-- Index for the most common lookup pattern (by tenant_code)
CREATE INDEX IF NOT EXISTS idx_telegram_cfg_tenant_code
    ON tenant_telegram_config(tenant_code);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Seed default notification preference on existing tenants so that the
--    NotificationDispatcher always finds a valid channel key.
--    "whatsapp" preserves backward-compatibility with existing behaviour.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE tenant
SET preferences = preferences || '{"notifications": {"channel": "telegram"}}'::jsonb
WHERE preferences -> 'notifications' IS NULL;