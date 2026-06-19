package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.TenantTelegramConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantTelegramConfigRepository extends JpaRepository<TenantTelegramConfig, Long> {

    /**
     * Primary lookup — find the active config for a tenant.
     * The {@code active} guard ensures a disabled config is never used for
     * dispatching without requiring a separate soft-delete mechanism.
     */
    @Query("SELECT c FROM TenantTelegramConfig c " +
           "WHERE c.tenantCode = :tenantCode AND c.active = true")
    Optional<TenantTelegramConfig> findActiveByTenantCode(@Param("tenantCode") String tenantCode);

    /**
     * Used by admin operations (update / deactivate) where we need the record
     * regardless of its active state.
     */
    Optional<TenantTelegramConfig> findByTenantCode(String tenantCode);
}
