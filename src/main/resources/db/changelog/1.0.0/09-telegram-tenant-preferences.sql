-- ── 1. Add preferences JSONB column to tenant table ─────────────────────────

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS preferences JSONB NOT NULL DEFAULT '{}';

COMMENT ON COLUMN tenant.preferences IS
    'Per-tenant feature flags and lightweight config (NOT credentials). '
    'Example: {"notifications": {"channel": "telegram"}, "features": {"loyalty_points": true}}';

-- GIN index for efficient JSONB key lookups
CREATE INDEX IF NOT EXISTS idx_tenant_preferences_gin
    ON tenant USING GIN (preferences);


-- ── 2. Create tenant_telegram_config table ───────────────────────────────────

CREATE TABLE IF NOT EXISTS tenant_telegram_config (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL UNIQUE,
    bot_token_encrypted TEXT            NOT NULL,
    chat_id             VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_telegram_config_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenant (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE tenant_telegram_config IS
    'Stores AES-256 encrypted Telegram bot credentials per tenant. '
    'One row per tenant (enforced by UNIQUE on tenant_id).';

COMMENT ON COLUMN tenant_telegram_config.bot_token_encrypted IS
    'AES-256-GCM encrypted Telegram bot token. Decrypted at service layer only.';

COMMENT ON COLUMN tenant_telegram_config.chat_id IS
    'Telegram chat/channel ID to send messages to. e.g. -100123456789';


-- ── 3. Index on tenant_id for fast config lookup ─────────────────────────────

CREATE INDEX IF NOT EXISTS idx_telegram_config_tenant_id
    ON tenant_telegram_config (tenant_id);


-- ── 4. Auto-update updated_at on row change ──────────────────────────────────

-- Create the trigger function (shared, reusable across tables)
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger to tenant_telegram_config
DROP TRIGGER IF EXISTS trg_telegram_config_updated_at ON tenant_telegram_config;
CREATE TRIGGER trg_telegram_config_updated_at
    BEFORE UPDATE ON tenant_telegram_config
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();
