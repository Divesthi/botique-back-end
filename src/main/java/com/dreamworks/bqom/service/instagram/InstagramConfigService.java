package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.model.instagram.InstagramConfigResponse;
import com.dreamworks.bqom.repository.TenantInstagramConfigRepository;
import com.dreamworks.bqom.repository.entity.TenantInstagramConfig;
import com.dreamworks.bqom.service.TelegramConfigService;
import com.dreamworks.bqom.service.WhatsAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing the Instagram configuration lifecycle for a tenant.
 *
 * <h2>Separation from InstagramOAuthService</h2>
 * <p>{@link InstagramOAuthService} owns the Meta API conversation (token
 * exchange, account fetch, refresh). This service owns the CRUD surface
 * that the controller exposes to admins:
 * <ul>
 *   <li>Reading the current connection status (without exposing the token).</li>
 *   <li>Disconnecting (soft-delete) and hard-deleting the config.</li>
 * </ul>
 *
 * <p>This split keeps {@link InstagramOAuthService} focused on the external
 * Meta API boundary and this service focused on the internal data boundary.
 * It also makes both independently testable without mocking unrelated concerns.
 *
 * <h2>Disconnect vs Delete</h2>
 * <p>Disconnect sets {@code active = false} and removes the encrypted token
 * and account IDs from the record for GDPR/data minimisation reasons, but
 * retains the row for audit trail (created_at, updated_at, tenant_code).
 * This matches the pattern used by {@link TelegramConfigService} and
 * {@link WhatsAppConfigService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstagramConfigService {

    private final TenantInstagramConfigRepository instagramConfigRepository;

    /**
     * Returns the current Instagram connection status for a tenant.
     *
     * <p>The response includes the @handle and account ID for the frontend
     * settings page but never exposes the access token — not even encrypted.
     *
     * @param tenantCode the tenant to query
     * @return connection metadata, or an "not connected" response if no active config exists
     */
    public InstagramConfigResponse getConfig(String tenantCode) {
        Optional<TenantInstagramConfig> configOpt =
                instagramConfigRepository.findActiveByTenantCode(tenantCode);

        if (configOpt.isEmpty()) {
            log.debug("[InstagramConfig] No active config for tenant={}", tenantCode);
            return InstagramConfigResponse.notConnected();
        }

        TenantInstagramConfig config = configOpt.get();
        log.debug("[InstagramConfig] Active config found for tenant={}, ig_username={}",
                tenantCode, config.getIgUsername());

        return InstagramConfigResponse.connected(
                config.getIgUserId(),
                config.getIgUsername(),
                config.getTokenExpiry(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

    /**
     * Disconnects the Instagram account for a tenant.
     *
     * <p>This performs a soft-delete: the config row is retained for audit
     * purposes but is marked inactive and its sensitive fields are cleared.
     *
     * <p>Clearing sensitive fields ({@code access_token}, {@code ig_user_id},
     * {@code ig_username}) on disconnect is a deliberate data minimisation
     * decision — once disconnected, the token is invalid for posting anyway,
     * and retaining it serves no purpose while presenting an unnecessary
     * data risk.
     *
     * <p>If the tenant reconnects later, {@link InstagramOAuthService#persistConfig}
     * will upsert the row with fresh credentials — no conflict.
     *
     * @param tenantCode the tenant to disconnect
     * @throws InstagramConfigNotFoundException if no config exists for this tenant
     */
    @Transactional
    public void disconnect(String tenantCode) {
        TenantInstagramConfig config = instagramConfigRepository
                .findByTenantCode(tenantCode)
                .orElseThrow(() -> {
                    log.warn("[InstagramConfig] Disconnect requested but no config found for tenant={}",
                            tenantCode);
                    return new InstagramConfigNotFoundException(
                            "No Instagram configuration found for tenant: " + tenantCode);
                });

        if (!config.isActive()) {
            log.info("[InstagramConfig] Disconnect called on already-inactive config for tenant={} " +
                    "— treating as no-op", tenantCode);
            // Idempotent — no error thrown, no-op is safe
            return;
        }

        // Soft-delete: clear sensitive fields, mark inactive
        config.setActive(false);
        config.setAccessToken(""); // cleared — token is invalid post-disconnect anyway
        config.setIgUserId("");
        config.setIgUsername(null);
        // token_expiry is left as-is for audit (when did it expire?)

        instagramConfigRepository.save(config);
        log.info("[InstagramConfig] Instagram account disconnected for tenant={}", tenantCode);
    }

    // ── Nested exception ───────────────────────────────────────────────────────

    /**
     * Thrown when a config operation targets a tenant that has no Instagram
     * configuration (connected or otherwise).
     * Maps to HTTP 404 in the controller.
     */
    public static class InstagramConfigNotFoundException extends RuntimeException {
        public InstagramConfigNotFoundException(String message) {
            super(message);
        }
    }
}