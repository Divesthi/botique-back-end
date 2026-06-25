package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.TenantInstagramConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TenantInstagramConfig}.
 *
 * <h2>Query design notes</h2>
 * <ul>
 *   <li>{@link #findActiveByTenantCode} — used by admin reads and the
 *       disconnect flow; the {@code active} guard ensures a disabled config
 *       is never returned without explicit opt-in.</li>
 *   <li>{@link #findByTenantCode} — used by the disconnect flow where the
 *       record must be found regardless of active state.</li>
 *   <li>{@link #findAllActiveExpiringBefore} — used exclusively by
 *       {@link com.dreamworks.bqom.scheduler.InstagramTokenRefreshScheduler}.
 *       Returns only active configs, ordered by soonest expiry first so the
 *       scheduler processes the most urgent tokens first and can be safely
 *       interrupted mid-run without losing track of urgency.</li>
 * </ul>
 */
@Repository
public interface TenantInstagramConfigRepository extends JpaRepository<TenantInstagramConfig, Long> {

    /**
     * Primary lookup for admin reads (config status, disconnect).
     * Returns only the active config — a deactivated config is treated
     * as if it doesn't exist for all business operations.
     *
     * @param tenantCode the tenant whose config is needed
     * @return the active Instagram config, or empty if not connected
     */
    @Query("SELECT c FROM TenantInstagramConfig c " +
            "WHERE c.tenantCode = :tenantCode AND c.active = true")
    Optional<TenantInstagramConfig> findActiveByTenantCode(@Param("tenantCode") String tenantCode);

    /**
     * Admin-only lookup used by the disconnect flow.
     * Returns the config regardless of its active state so the service
     * can set {@code active = false} even if it's already inactive.
     *
     * @param tenantCode the tenant whose config is needed
     * @return the config (active or inactive), or empty if never connected
     */
    Optional<TenantInstagramConfig> findByTenantCode(String tenantCode);

    /**
     * Scheduler-only query — returns all active configs whose token expiry
     * falls before {@code expiryThreshold} (i.e., tokens that are about to
     * expire or have already expired).
     *
     * <p>Ordered by {@code token_expiry ASC} so the scheduler always processes
     * the most urgent tokens first. This ordering ensures that if the scheduler
     * is interrupted mid-run (e.g., server restart), the next run still picks
     * up the most critical tokens first.
     *
     * <p>This maps to the partial index {@code idx_ig_cfg_expiry_active}
     * created in migration 10, making the query O(matches) rather than O(table).
     *
     * @param expiryThreshold tokens expiring before this timestamp are returned
     * @return active configs needing token refresh, soonest first
     */
    @Query("SELECT c FROM TenantInstagramConfig c " +
            "WHERE c.active = true AND c.tokenExpiry < :expiryThreshold " +
            "ORDER BY c.tokenExpiry ASC")
    List<TenantInstagramConfig> findAllActiveExpiringBefore(
            @Param("expiryThreshold") OffsetDateTime expiryThreshold);
}