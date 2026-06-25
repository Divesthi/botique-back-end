-- ─────────────────────────────────────────────────────────────────────────────
-- 10-instagram-post-log.sql
--
-- Audit table for Instagram content publishing operations.
--
-- Design notes:
--   • One row per POST /instagram/posts request.
--   • Captures the overall outcome and per-image detail as JSONB so we can
--     query "which images failed most often" without schema changes.
--   • failed_images is nullable — NULL means SUCCESS (no failures).
--   • post_type is VARCHAR to accommodate future post types (REEL, STORY, etc.)
--     without an ALTER TABLE.
--   • Tenant isolation enforced by tenant_code FK + RLS (if enabled).
--   • Rows are never hard-deleted; use a retention policy / pg_partman for cleanup.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS instagram_post_log (
    id                      BIGSERIAL    PRIMARY KEY,
    tenant_code             VARCHAR(25)  NOT NULL,

    -- Outcome ── SUCCESS | PARTIAL | FAILED
    status                  VARCHAR(20)  NOT NULL
        CONSTRAINT chk_post_log_status
            CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED')),

    -- Meta post ID returned after successful publish; NULL when FAILED
    ig_post_id              VARCHAR(100),

    -- Type of post created: FEED, CAROUSEL_FEED; NULL when FAILED
    post_type               VARCHAR(30),

    -- Image counts
    total_images_requested  INT          NOT NULL,
    images_published        INT          NOT NULL DEFAULT 0,

    -- JSONB array of { fileName, index, failureStage, reason } objects
    -- for any images that failed during upload or container creation
    failed_images           JSONB,

    -- Human-readable summary (mirrors InstagramPostResponse.message)
    summary_message         TEXT,

    -- Timestamp when the async job completed server-side
    completed_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Timestamp when the request was first received (set by service before @Async)
    requested_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_post_log_tenant
        FOREIGN KEY (tenant_code) REFERENCES tenant(code)
        ON DELETE CASCADE
);

-- Primary access pattern: list recent posts for a tenant
CREATE INDEX IF NOT EXISTS idx_post_log_tenant_requested
    ON instagram_post_log(tenant_code, requested_at DESC);

-- Allow fast lookup of failed/partial posts for monitoring
CREATE INDEX IF NOT EXISTS idx_post_log_status
    ON instagram_post_log(status)
    WHERE status != 'SUCCESS';

COMMENT ON TABLE instagram_post_log IS
    'Audit log for every Instagram content publishing request. '
    'One row per POST /instagram/posts call. Retains partial-failure detail in failed_images JSONB.';