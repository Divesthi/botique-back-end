package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppConfigRepository extends JpaRepository<TenantWhatsAppConfig, Long> {

    Optional<TenantWhatsAppConfig> findByTenantCodeAndIsActive(String tenantCode, Boolean isActive);

    Optional<TenantWhatsAppConfig> findByTenantCode(String tenantCode);

    boolean existsByTenantCode(String tenantCode);
}
