package com.dreamworks.bqom.model.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Canonical identifiers for notification channels.
 *
 * <p>Using an enum (rather than bare strings) means:
 * <ul>
 *   <li>The compiler catches typos in channel names.</li>
 *   <li>{@link com.dreamworks.bqom.service.notification.NotificationDispatcher}
 *       can switch on a closed set without instanceof chains.</li>
 *   <li>Adding a new channel requires only a new enum constant + a new
 *       {@link com.dreamworks.bqom.service.notification.NotificationStrategy}
 *       bean — the dispatcher finds it via the registry map.</li>
 * </ul>
 */
public enum NotificationChannel {

    WHATSAPP("whatsapp"),
    TELEGRAM("telegram");

    private final String value;

    NotificationChannel(String value) {
        this.value = value;
    }

    /** Used by Jackson when serialising preferences JSON. */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Case-insensitive lookup used by the dispatcher when reading the
     * {@code preferences->>'notifications'->>'channel'} field.
     *
     * @throws IllegalArgumentException for unknown channel names so the
     *         dispatcher can log a useful error rather than fail silently.
     */
    @JsonCreator
    public static NotificationChannel fromValue(String value) {
        return Arrays.stream(values())
                .filter(c -> c.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown notification channel: '" + value + "'. " +
                                "Valid values: " + Arrays.toString(values())
                ));
    }
}