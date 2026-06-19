package com.dreamworks.bqom.service.notification;

import com.dreamworks.bqom.model.notification.NotificationChannel;
import com.dreamworks.bqom.model.notification.NotificationMessage;

/**
 * Strategy contract for sending notifications via a specific channel.
 *
 * <h2>Extension guide</h2>
 * To add a new channel (e.g., email, Slack):
 * <ol>
 *   <li>Add a constant to {@link NotificationChannel}.</li>
 *   <li>Create a new {@code @Service} that implements this interface.</li>
 *   <li>Return the matching {@link NotificationChannel} from {@link #channel()}.</li>
 *   <li>{@link NotificationDispatcher} picks it up automatically via the
 *       Spring bean registry — no changes needed to the dispatcher.</li>
 * </ol>
 *
 * <p>Implementations are expected to:
 * <ul>
 *   <li>Throw a {@link NotificationException} on unrecoverable errors so the
 *       dispatcher can log them with full context.</li>
 *   <li>Be stateless — credentials are fetched inside {@code send()} to allow
 *       config changes to take effect without a restart.</li>
 * </ul>
 */
public interface NotificationStrategy {

    /**
     * @return the channel this strategy handles; used by the dispatcher to
     *         build its registry map at startup.
     */
    NotificationChannel channel();

    /**
     * Send {@code message} via this strategy's channel.
     *
     * @param message the notification payload (never null)
     * @throws NotificationException if the message could not be delivered
     */
    void sendMeasurement(NotificationMessage message);

    void sendOrderStatus(NotificationMessage message);

    // ── nested exception ──────────────────────────────────────────────────────

    /**
     * Unchecked exception thrown by strategies on delivery failure.
     * Carries the originating {@code tenantCode} for structured logging.
     */
    class NotificationException extends RuntimeException {

        private final String tenantCode;

        public NotificationException(String tenantCode, String message, Throwable cause) {
            super(message, cause);
            this.tenantCode = tenantCode;
        }

        public NotificationException(String tenantCode, String message) {
            super(message);
            this.tenantCode = tenantCode;
        }

        public String getTenantCode() {
            return tenantCode;
        }
    }
}