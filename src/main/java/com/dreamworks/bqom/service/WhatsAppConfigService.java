package com.dreamworks.bqom.service;

import com.dreamworks.bqom.config.AesEncryptionUtil;
import com.dreamworks.bqom.model.whatsapp.WhatsAppConfigRequest;
import com.dreamworks.bqom.model.whatsapp.WhatsAppConfigResponse;
import com.dreamworks.bqom.repository.WhatsAppConfigRepository;
import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppConfigService {

    private final WhatsAppConfigRepository configRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    public WhatsAppConfigResponse saveConfig(String tenantCode,
                                             WhatsAppConfigRequest request) {
        TenantWhatsAppConfig config;

        if (configRepository.existsByTenantCode(tenantCode)) {
            // Update existing config
            config = configRepository.findByTenantCode(tenantCode)
                    .orElseThrow(() -> new RuntimeException("Config not found for tenant: " + tenantCode));
            log.info("Updating WhatsApp config for tenant: {}", tenantCode);
        } else {
            // Create new config
            config = new TenantWhatsAppConfig();
            config.setTenantCode(tenantCode);
            log.info("Creating new WhatsApp config for tenant: {}", tenantCode);
        }

        config.setPhoneNumberId(request.getPhoneNumberId());
        config.setWabaId(request.getWabaId());
        config.setAccessToken(aesEncryptionUtil.encrypt(request.getAccessToken())); // encrypt before saving
        config.setBusinessPhoneNumber(request.getBusinessPhoneNumber());
        config.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        TenantWhatsAppConfig saved = configRepository.save(config);
        return toResponse(saved);
    }

    public WhatsAppConfigResponse getConfig(String tenantCode) {
        TenantWhatsAppConfig config = configRepository.findByTenantCode(tenantCode)
                .orElseThrow(() -> new RuntimeException("WhatsApp config not found for tenant: " + tenantCode));
        return toResponse(config);
    }

    public void toggleActive(String tenantCode, boolean isActive) {
        TenantWhatsAppConfig config = configRepository.findByTenantCode(tenantCode)
                .orElseThrow(() -> new RuntimeException("WhatsApp config not found for tenant: " + tenantCode));
        config.setIsActive(isActive);
        configRepository.save(config);
        log.info("WhatsApp config for tenant {} set to active={}", tenantCode, isActive);
    }

    // Internal use only — returns decrypted token for API calls
    public TenantWhatsAppConfig getDecryptedConfig(String tenantCode) {
        TenantWhatsAppConfig config = configRepository.findByTenantCodeAndIsActive(tenantCode, true)
                .orElseThrow(() -> new RuntimeException("Active WhatsApp config not found for tenant: " + tenantCode));
        config.setAccessToken(aesEncryptionUtil.decrypt(config.getAccessToken()));
        return config;
    }

    private WhatsAppConfigResponse toResponse(TenantWhatsAppConfig config) {
        WhatsAppConfigResponse response = new WhatsAppConfigResponse();
        response.setId(config.getId());
        response.setTenantCode(config.getTenantCode());
        response.setPhoneNumberId(config.getPhoneNumberId());
        response.setWabaId(config.getWabaId());
        response.setBusinessPhoneNumber(config.getBusinessPhoneNumber());
        response.setIsActive(config.getIsActive());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        // Note: access token is intentionally excluded from response
        return response;
    }
}
