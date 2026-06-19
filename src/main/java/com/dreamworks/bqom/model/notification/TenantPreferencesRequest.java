package com.dreamworks.bqom.model.notification;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PATCH body for {@code /v1/bqom/tenants/{code}/preferences}.
 *
 * <p>Uses a typed {@code notifications} sub-object for the known structure,
 * plus a generic "extras" map (via {@code @JsonAnySetter}) so unknown future
 * keys are preserved during a deep merge rather than silently dropped.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantPreferencesRequest {

    @Valid
    private NotificationPreferences notifications;

    /**
     * Catch-all for any additional preference keys not yet modelled.
     * Stored here so the service can still deep-merge them into the DB JSONB.
     */
    @JsonIgnore
    @Builder.Default
    private Map<String, Object> extras = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        this.extras.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtras() {
        return extras;
    }

    // ── nested ────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NotificationPreferences {

        @NotNull(message = "notifications.channel must not be null")
        private NotificationChannel channel;
    }
}