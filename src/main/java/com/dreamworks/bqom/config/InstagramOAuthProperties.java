package com.dreamworks.bqom.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe, validated configuration properties for the Instagram OAuth flow.
 *
 * <h2>Why @ConfigurationProperties over @Value?</h2>
 * <ul>
 *   <li><b>Validation at startup:</b> {@code @Validated} + JSR-380 annotations
 *       mean a misconfigured environment fails fast with a clear error message
 *       — not silently at first OAuth request.</li>
 *   <li><b>Testability:</b> Properties can be injected as a plain POJO in unit
 *       tests without a Spring context.</li>
 *   <li><b>Grouping:</b> All Instagram config is co-located and discoverable,
 *       unlike scattered {@code @Value} annotations across multiple classes.</li>
 *   <li><b>IDE support:</b> Spring configuration metadata processor generates
 *       auto-complete for {@code application.properties}.</li>
 * </ul>
 *
 * <h2>Required environment variables</h2>
 * <pre>
 * INSTAGRAM_APP_ID              — Meta App ID (from Meta Developer Console)
 * INSTAGRAM_APP_SECRET          — Meta App Secret (keep server-side only, never expose)
 * INSTAGRAM_REDIRECT_URI        — Must exactly match the URI registered in Meta App Dashboard
 *                                 e.g. https://botique-back-end.onrender.com/v1/bqom/instagram/callback
 * INSTAGRAM_FRONTEND_BASE_URL   — React app base URL for post-OAuth redirects
 *                                 e.g. https://botique-front-end.onrender.com
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "instagram")
@Validated
@Getter
@Setter
public class InstagramOAuthProperties {

    /**
     * Meta App ID — available from Meta Developer Console → App Dashboard.
     * Treated as non-secret (used in OAuth URL visible to the browser),
     * but should still be environment-variable driven, not hardcoded.
     */
    @NotBlank(message = "instagram.app-id must not be blank — set INSTAGRAM_APP_ID env var")
    private String appId;

    /**
     * Meta App Secret — MUST remain server-side only.
     * Used to exchange the short-lived auth code for a token.
     * Never log, never return in an API response, never expose to the frontend.
     */
    @NotBlank(message = "instagram.app-secret must not be blank — set INSTAGRAM_APP_SECRET env var")
    private String appSecret;

    /**
     * OAuth callback URI registered in the Meta App Dashboard.
     * Must exactly match — including trailing slash — what Meta has on record.
     * Any mismatch causes Meta to reject the OAuth flow with redirect_uri_mismatch.
     *
     * Expected value:
     * https://botique-back-end.onrender.com/v1/bqom/instagram/callback
     */
    @NotBlank(message = "instagram.redirect-uri must not be blank — set INSTAGRAM_REDIRECT_URI env var")
    private String redirectUri;

    /**
     * React frontend base URL used to build the post-OAuth redirect.
     * The backend appends {@code /settings?instagram=connected} or
     * {@code /settings?instagram=error&reason=...} to this base.
     *
     * Expected value: https://botique-front-end.onrender.com
     */
    @NotBlank(message = "instagram.frontend-base-url must not be blank — set INSTAGRAM_FRONTEND_BASE_URL env var")
    private String frontendBaseUrl;

    /**
     * TTL in minutes for the encrypted state token embedded in the OAuth URL.
     * After this window, the callback will reject the state as expired,
     * preventing replay attacks on stale OAuth flows.
     *
     * Default: 10 minutes — long enough for a human to complete login,
     * short enough to limit the replay window.
     */
    @NotNull
    @Min(value = 5, message = "instagram.state-token-expiry-minutes must be at least 5")
    private Integer stateTokenExpiryMinutes = 10;

    /**
     * How many days before token expiry the weekly refresh scheduler
     * should proactively renew the Meta long-lived token.
     *
     * Default: 10 days — gives 10 days of retry headroom if Meta's API
     * is temporarily unavailable when the scheduler runs.
     * Meta long-lived tokens last 60 days, so this triggers refresh at day ~50.
     */
    @NotNull
    @Min(value = 1, message = "instagram.token-refresh-threshold-days must be at least 1")
    private Integer tokenRefreshThresholdDays = 10;

    /**
     * Meta Graph API version string. Centralised here so upgrading the
     * API version requires a single property change, not a code search.
     *
     * Keep in sync with the version tested against in your Meta App Dashboard.
     */
    @NotBlank
    private String graphApiVersion = "v19.0";

    // ── Derived helpers (used by services to avoid string concatenation) ───────

    /**
     * Base URL for all Meta Graph API calls.
     * Example: {@code https://graph.facebook.com/v19.0}
     */
    public String graphApiBaseUrl() {
        return "https://graph.facebook.com/" + graphApiVersion;
    }

    /**
     * Meta OAuth dialog base URL — the page the user's browser is sent to
     * to initiate the Facebook Login flow.
     */
    public String oauthDialogUrl() {
        return "https://www.facebook.com/" + graphApiVersion + "/dialog/oauth";
    }

    /**
     * Builds the post-OAuth redirect URL for the React frontend.
     *
     * @param status  "connected" or "error"
     * @param params  additional query params (e.g., "reason=no_ig_business_account")
     * @return full redirect URL
     */
    public String buildFrontendRedirectUrl(String status, String params) {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        String url = base + "/settings?instagram=" + status;
        if (params != null && !params.isBlank()) {
            url += "&" + params;
        }
        return url;
    }
}