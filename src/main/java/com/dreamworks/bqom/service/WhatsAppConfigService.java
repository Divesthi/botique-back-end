package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.notification.WhatsAppConfigRequest;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.TenantWhatsAppConfigRepository;
import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing per-tenant WhatsApp Business Cloud API credentials.
 *
 * <h2>Encryption contract</h2>
 * <ul>
 *   <li><b>Write path:</b> plaintext {@code accessToken} arrives from the
 *       controller, is encrypted here via {@link EncryptionService} (AES-256-GCM),
 *       and the ciphertext is persisted. The DB never sees the plaintext token.</li>
 *   <li><b>Read path (dispatch):</b>
 *       {@link com.dreamworks.bqom.service.notification.WhatsAppNotificationStrategy}
 *       fetches the config directly from the repository and decrypts
 *       {@code accessToken} inside the strategy — keeping credential handling
 *       co-located with the code that uses it.</li>
 *   <li><b>Read path (API response):</b> {@code accessToken} is never returned
 *       to callers. Only non-sensitive metadata is exposed.</li>
 * </ul>
 *
 * <h2>Upsert behaviour</h2>
 * <p>If a config already exists for the tenant it is updated in-place (all
 * fields replaced, re-encrypted). The {@code uq_whatsapp_cfg_tenant} DB
 * constraint enforces one config per tenant as a safety net.
 *
 * <h2>isActive</h2>
 * <p>Always forced to {@code true} on upsert — consistent with
 * {@link TelegramConfigService}. Deactivation is a separate explicit action
 * via {@link #deactivateConfig(String)}.
 */
@Service
@Slf4j
public class WhatsAppConfigService {

    @Autowired
    private TenantWhatsAppConfigRepository whatsAppConfigRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private EncryptionService encryptionService;

    /**
     * Creates or updates the WhatsApp config for a tenant.
     *
     * <p>Only {@code accessToken} is encrypted — {@code phoneNumberId},
     * {@code wabaId}, and {@code businessPhoneNumber} are resource identifiers
     * stored in plaintext.
     *
     * @param tenantCode the tenant to configure
     * @param request    plaintext WhatsApp credentials (pre-validated)
     * @return the persisted config (with encrypted token — use only for metadata)
     * @throws RuntimeException if the tenant does not exist
     */
    @Transactional
    public TenantWhatsAppConfig upsertConfig(String tenantCode, WhatsAppConfigRequest request) {
        // Guard: ensure tenant exists before creating a dangling config row
        tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> {
                    log.error("[WhatsAppConfig] Tenant not found: {}", tenantCode);
                    return new RuntimeException("Tenant not found: " + tenantCode);
                });

        // Encrypt only the secret credential
        String encryptedAccessToken = encryptionService.encrypt(request.getAccessToken());

        TenantWhatsAppConfig config = whatsAppConfigRepository
                .findByTenantCode(tenantCode)
                .map(existing -> {
                    log.info("[WhatsAppConfig] Updating existing config for tenant={}", tenantCode);
                    existing.setPhoneNumberId(request.getPhoneNumberId());
                    existing.setWabaId(request.getWabaId());
                    existing.setBusinessPhoneNumber(request.getBusinessPhoneNumber());
                    existing.setAccessToken(encryptedAccessToken);
                    existing.setActive(true);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("[WhatsAppConfig] Creating new config for tenant={}", tenantCode);
                    return TenantWhatsAppConfig.builder()
                            .tenantCode(tenantCode)
                            .phoneNumberId(request.getPhoneNumberId())
                            .wabaId(request.getWabaId())
                            .businessPhoneNumber(request.getBusinessPhoneNumber())
                            .accessToken(encryptedAccessToken)
                            .active(true)
                            .build();
                });

        TenantWhatsAppConfig saved = whatsAppConfigRepository.save(config);
        log.info("[WhatsAppConfig] Config saved for tenant={}, configId={}",
                tenantCode, saved.getId());
        return saved;
    }

    /**
     * Get WhatsApp config for the given tenant
     */
    public TenantWhatsAppConfig getConfig(String tenantCode) {
        // Guard: ensure tenant exists before creating a dangling config row
        tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> {
                    log.error("[WhatsAppConfig] Tenant not found: {}", tenantCode);
                    return new RuntimeException("Tenant not found: " + tenantCode);
                });

        TenantWhatsAppConfig config = whatsAppConfigRepository
                .findByTenantCode(tenantCode).orElseThrow(()-> {
                  log.error("WhatsApp configuration doesn't exists for the tenantCode - {}" ,
                          tenantCode);
                  return new RuntimeException("WhatsApp configuration doesn't exists for tenant " +
                          "- : " + tenantCode);
                  });
        config.setAccessToken(encryptionService.decrypt(config.getAccessToken()));
        return config;
    }

    /**
     * Soft-deactivates the WhatsApp config for a tenant.
     * The record is retained for audit; the dispatcher will no longer use it
     * because {@link TenantWhatsAppConfigRepository#findActiveByTenantCode}
     * filters on {@code active = true}.
     *
     * @param tenantCode the tenant whose config should be deactivated
     * @throws RuntimeException if no config exists for the tenant
     */
    @Transactional
    public void deactivateConfig(String tenantCode) {
        TenantWhatsAppConfig config = whatsAppConfigRepository
                .findByTenantCode(tenantCode)
                .orElseThrow(() -> new RuntimeException(
                        "No WhatsApp config found for tenant: " + tenantCode));

        config.setActive(false);
        whatsAppConfigRepository.save(config);
        log.info("[WhatsAppConfig] Config deactivated for tenant={}", tenantCode);
    }
}