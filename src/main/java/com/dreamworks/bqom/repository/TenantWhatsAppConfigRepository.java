package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantWhatsAppConfigRepository extends JpaRepository<TenantWhatsAppConfig, Long> {

    /**
     * Primary lookup used by {@link com.dreamworks.bqom.service.notification.WhatsAppNotificationStrategy}
     * during dispatch. The {@code active} guard ensures a deactivated config
     * is never used for sending without requiring a hard delete.
     */
    @Query("SELECT c FROM TenantWhatsAppConfig c " +
            "WHERE c.tenantCode = :tenantCode AND c.active = true")
    Optional<TenantWhatsAppConfig> findActiveByTenantCode(@Param("tenantCode") String tenantCode);

    /**
     * Used by admin operations (upsert / deactivate) where the record is
     * needed regardless of its active state.
     */
    Optional<TenantWhatsAppConfig> findByTenantCode(String tenantCode);
}