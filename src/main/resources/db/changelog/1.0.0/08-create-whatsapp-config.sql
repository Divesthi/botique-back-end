CREATE TABLE tenant_whatsapp_config (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_code           VARCHAR(25)  NOT NULL UNIQUE,
    phone_number_id       VARCHAR(50)  NOT NULL,
    waba_id               VARCHAR(50)  NOT NULL,
    access_token          TEXT         NOT NULL,   -- AES encrypted
    business_phone_number VARCHAR(20)  NOT NULL,
    is_active             BOOLEAN      DEFAULT TRUE,
    created_at            TIMESTAMP    DEFAULT NOW(),
    updated_at            TIMESTAMP    DEFAULT NOW(),
    FOREIGN KEY (tenant_code) REFERENCES tenant(code)
);