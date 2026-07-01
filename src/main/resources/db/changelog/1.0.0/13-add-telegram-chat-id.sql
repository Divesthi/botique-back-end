-- ─────────────────────────────────────────────────────────────────────────────
-- Adds telegram_chat_id to customer_details.
--
-- Purpose: enables sending order-ready-for-pickup notifications directly to
-- the customer's own Telegram chat, distinct from tenant_telegram_config's
-- chat_id (which identifies the boutique OWNER's chat, used only for the
-- measurement-share use case).
--
-- Nullable by design — populated by the (forthcoming) customer Telegram
-- linking flow, typically via a bot /start deep-link. Until a customer links,
-- this column is NULL and order-status Telegram notifications for that
-- customer are skipped (logged, not thrown) by TelegramNotificationStrategy.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE customer_details
    ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(64);

COMMENT ON COLUMN customer_details.telegram_chat_id IS
    'Customer''s Telegram chat ID, set once they link via the bot. NULL until linked.';