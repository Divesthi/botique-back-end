-- ─────────────────────────────────────────────────────────────────────────────
-- tenant_whatsapp_config
--
-- Stores per-tenant WhatsApp Business Cloud API credentials.
--
-- Field-level encryption decisions (consistent with tenant_telegram_config):
--   • access_token  → AES-256-GCM encrypted (TEXT)   — Meta bearer token, secret
--   • phone_number_id   → plaintext VARCHAR            — numeric Meta resource ID
--   • waba_id           → plaintext VARCHAR            — numeric Meta account ID
--   • business_phone_number → plaintext VARCHAR        — E.164 phone number, PII
--                                                        but not a credential;
--                                                        encrypt if your data
--                                                        classification requires it
--
-- One config per tenant enforced by unique constraint on tenant_code.
-- Soft-delete via active flag — records are never hard-deleted for audit trail.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tenant_whatsapp_config (
    id                      BIGSERIAL    PRIMARY KEY,
    tenant_code             VARCHAR(25)  NOT NULL,

    -- Meta resource identifiers (plaintext — not credentials)
    phone_number_id         VARCHAR(100) NOT NULL,
    waba_id                 VARCHAR(100) NOT NULL,
    business_phone_number   VARCHAR(25)  NOT NULL,

    -- AES-256-GCM encrypted Meta access token — never store plaintext
    access_token            TEXT         NOT NULL,

    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_whatsapp_cfg_tenant
        FOREIGN KEY (tenant_code) REFERENCES tenant(code)
        ON DELETE CASCADE,

    -- One active config per tenant; enforced at DB level as a safety net
    -- even though the service layer also enforces this via upsert logic.
    CONSTRAINT uq_whatsapp_cfg_tenant
        UNIQUE (tenant_code)
);

-- Primary lookup index — dispatcher and admin reads both filter by tenant_code
CREATE INDEX IF NOT EXISTS idx_whatsapp_cfg_tenant_code
    ON tenant_whatsapp_config(tenant_code);