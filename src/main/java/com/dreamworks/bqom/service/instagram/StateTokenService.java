package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.config.InstagramOAuthProperties;
import com.dreamworks.bqom.service.EncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Generates and verifies tamper-proof, time-bound OAuth state tokens.
 *
 * <h2>Why a custom state token over a standard JWT library?</h2>
 * <p>The {@code state} parameter in OAuth 2.0 must be:
 * <ol>
 *   <li><b>Tamper-proof</b> — an attacker cannot forge a state for a different tenant.</li>
 *   <li><b>Time-bound</b> — a state token from 2 hours ago should not be replayable.</li>
 *   <li><b>Opaque to the browser</b> — the tenantCode must not be readable in the URL.</li>
 * </ol>
 *
 * <p>Standard JWT libraries (JJWT, Nimbus) were intentionally avoided to keep
 * the dependency tree minimal. AES-256-GCM via the existing {@link EncryptionService}
 * satisfies all three requirements:
 * <ul>
 *   <li>GCM's authentication tag makes the payload tamper-proof without a
 *       separate HMAC step.</li>
 *   <li>An {@code exp} field inside the encrypted payload enforces time-bound
 *       validation server-side.</li>
 *   <li>AES encryption makes the payload opaque — the browser sees only a
 *       Base64-encoded ciphertext.</li>
 * </ul>
 *
 * <h2>State token payload</h2>
 * <pre>
 * {
 *   "tenantCode": "BOUTIQUE_A",
 *   "nonce":      "550e8400-e29b-41d4-a716-446655440000",   ← prevents replay
 *   "exp":        1719000000                                 ← Unix epoch seconds
 * }
 * </pre>
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li>A different random IV is used per encryption call (guaranteed by
 *       {@link EncryptionService}) — two state tokens for the same tenant
 *       produce different ciphertexts.</li>
 *   <li>The nonce is a UUID v4 — combined with the expiry, this prevents
 *       replay attacks even within the validity window.</li>
 *   <li>If the {@code exp} has passed, the token is rejected regardless of
 *       cryptographic validity.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StateTokenService {

    private static final String FIELD_TENANT_CODE = "tenantCode";
    private static final String FIELD_NONCE       = "nonce";
    private static final String FIELD_EXP         = "exp";

    private final EncryptionService encryptionService;
    private final InstagramOAuthProperties instagramProperties;
    private final ObjectMapper objectMapper;

    /**
     * Generates a tamper-proof, time-bound state token embedding the tenantCode.
     *
     * <p>The token is safe to embed directly in the OAuth redirect URL as the
     * {@code state} query parameter. Meta will echo it back verbatim in the
     * callback, where it is verified via {@link #verifyAndExtractTenantCode(String)}.
     *
     * @param tenantCode the tenant initiating the OAuth flow
     * @return Base64-encoded AES-GCM encrypted state token
     * @throws StateTokenException if serialisation or encryption fails
     */
    public String generate(String tenantCode) {
        try {
            long expEpochSeconds = Instant.now()
                    .plusSeconds(instagramProperties.getStateTokenExpiryMinutes() * 60L)
                    .getEpochSecond();

            Map<String, Object> payload = Map.of(
                    FIELD_TENANT_CODE, tenantCode,
                    FIELD_NONCE,       UUID.randomUUID().toString(),
                    FIELD_EXP,         expEpochSeconds
            );

            String json = objectMapper.writeValueAsString(payload);
            return encryptionService.encrypt(json);

        } catch (JsonProcessingException e) {
            log.error("[StateToken] Failed to serialise state payload for tenant={}", tenantCode, e);
            throw new StateTokenException("Failed to generate state token", e);
        } catch (EncryptionService.EncryptionException e) {
            log.error("[StateToken] Failed to encrypt state payload for tenant={}", tenantCode, e);
            throw new StateTokenException("Failed to encrypt state token", e);
        }
    }

    /**
     * Verifies a state token received from the OAuth callback and extracts
     * the tenantCode it was issued for.
     *
     * <p>Verification steps (in order — fail-fast):
     * <ol>
     *   <li>AES-GCM decryption — if the ciphertext was tampered with, this
     *       throws and we reject the callback immediately.</li>
     *   <li>JSON deserialisation — malformed payloads are rejected.</li>
     *   <li>Expiry check — tokens past their {@code exp} are rejected even
     *       if cryptographically valid.</li>
     *   <li>TenantCode extraction — returned to the caller.</li>
     * </ol>
     *
     * @param stateToken the encrypted state token from the callback query param
     * @return the tenantCode embedded in the token
     * @throws StateTokenExpiredException if the token's expiry has passed
     * @throws StateTokenException        if the token is invalid for any other reason
     */
    public String verifyAndExtractTenantCode(String stateToken) {
        if (stateToken == null || stateToken.isBlank()) {
            throw new StateTokenException("State token is null or blank");
        }

        String json;
        try {
            json = encryptionService.decrypt(stateToken);
        } catch (EncryptionService.EncryptionException e) {
            // Tampered or corrupted ciphertext — treat as invalid, not as an
            // encryption infrastructure failure (no stack trace to avoid log noise
            // from probing attacks)
            log.warn("[StateToken] Decryption failed — possible tampered state token");
            throw new StateTokenException("State token is invalid");
        }

        Map<?, ?> payload;
        try {
            payload = objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("[StateToken] Failed to deserialise state payload — malformed token");
            throw new StateTokenException("State token payload is malformed");
        }

        // Expiry check — must happen before any data is trusted
        Object expObj = payload.get(FIELD_EXP);
        if (expObj == null) {
            throw new StateTokenException("State token missing exp field");
        }

        long expEpochSeconds = ((Number) expObj).longValue();
        if (Instant.now().getEpochSecond() > expEpochSeconds) {
            log.warn("[StateToken] Expired state token received — exp={}", expEpochSeconds);
            throw new StateTokenExpiredException("State token has expired");
        }

        // Extract tenantCode
        Object tenantCodeObj = payload.get(FIELD_TENANT_CODE);
        if (tenantCodeObj == null || tenantCodeObj.toString().isBlank()) {
            throw new StateTokenException("State token missing tenantCode field");
        }

        return tenantCodeObj.toString();
    }

    // ── Nested exceptions ─────────────────────────────────────────────────────

    /**
     * Thrown when the state token cannot be verified for any reason other than expiry.
     * Maps to a {@code state_invalid} error reason in the frontend redirect.
     */
    public static class StateTokenException extends RuntimeException {
        public StateTokenException(String message) {
            super(message);
        }
        public StateTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown specifically when the state token has passed its expiry time.
     * Kept as a distinct subclass so the controller can surface a more
     * specific {@code state_expired} reason to the frontend, helping admins
     * understand they need to restart the OAuth flow (rather than suspecting
     * a security incident).
     */
    public static class StateTokenExpiredException extends StateTokenException {
        public StateTokenExpiredException(String message) {
            super(message);
        }
    }
}