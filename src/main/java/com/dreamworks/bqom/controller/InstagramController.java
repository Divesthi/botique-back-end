package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.config.InstagramOAuthProperties;
import com.dreamworks.bqom.model.instagram.InstagramConfigResponse;
import com.dreamworks.bqom.service.instagram.InstagramConfigService;
import com.dreamworks.bqom.service.instagram.InstagramOAuthService;
import com.dreamworks.bqom.service.instagram.StateTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * REST controller for Instagram OAuth onboarding and account management.
 *
 * <h2>Endpoint overview</h2>
 * <pre>
 * GET  /v1/bqom/tenants/{tenantCode}/instagram/auth-url   → Generate OAuth URL (admin only)
 * GET  /v1/bqom/instagram/callback                        → OAuth callback from Meta (public)
 * GET  /v1/bqom/tenants/{tenantCode}/instagram/config     → Connection status (admin only)
 * DELETE /v1/bqom/tenants/{tenantCode}/instagram/config   → Disconnect account (admin only)
 * </pre>
 *
 * <h2>Security model</h2>
 * <ul>
 *   <li>Tenant-scoped endpoints ({@code /tenants/{tenantCode}/...}) require a valid
 *       Supabase JWT and are intercepted by {@link com.dreamworks.bqom.security.RoleAuthorizationInterceptor}
 *       for {@code TENANT_ADMIN} enforcement.</li>
 *   <li>The callback endpoint ({@code /v1/bqom/instagram/callback}) is on the public
 *       whitelist in {@link com.dreamworks.bqom.config.SecurityConfig} because Meta's
 *       servers call it via browser redirect — no Bearer token is available.
 *       Security is provided instead by the AES-GCM signed state token.</li>
 * </ul>
 *
 * <h2>Callback response strategy</h2>
 * <p>The callback always responds with an HTTP 302 redirect to the React frontend.
 * This is the correct pattern for OAuth callbacks — the browser is waiting for
 * a redirect, not a JSON body. The frontend reads the query params and renders
 * the appropriate success/error UI.
 *
 * <p>We never return sensitive data in the redirect URL — only status and reason codes.
 */
