package com.dreamworks.bqom.service.notification;

import com.dreamworks.bqom.model.notification.NotificationChannel;
import com.dreamworks.bqom.model.notification.NotificationMessage;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.entity.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asynchronous notification dispatcher using the Strategy Pattern.
 *
 * <h2>Registry-based routing — no if/else chains</h2>
 * <p>At startup, Spring injects ALL beans implementing {@link NotificationStrategy}
 * into a {@code List}. The constructor converts this into a
 * {@code Map<NotificationChannel, NotificationStrategy>} keyed by channel.
 * Routing is then a single O(1) map lookup — adding a new channel never
 * requires changing this class.
 *
 * <h2>Async behaviour</h2>
 * <p>{@link #dispatchMeasurement(NotificationMessage)} is annotated {@code @Async}.
 * It runs on Spring's default {@code SimpleAsyncTaskExecutor} (fire-and-forget).
 * The calling thread returns immediately; notification delivery is non-blocking.
 *
 * <p>If you need a bounded thread pool (recommended for high-throughput
 * production deployments), configure a {@code ThreadPoolTaskExecutor} bean
 * named {@code "taskExecutor"} in a {@code @Configuration} class and set
 * {@code spring.task.execution.pool.*} properties.
 *
 * <h2>Failure handling</h2>
 * <p>Failures are logged with structured context ({@code tenantCode},
 * {@code channel}, {@code messagePreview}) and then swallowed so the async
 * thread does not propagate an uncaught exception. This is intentional for
 * fire-and-forget semantics — callers are never blocked by notification
 * failures. Monitor {@code ERROR} log entries with the {@code [Dispatcher]}
 * prefix for alerting.
 *
 * <h2>Preferences lookup</h2>
 * <p>The channel is read from:
 * {@code tenant.preferences -> 'notifications' -> 'channel'}.
 * If the key is absent or the value is invalid, the dispatcher logs a clear
 * error with actionable guidance (set preferences via PATCH endpoint).
 */
@Service
@Slf4j
public class NotificationDispatcher {

    private static final String PREF_KEY_NOTIFICATIONS = "notifications";
    private static final String PREF_KEY_CHANNEL       = "channel";

    /** Maximum characters of the message body logged in error/debug entries. */
    private static final int LOG_PREVIEW_LENGTH = 80;

    private final TenantRepository tenantRepository;

    /**
     * Registry: channel → strategy.
     * Built once at startup from all {@link NotificationStrategy} beans.
     * Thread-safe after construction (unmodifiable map).
     */
    private final Map<NotificationChannel, NotificationStrategy> strategyRegistry;

    /**
     * Spring injects all {@link NotificationStrategy} implementations.
     * The registry map is built here — immutable after construction.
     *
     * @param strategies all strategy beans discovered by Spring
     * @param tenantRepository used to read tenant preferences
     */
    @Autowired
    public NotificationDispatcher(List<NotificationStrategy> strategies,
                                  TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
        this.strategyRegistry = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationStrategy::channel,
                        Function.identity()
                ));
        log.info("[Dispatcher] Registered notification strategies: {}",
                strategyRegistry.keySet());
    }

    private NotificationStrategy getStrategy(NotificationMessage notificationMessage) {
        String tenantCode = notificationMessage.getTenantCode();
        // ── 1. Load tenant ─────────────────────────────────────────────────
        Tenant tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found for tenantCode: " + tenantCode));

        // ── 2. Read channel preference ─────────────────────────────────────
        NotificationChannel channel = resolveChannel(tenant, tenantCode);

        log.info("[Dispatcher] Routing notification for tenant={} via channel={}",
                tenantCode, channel);

        // ── 3. Look up strategy — O(1), no if/else ────────────────────────
        NotificationStrategy strategy = strategyRegistry.get(channel);
        if (strategy == null) {
            // Registry miss: channel is registered in the enum but has no
            // strategy bean — this is a programming error caught at runtime.
            log.error("[Dispatcher] No strategy registered for channel={} " +
                            "(tenant={}). Available channels: {}",
                    channel, tenantCode, strategyRegistry.keySet());
        }

        return strategy;
    }

    /**
     * Dispatches a notification asynchronously (fire-and-forget).
     *
     * <p>Steps:
     * <ol>
     *   <li>Load tenant preferences from DB.</li>
     *   <li>Extract and resolve the configured channel.</li>
     *   <li>Look up the strategy from the registry.</li>
     *   <li>Delegate to {@link NotificationStrategy#sendMeasurement(NotificationMessage)}.</li>
     * </ol>
     *
     * <p>Any exception at any step is caught, logged with full context, and
     * swallowed. The caller's thread is unaffected.
     *
     * @param message the notification payload (must include a valid tenantCode)
     */
    @Async
    public void dispatchMeasurement(NotificationMessage message) {
        String tenantCode = message.getTenantCode();
        try {
           NotificationStrategy strategy = getStrategy(message);
            if (strategy == null) {
                // Registry miss: channel is registered in the enum but has no
                // strategy bean — this is a programming error caught at runtime.
                log.error("[Dispatcher] No strategy registered " +
                                "(tenant={}). Available channels: {}",
                        tenantCode, strategyRegistry.keySet());
                return;
            }

            // Delegate ────────────────────────────────────────────────────
            strategy.sendMeasurement(message);

        } catch (Exception e) {
            // Unexpected failure — must never silently disappear
            log.error("[Dispatcher] Unexpected error dispatching notification " +
                            "for tenant={}, messagePreview='{}': {}",
                    tenantCode, message.getParameters(), e.getMessage(), e);
        }
    }

    @Async
    public void dispatchOrderStatus(NotificationMessage message) {
        String tenantCode = message.getTenantCode();
        try {
            NotificationStrategy strategy = getStrategy(message);
            if (strategy == null) {
                // Registry miss: channel is registered in the enum but has no
                // strategy bean — this is a programming error caught at runtime.
                log.error("No strategy registered " +
                                "(tenant={}). Available channels: {}",
                        tenantCode, strategyRegistry.keySet());
                return;
            }

            // Delegate ────────────────────────────────────────────────────
            strategy.sendOrderStatus(message);

        } catch (Exception e) {
            // Unexpected failure — must never silently disappear
            log.error("Unexpected error dispatching notification " +
                            "for tenant={}, messagePreview='{}': {}",
                    tenantCode, message.getParameters(), e.getMessage(), e);
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Reads {@code preferences -> notifications -> channel} and resolves it to
     * a {@link NotificationChannel} enum constant.
     *
     * <p>Uses nested {@code Map} access to avoid deserialising the entire
     * preferences blob into a typed object — the map is already materialised
     * by Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)} mapping.
     *
     * @throws IllegalArgumentException if the key is missing or the value is
     *         not a recognised channel name
     */
    @SuppressWarnings("unchecked")
    private NotificationChannel resolveChannel(Tenant tenant, String tenantCode) {
        Map<String, Object> preferences = tenant.getPreferences();

        if (preferences == null || preferences.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tenant '" + tenantCode + "' has no preferences configured. " +
                            "Set a channel via PATCH /v1/bqom/tenants/" + tenantCode + "/preferences"
            );
        }

        Object notificationsObj = preferences.get(PREF_KEY_NOTIFICATIONS);
        if (!(notificationsObj instanceof Map)) {
            throw new IllegalArgumentException(
                    "Tenant '" + tenantCode + "' preferences.notifications is missing or " +
                            "not an object. Current preferences: " + preferences
            );
        }

        Map<String, Object> notifications = (Map<String, Object>) notificationsObj;
        Object channelValue = notifications.get(PREF_KEY_CHANNEL);

        if (channelValue == null) {
            throw new IllegalArgumentException(
                    "Tenant '" + tenantCode + "' preferences.notifications.channel is not set. " +
                            "Valid values: " + java.util.Arrays.toString(NotificationChannel.values())
            );
        }

        // NotificationChannel.fromValue() throws IllegalArgumentException for
        // unknown values — caught by the outer handler in dispatch().
        return NotificationChannel.fromValue(channelValue.toString());
    }

    /**
     * Returns a safe, length-bounded preview of a message body for logging.
     * Prevents multi-kilobyte messages from flooding log lines.
     */
    private String preview(String body) {
        if (body == null) return "<null>";
        return body.length() > LOG_PREVIEW_LENGTH
                ? body.substring(0, LOG_PREVIEW_LENGTH) + "..."
                : body;
    }
}