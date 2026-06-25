package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.config.InstagramOAuthProperties;
import com.dreamworks.bqom.repository.TenantInstagramConfigRepository;
import com.dreamworks.bqom.repository.entity.TenantInstagramConfig;
import com.dreamworks.bqom.service.EncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * Core service for the Instagram OAuth 2.0 flow via Facebook Login.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li><b>Auth URL generation</b> — builds the Meta OAuth dialog URL with
 *       a signed, time-bound state token to prevent CSRF.</li>
 *   <li><b>Token exchange</b> — exchanges the one-time auth code for a
 *       short-lived token, then immediately upgrades it to a long-lived token
 *       (60-day lifetime).</li>
 *   <li><b>Account verification</b> — confirms the authenticated Facebook Page
 *       has a linked Instagram Business/Creator account. Fails explicitly if not.</li>
 *   <li><b>Persistence</b> — encrypts and stores (or updates) the token and
 *       account metadata in {@code tenant_instagram_config}.</li>
 *   <li><b>Token refresh</b> — called by the weekly scheduler to renew tokens
 *       nearing expiry. Operates on a single config record at a time so
 *       failures are isolated per tenant.</li>
 * </ol>
 *
 * <h2>Meta Graph API flow</h2>
 * <pre>
 * Step 1: User browser → Meta OAuth dialog
 *         GET https://facebook.com/{version}/dialog/oauth
 *               ?client_id=APP_ID
 *               &redirect_uri=CALLBACK_URI
 *               &scope=instagram_basic,instagram_content_publish,...
 *               &state=ENCRYPTED_STATE_TOKEN
 *               &response_type=code
 *
 * Step 2: Meta → callback with one-time code
 *         GET /v1/bqom/instagram/callback?code=AUTH_CODE&state=TOKEN
 *
 * Step 3: Backend exchanges code → short-lived user access token (1h)
 *         POST https://graph.facebook.com/{version}/oauth/access_token
 *              client_id, client_secret, redirect_uri, code
 *
 * Step 4: Backend exchanges short-lived → long-lived token (60 days)
 *         GET https://graph.facebook.com/{version}/oauth/access_token
 *             ?grant_type=fb_exchange_token
 *             &client_id=APP_ID
 *             &client_secret=APP_SECRET
 *             &fb_exchange_token=SHORT_LIVED_TOKEN
 *
 * Step 5: Fetch Instagram Business Account linked to the Facebook Page
 *         GET https://graph.facebook.com/{version}/me/accounts
 *             ?fields=instagram_business_account{id,username}
 *             &access_token=LONG_LIVED_TOKEN
 *
 * Step 6: Encrypt token, persist config, return account metadata
 * </pre>
 *
 * <h2>Permissions requested (once, at onboarding)</h2>
 * <ul>
 *   <li>{@code instagram_basic} — read account info</li>
 *   <li>{@code instagram_content_publish} — carousel posting (future phase)</li>
 *   <li>{@code pages_show_list} — list connected Facebook Pages</li>
 *   <li>{@code pages_read_engagement} — read page metadata</li>
 *   <li>{@code business_management} — access business assets</li>
 * </ul>
 *
 * <h2>Error handling philosophy</h2>
 * <p>Every Meta API call is wrapped in a try/catch that maps to a specific
 * {@link InstagramOAuthException} subtype. This lets the controller surface
 * a precise {@code reason} query param to the frontend redirect, helping
 * admins understand exactly what went wrong without exposing internal details.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstagramOAuthService {

    // OAuth scopes — requested once at onboarding so the boutique owner
    // authenticates only once even when posting features are added later
    private static final String OAUTH_SCOPES =
            "instagram_basic,instagram_content_publish,pages_show_list," +
                    "pages_read_engagement,business_management";

    // Meta API field projections — only request what we need
    private static final String IG_ACCOUNT_FIELDS =
            "instagram_business_account{id,username}";

    // Long-lived token lifetime — Meta guarantees 60 days; we store 59
    // to give the scheduler a guaranteed refresh window before actual expiry
    private static final long LONG_LIVED_TOKEN_DAYS = 59L;

    private final InstagramOAuthProperties properties;
    private final StateTokenService stateTokenService;
    private final EncryptionService encryptionService;
    private final TenantInstagramConfigRepository instagramConfigRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── 1. Auth URL generation ─────────────────────────────────────────────────

    /**
     * Builds the Meta OAuth dialog URL for the given tenant.
     *
     * <p>The {@code state} parameter is an AES-256-GCM encrypted, time-bound
     * token carrying the {@code tenantCode}. Meta echoes it back in the callback,
     * where it is verified before any processing occurs.
     *
     * @param tenantCode the tenant initiating the OAuth flow
     * @return fully-formed OAuth dialog URL ready to open in the browser
     */
    public String buildAuthUrl(String tenantCode) {
        String stateToken = stateTokenService.generate(tenantCode);
        log.info("[Instagram] Auth URL generated for tenant={}", tenantCode);

        return UriComponentsBuilder
                .fromUriString(properties.oauthDialogUrl())
                .queryParam("client_id",     properties.getAppId())
                .queryParam("redirect_uri",  properties.getRedirectUri())
                .queryParam("scope",         OAUTH_SCOPES)
                .queryParam("response_type", "code")
                .queryParam("state",         stateToken)
                .build()
                .toUriString();
    }

    // ── 2. OAuth callback processing ───────────────────────────────────────────

    /**
     * Processes the OAuth callback from Meta. This is the heart of the flow.
     *
     * <p>Steps (in strict order — each step depends on the previous):
     * <ol>
     *   <li>Verify state token → extract tenantCode.</li>
     *   <li>Exchange auth code → short-lived user access token.</li>
     *   <li>Exchange short-lived → long-lived token (60 days).</li>
     *   <li>Fetch Instagram Business Account linked to the Facebook Page.</li>
     *   <li>Encrypt token and upsert into {@code tenant_instagram_config}.</li>
     * </ol>
     *
     * @param code       the one-time authorization code from Meta
     * @param stateToken the echoed state token (must be verified first)
     * @return metadata about the connected Instagram account
     * @throws InstagramOAuthException on any failure with a specific reason code
     */
    @Transactional
    public InstagramAccountInfo processCallback(String code, String stateToken) {
        // Step 1 — verify state (throws StateTokenException on tamper/expiry)
        String tenantCode = stateTokenService.verifyAndExtractTenantCode(stateToken);
        log.info("[Instagram] Processing OAuth callback for tenant={}", tenantCode);

        // Step 2 — exchange code → short-lived token
        String shortLivedToken = exchangeCodeForShortLivedToken(code, tenantCode);

        // Step 3 — upgrade to long-lived token
        String longLivedToken = exchangeForLongLivedToken(shortLivedToken, tenantCode);

        // Step 4 — fetch Instagram Business Account
        InstagramAccountInfo accountInfo = fetchInstagramBusinessAccount(longLivedToken, tenantCode);

        // Step 5 — encrypt and persist
        persistConfig(tenantCode, longLivedToken, accountInfo);

        log.info("[Instagram] OAuth onboarding complete for tenant={}, ig_username={}",
                tenantCode, accountInfo.igUsername());

        return accountInfo;
    }

    // ── 3. Token refresh (called by scheduler) ─────────────────────────────────

    /**
     * Refreshes the long-lived access token for a single tenant config.
     *
     * <p>Meta long-lived tokens can be refreshed by making a GET request
     * with the existing token — no user interaction required. Each refresh
     * resets the 60-day clock.
     *
     * <p>This method is called per-tenant by the scheduler. Failures throw
     * an exception which the scheduler catches, logs, and skips (Option A —
     * one failure never blocks other tenants).
     *
     * @param config the config record to refresh (loaded by the scheduler)
     */
    @Transactional
    public void refreshToken(TenantInstagramConfig config) {
        String tenantCode = config.getTenantCode();
        log.info("[Instagram] Refreshing token for tenant={}, current_expiry={}",
                tenantCode, config.getTokenExpiry());

        // Decrypt current token to call the refresh endpoint
        String currentToken;
        try {
            currentToken = encryptionService.decrypt(config.getAccessToken());
        } catch (EncryptionService.EncryptionException e) {
            log.error("[Instagram] Token decryption failed for tenant={} during refresh — " +
                    "possible key rotation issue", tenantCode, e);
            throw new InstagramOAuthException(tenantCode, "token_decrypt_failed",
                    "Failed to decrypt existing token for refresh");
        }

        // Call Meta's token refresh endpoint
        String url = UriComponentsBuilder
                .fromUriString(properties.graphApiBaseUrl() + "/oauth/access_token")
                .queryParam("grant_type",       "fb_exchange_token")
                .queryParam("client_id",        properties.getAppId())
                .queryParam("client_secret",    properties.getAppSecret())
                .queryParam("fb_exchange_token", currentToken)
                .build()
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode body = parseResponse(response, tenantCode, "token refresh");

            String newToken = extractTextField(body, "access_token", tenantCode, "token refresh");

            // Encrypt and update
            config.setAccessToken(encryptionService.encrypt(newToken));
            config.setTokenExpiry(OffsetDateTime.now().plusDays(LONG_LIVED_TOKEN_DAYS));
            instagramConfigRepository.save(config);

            log.info("[Instagram] Token refreshed successfully for tenant={}, new_expiry={}",
                    tenantCode, config.getTokenExpiry());

        } catch (InstagramOAuthException e) {
            throw e; // already structured
        } catch (Exception e) {
            log.error("[Instagram] Token refresh failed for tenant={}", tenantCode, e);
            throw new InstagramOAuthException(tenantCode, "token_refresh_failed",
                    "Token refresh call to Meta failed: " + e.getMessage());
        }
    }

    // ── Private — step implementations ────────────────────────────────────────

    /**
     * Step 2: Exchange the one-time authorization code for a short-lived
     * user access token (1-hour lifetime).
     *
     * <p>This is a POST to Meta's token endpoint with the app credentials
     * and the code. The code is single-use — any retry must restart the flow.
     */
    private String exchangeCodeForShortLivedToken(String code, String tenantCode) {
        String url = properties.graphApiBaseUrl() + "/oauth/access_token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id",     properties.getAppId());
        params.add("client_secret", properties.getAppSecret());
        params.add("redirect_uri",  properties.getRedirectUri());
        params.add("code",          code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode body = parseResponse(response, tenantCode, "code exchange");
            String token = extractTextField(body, "access_token", tenantCode, "code exchange");
            log.debug("[Instagram] Short-lived token obtained for tenant={}", tenantCode);
            return token;

        } catch (InstagramOAuthException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("[Instagram] Code exchange failed for tenant={} — status={}, body={}",
                    tenantCode, e.getStatusCode(), e.getResponseBodyAsString());
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Meta rejected the authorization code: " + e.getStatusCode());
        } catch (RestClientException e) {
            log.error("[Instagram] Network error during code exchange for tenant={}", tenantCode, e);
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Network error during token exchange");
        }
    }

    /**
     * Step 3: Exchanges the short-lived user access token for a long-lived
     * token (60-day lifetime).
     *
     * <p>Meta's token exchange endpoint returns a new token that can be
     * refreshed indefinitely as long as it is used at least once every 60 days.
     * This is a GET request (Meta's API design, not ours).
     */
    private String exchangeForLongLivedToken(String shortLivedToken, String tenantCode) {
        String url = UriComponentsBuilder
                .fromUriString(properties.graphApiBaseUrl() + "/oauth/access_token")
                .queryParam("grant_type",       "fb_exchange_token")
                .queryParam("client_id",        properties.getAppId())
                .queryParam("client_secret",    properties.getAppSecret())
                .queryParam("fb_exchange_token", shortLivedToken)
                .build()
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode body = parseResponse(response, tenantCode, "long-lived token exchange");
            String token = extractTextField(body, "access_token", tenantCode, "long-lived token exchange");
            log.debug("[Instagram] Long-lived token obtained for tenant={}", tenantCode);
            return token;

        } catch (InstagramOAuthException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("[Instagram] Long-lived token exchange failed for tenant={}", tenantCode, e);
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Failed to upgrade to long-lived token");
        }
    }

    /**
     * Step 4: Fetches the Instagram Business Account linked to the
     * authenticated user's Facebook Page.
     *
     * <p>The Graph API path is:
     * {@code /me/accounts?fields=instagram_business_account{id,username}}
     *
     * <p>If no Instagram Business Account is linked, we reject the onboarding
     * with a clear error — storing a token that cannot post would be misleading.
     * The admin is directed to link their Instagram account in Meta Business Suite.
     */
    private InstagramAccountInfo fetchInstagramBusinessAccount(String accessToken, String tenantCode) {
        URI url = UriComponentsBuilder
                .fromUriString(properties.graphApiBaseUrl() + "/me/accounts")
                .queryParam("fields",       IG_ACCOUNT_FIELDS)
                .queryParam("access_token", accessToken)
                .build()
                .toUri();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode body = parseResponse(response, tenantCode, "IG account fetch");

            // The response is: { "data": [ { "instagram_business_account": { "id": "...", "username": "..." } } ] }
            JsonNode dataArray = body.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                log.warn("[Instagram] No Facebook Pages found for tenant={}", tenantCode);
                throw new NoInstagramBusinessAccountException(tenantCode);
            }

            // Iterate pages to find one with a linked Instagram Business Account
            for (JsonNode page : dataArray) {
                JsonNode igAccount = page.path("instagram_business_account");
                if (!igAccount.isMissingNode() && igAccount.has("id")) {
                    String igUserId   = igAccount.path("id").asText();
                    String igUsername = igAccount.path("username").asText(null);
                    log.info("[Instagram] Found IG Business Account for tenant={}: id={}, username={}",
                            tenantCode, igUserId, igUsername);
                    return new InstagramAccountInfo(igUserId, igUsername);
                }
            }

            // Pages found but none have a linked Instagram Business Account
            log.warn("[Instagram] Facebook Pages found but no linked IG Business Account for tenant={}",
                    tenantCode);
            throw new NoInstagramBusinessAccountException(tenantCode);

        } catch (InstagramOAuthException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("[Instagram] IG account fetch failed for tenant={}", tenantCode, e);
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Failed to fetch Instagram Business Account from Meta");
        }
    }

    /**
     * Step 5: Encrypts the long-lived token and upserts the config record.
     *
     * <p>Upsert semantics — if the tenant has reconnected (e.g., changed their
     * Instagram account), the existing record is updated in-place. This ensures
     * the unique constraint on {@code tenant_code} is never violated and avoids
     * leaving stale records.
     */
    private void persistConfig(String tenantCode, String longLivedToken,
                               InstagramAccountInfo accountInfo) {
        String encryptedToken = encryptionService.encrypt(longLivedToken);
        OffsetDateTime expiry = OffsetDateTime.now().plusDays(LONG_LIVED_TOKEN_DAYS);

        TenantInstagramConfig config = instagramConfigRepository
                .findByTenantCode(tenantCode)
                .map(existing -> {
                    log.info("[Instagram] Updating existing config for tenant={}", tenantCode);
                    existing.setIgUserId(accountInfo.igUserId());
                    existing.setIgUsername(accountInfo.igUsername());
                    existing.setAccessToken(encryptedToken);
                    existing.setTokenExpiry(expiry);
                    existing.setActive(true); // re-activate if previously disconnected
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("[Instagram] Creating new config for tenant={}", tenantCode);
                    return TenantInstagramConfig.builder()
                            .tenantCode(tenantCode)
                            .igUserId(accountInfo.igUserId())
                            .igUsername(accountInfo.igUsername())
                            .accessToken(encryptedToken)
                            .tokenExpiry(expiry)
                            .active(true)
                            .build();
                });

        instagramConfigRepository.save(config);
    }

    // ── Private — parsing helpers ──────────────────────────────────────────────

    /**
     * Parses a Meta API JSON response, checking for both HTTP-level errors
     * and Meta's application-level error envelope ({@code "error": {...}}).
     *
     * <p>Meta occasionally returns HTTP 200 with an error body — this helper
     * catches both cases so callers don't need to check both.
     */
    private JsonNode parseResponse(ResponseEntity<String> response,
                                   String tenantCode, String step) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("[Instagram] Non-2xx response during {} for tenant={}: status={}",
                    step, tenantCode, response.getStatusCode());
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Meta API returned " + response.getStatusCode() + " during " + step);
        }

        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            // Meta returns errors as: { "error": { "message": "...", "type": "...", "code": N } }
            if (body.has("error")) {
                String errorMsg  = body.path("error").path("message").asText("unknown error");
                int    errorCode = body.path("error").path("code").asInt(-1);
                log.error("[Instagram] Meta API error during {} for tenant={}: code={}, message={}",
                        step, tenantCode, errorCode, errorMsg);
                throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                        "Meta API error during " + step + ": " + errorMsg);
            }
            return body;
        } catch (InstagramOAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Instagram] Failed to parse Meta API response during {} for tenant={}",
                    step, tenantCode, e);
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Failed to parse Meta API response during " + step);
        }
    }

    /**
     * Safely extracts a required text field from a Meta API response JSON node.
     * Throws an {@link InstagramOAuthException} if the field is missing or blank.
     */
    private String extractTextField(JsonNode body, String fieldName,
                                    String tenantCode, String step) {
        String value = body.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            log.error("[Instagram] Missing '{}' field in Meta response during {} for tenant={}",
                    fieldName, step, tenantCode);
            throw new InstagramOAuthException(tenantCode, "token_exchange_failed",
                    "Meta response missing '" + fieldName + "' during " + step);
        }
        return value;
    }

    // ── Public nested types ────────────────────────────────────────────────────

    /**
     * Immutable value object carrying the Instagram account metadata
     * returned after a successful OAuth flow.
     *
     * <p>Returned to the controller which uses it only to confirm success
     * in the redirect URL — the actual data is already persisted by the time
     * this is returned.
     *
     * @param igUserId   Instagram Business Account numeric ID
     * @param igUsername Instagram @handle (may be null if Meta doesn't return it)
     */
    public record InstagramAccountInfo(String igUserId, String igUsername) {}

    /**
     * Thrown when the OAuth flow fails at any step.
     * Carries a {@code reason} code that maps directly to the
     * {@code ?instagram=error&reason=...} query param in the frontend redirect.
     */
    public static class InstagramOAuthException extends RuntimeException {
        private final String tenantCode;
        private final String reason;

        public InstagramOAuthException(String tenantCode, String reason, String message) {
            super(message);
            this.tenantCode = tenantCode;
            this.reason     = reason;
        }

        public String getTenantCode() { return tenantCode; }
        public String getReason()     { return reason; }
    }

    /**
     * Specialisation thrown when the Facebook Page has no linked Instagram
     * Business or Creator account. Surfaces as {@code reason=no_ig_business_account}
     * in the frontend redirect — the most actionable error message for admins.
     */
    public static class NoInstagramBusinessAccountException extends InstagramOAuthException {
        public NoInstagramBusinessAccountException(String tenantCode) {
            super(tenantCode, "no_ig_business_account",
                    "No Instagram Business Account linked to the Facebook Page for tenant: "
                            + tenantCode + ". Please link one in Meta Business Suite.");
        }
    }
}
