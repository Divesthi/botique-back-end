package com.dreamworks.bqom.model.instagram;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * API response for the Instagram connection status endpoint.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li>{@code @JsonInclude(NON_NULL)} — when {@code connected = false}, fields
 *       like {@code igUsername} and {@code tokenExpiry} are null and are omitted
 *       from the JSON response. This keeps the "not connected" response clean:
 *       {@code {"connected": false}} rather than a noisy response full of nulls.</li>
 *   <li>Static factory methods {@link #connected} and {@link #notConnected} make
 *       the intent explicit at the call site — no risk of building a half-populated
 *       object via the builder in the wrong state.</li>
 *   <li>The {@code accessToken} is intentionally absent — never returned to any
 *       caller through any API surface.</li>
 * </ul>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstagramConfigResponse {

    /**
     * Whether this tenant has an active Instagram Business Account connected.
     * Always present in the response.
     */
    private boolean connected;

    /**
     * Instagram Business Account numeric ID.
     * Present only when {@code connected = true}.
     */
    private String igUserId;

    /**
     * Instagram @handle for display in the frontend settings page.
     * Present only when {@code connected = true}.
     * May be null even when connected if Meta did not return the username.
     */
    private String igUsername;

    /**
     * UTC timestamp when the current access token expires.
     * Present only when {@code connected = true}.
     * Used by the frontend to warn admins if the token is about to expire
     * and the refresh scheduler has not yet run.
     */
    private OffsetDateTime tokenExpiry;

    /** Timestamp of the initial connection. */
    private OffsetDateTime connectedAt;

    /** Timestamp of the last config update (reconnect or token refresh). */
    private OffsetDateTime updatedAt;

    // ── Static factory methods ─────────────────────────────────────────────────

    /**
     * Builds a response for a tenant with an active Instagram connection.
     */
    public static InstagramConfigResponse connected(String igUserId,
                                                    String igUsername,
                                                    OffsetDateTime tokenExpiry,
                                                    OffsetDateTime connectedAt,
                                                    OffsetDateTime updatedAt) {
        return InstagramConfigResponse.builder()
                .connected(true)
                .igUserId(igUserId)
                .igUsername(igUsername)
                .tokenExpiry(tokenExpiry)
                .connectedAt(connectedAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Builds a response for a tenant with no active Instagram connection.
     * Returns the minimal {@code {"connected": false}} payload.
     */
    public static InstagramConfigResponse notConnected() {
        return InstagramConfigResponse.builder()
                .connected(false)
                .build();
    }
}