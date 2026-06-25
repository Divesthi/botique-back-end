-- ─────────────────────────────────────────────────────────────────────────────
-- tenant_instagram_config
--
-- Stores per-tenant Instagram Business Account OAuth credentials.
--
-- Design decisions:
--   • access_token       → AES-256-GCM encrypted (TEXT)   — Meta long-lived token (60d)
--   • ig_user_id         → plaintext VARCHAR               — Instagram Business Account ID
--   • ig_username        → plaintext VARCHAR               — @handle, display only
--   • token_expiry       → TIMESTAMP WITH TIME ZONE        — drives weekly refresh job
--   • active             → BOOLEAN soft-delete             — disconnect without data loss
--
-- One config per tenant enforced by unique constraint on tenant_code.
-- ON DELETE CASCADE: if a tenant is removed, their Instagram config is also removed.
--
-- The weekly refresh scheduler reads token_expiry to decide whether to call
-- the Meta token refresh endpoint. Threshold is configurable via
-- instagram.token-refresh-threshold-days (default 10).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tenant_instagram_config (
    id              BIGSERIAL               PRIMARY KEY,
    tenant_code     VARCHAR(25)             NOT NULL,

    -- Instagram Business Account details (plaintext — not credentials)
    ig_user_id      VARCHAR(100)            NOT NULL,
    ig_username     VARCHAR(100),

    -- AES-256-GCM encrypted Meta long-lived access token — never store plaintext
    access_token    TEXT                    NOT NULL,

    -- Expiry timestamp for the long-lived token (Meta issues 60-day tokens)
    -- The refresh scheduler uses this to proactively renew before expiry
    token_expiry    TIMESTAMP WITH TIME ZONE NOT NULL,

    active          BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ig_cfg_tenant
        FOREIGN KEY (tenant_code) REFERENCES tenant(code)
        ON DELETE CASCADE,

    -- One Instagram account per tenant enforced at DB level
    CONSTRAINT uq_ig_cfg_tenant
        UNIQUE (tenant_code)
);

-- Primary lookup index — used by scheduler and config reads
CREATE INDEX IF NOT EXISTS idx_ig_cfg_tenant_code
    ON tenant_instagram_config(tenant_code);

-- Scheduler index — weekly job filters on active=true and token_expiry
-- to find tokens nearing expiry without a full table scan
CREATE INDEX IF NOT EXISTS idx_ig_cfg_expiry_active
    ON tenant_instagram_config(token_expiry, active)
    WHERE active = TRUE;