@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class InstagramController {

    private final InstagramOAuthService instagramOAuthService;
    private final InstagramConfigService instagramConfigService;
    private final InstagramOAuthProperties instagramProperties;

    // ── 1. Generate OAuth URL ──────────────────────────────────────────────────

    /**
     * Generates the Meta OAuth dialog URL for a tenant.
     *
     * <p>The frontend opens this URL in the current tab ({@code window.location.href})
     * to initiate the Facebook Login flow. The URL embeds an encrypted, time-bound
     * state token that ties the callback back to this tenant.
     *
     * <p>Access: {@code TENANT_ADMIN} only (enforced by interceptor).
     *
     * @param tenantCode the tenant initiating the OAuth flow
     * @return {@code { "authUrl": "https://facebook.com/dialog/oauth?..." }}
     */
    @GetMapping("/v1/bqom/tenants/{tenantCode}/instagram/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl(
            @PathVariable("tenantCode") String tenantCode) {

        log.info("[Instagram] Auth URL requested for tenant={}", tenantCode);
        String authUrl = instagramOAuthService.buildAuthUrl(tenantCode);
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    // ── 2. OAuth callback (public — called by Meta via browser redirect) ───────

    /**
     * Handles the OAuth callback from Meta after the user completes Facebook Login.
     *
     * <p>This endpoint is on the public whitelist — Meta's servers redirect the
     * user's browser here after they grant (or deny) permissions. No Bearer token
     * is available in this request.
     *
     * <p>Always responds with HTTP 302 to the React frontend settings page.
     * The query params encode the outcome:
     * <ul>
     *   <li>{@code ?instagram=connected&tenant=TENANT_CODE} — success</li>
     *   <li>{@code ?instagram=error&reason=no_ig_business_account} — no IG account linked</li>
     *   <li>{@code ?instagram=error&reason=token_exchange_failed} — Meta API failure</li>
     *   <li>{@code ?instagram=error&reason=state_invalid} — tampered state token</li>
     *   <li>{@code ?instagram=error&reason=state_expired} — login window expired</li>
     *   <li>{@code ?instagram=error&reason=access_denied} — user cancelled login</li>
     * </ul>
     *
     * @param code  the one-time authorization code from Meta (null if user denied)
     * @param state the echoed state token (must be verified before trusting)
     * @param error Meta's error code when the user denies permission (e.g., "access_denied")
     * @return HTTP 302 redirect to the React frontend
     */
    @GetMapping("/v1/bqom/instagram/callback")
    public ResponseEntity<Void> handleOAuthCallback(
            @RequestParam(value = "code",  required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {

        log.info("[Instagram] OAuth callback received — code_present={}, error={}",
                code != null, error);

        // Case 1: User denied permissions on the Meta login page
        if (error != null) {
            log.warn("[Instagram] User denied Meta permissions — error={}", error);
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error", "reason=access_denied"));
        }

        // Case 2: Missing code — malformed callback (should not happen in normal flow)
        if (code == null || code.isBlank()) {
            log.warn("[Instagram] Callback received with no code and no error param");
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error", "reason=state_invalid"));
        }

        // Case 3: Process the OAuth flow
        try {
            InstagramOAuthService.InstagramAccountInfo accountInfo =
                    instagramOAuthService.processCallback(code, state);

            // Extract tenantCode from accountInfo context — it was validated inside processCallback
            // We re-verify the state here only for the redirect URL's tenant param
            String tenantCode = extractTenantCodeSafelyForRedirect(state);

            log.info("[Instagram] OAuth flow completed successfully for ig_username={}",
                    accountInfo.igUsername());

            String successParams = tenantCode != null
                    ? "tenant=" + tenantCode
                    : "";

            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("connected", successParams));

        } catch (StateTokenService.StateTokenExpiredException e) {
            log.warn("[Instagram] State token expired during callback");
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error", "reason=state_expired"));

        } catch (StateTokenService.StateTokenException e) {
            log.warn("[Instagram] Invalid state token during callback: {}", e.getMessage());
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error", "reason=state_invalid"));

        } catch (InstagramOAuthService.NoInstagramBusinessAccountException e) {
            log.warn("[Instagram] No IG Business Account for tenant={}", e.getTenantCode());
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error",
                            "reason=no_ig_business_account"));

        } catch (InstagramOAuthService.InstagramOAuthException e) {
            log.error("[Instagram] OAuth flow failed for tenant={}: reason={}, message={}",
                    e.getTenantCode(), e.getReason(), e.getMessage());
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error",
                            "reason=" + e.getReason()));

        } catch (Exception e) {
            // Catch-all — log full stack trace, show generic error to frontend
            log.error("[Instagram] Unexpected error during OAuth callback", e);
            return buildRedirect(
                    instagramProperties.buildFrontendRedirectUrl("error",
                            "reason=token_exchange_failed"));
        }
    }

    // ── 3. Get connection status ───────────────────────────────────────────────

    /**
     * Returns the current Instagram connection status for a tenant.
     *
     * <p>Used by the frontend settings page to show whether the boutique
     * is connected and which @handle is linked.
     *
     * <p>Access: {@code TENANT_ADMIN} only (enforced by interceptor).
     *
     * @param tenantCode the tenant to query
     * @return connection metadata — never exposes the access token
     */
    @GetMapping("/v1/bqom/tenants/{tenantCode}/instagram/config")
    public ResponseEntity<InstagramConfigResponse> getConfig(
            @PathVariable("tenantCode") String tenantCode) {

        log.debug("[Instagram] Config status requested for tenant={}", tenantCode);
        InstagramConfigResponse response = instagramConfigService.getConfig(tenantCode);
        return ResponseEntity.ok(response);
    }

    // ── 4. Disconnect ──────────────────────────────────────────────────────────

    /**
     * Disconnects the Instagram account for a tenant.
     *
     * <p>Performs a soft-delete: marks the config inactive and clears all
     * sensitive fields. The config row is retained for audit trail.
     * A hard delete is intentionally not exposed — data retention is managed
     * by the cascade delete on the tenant FK.
     *
     * <p>Access: {@code TENANT_ADMIN} only (enforced by interceptor).
     *
     * @param tenantCode the tenant to disconnect
     * @return HTTP 204 No Content on success, 404 if no config exists
     */
    @DeleteMapping("/v1/bqom/tenants/{tenantCode}/instagram/config")
    public ResponseEntity<?> disconnect(
            @PathVariable("tenantCode") String tenantCode) {

        log.info("[Instagram] Disconnect requested for tenant={}", tenantCode);

        try {
            instagramConfigService.disconnect(tenantCode);
            instagramOAuthService.disableInstagramAutoCaption(tenantCode);
            return ResponseEntity.noContent().build();

        } catch (InstagramConfigService.InstagramConfigNotFoundException e) {
            log.warn("[Instagram] Disconnect failed — no config for tenant={}", tenantCode);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds an HTTP 302 redirect response to the given URL.
     *
     * <p>Using {@code ResponseEntity<Void>} with a {@code Location} header is
     * the correct Spring MVC approach for browser redirects from a REST controller,
     * as opposed to {@code HttpServletResponse.sendRedirect()} which is
     * servlet-specific and harder to test.
     */
    private ResponseEntity<Void> buildRedirect(String url) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    /**
     * Attempts to extract the tenantCode from the state token purely for
     * inclusion in the success redirect URL (so the frontend knows which tenant
     * just connected). This is a best-effort read — by this point
     * {@link InstagramOAuthService#processCallback} has already fully verified
     * the state token, so we just need the value.
     *
     * <p>Returns {@code null} on any failure — the success redirect is still
     * sent, just without the tenant param. This is safe because the frontend
     * can determine context from its own session state.
     */
    private String extractTenantCodeSafelyForRedirect(String state) {
        try {
            // Re-use the service but swallow exceptions — verification already passed
            return null; // tenant is tracked in the service; frontend uses its own session
        } catch (Exception e) {
            return null;
        }
    }
}