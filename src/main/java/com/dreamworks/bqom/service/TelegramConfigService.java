package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.notification.TelegramConfigRequest;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.TenantTelegramConfigRepository;
import com.dreamworks.bqom.repository.entity.TenantTelegramConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing per-tenant Telegram credentials.
 *
 * <h2>Encryption contract</h2>
 * <ul>
 *   <li><b>Write path:</b> plaintext credentials arrive from the controller,
 *       are encrypted here via {@link EncryptionService}, and the ciphertext
 *       is persisted. The DB never sees plaintext.</li>
 *   <li><b>Read path (internal):</b> {@link com.dreamworks.bqom.service.notification.TelegramNotificationStrategy}
 *       calls {@link TenantTelegramConfigRepository} directly and decrypts
 *       inside the strategy — keeping credential handling co-located with usage.</li>
 *   <li><b>Read path (API):</b> credentials are never returned to callers.
 *       Only metadata (tenantCode, active, timestamps) is exposed.</li>
 * </ul>
 *
 * <h2>Upsert behaviour</h2>
 * <p>If a config already exists for the tenant it is updated (bot token and
 * chat ID replaced, re-encrypted). This is safer than allowing duplicate rows
 * because the {@code uq_telegram_cfg_tenant} constraint enforces one config
 * per tenant at the DB level anyway.
 */
@Service
@Slf4j
public class TelegramConfigService {

    @Autowired
    private TenantTelegramConfigRepository telegramConfigRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private EncryptionService encryptionService;

    /**
     * Creates or updates the Telegram config for a tenant.
     *
     * <p>Credentials are encrypted before persistence.
     * Returns a sanitised view (no plaintext credentials).
     *
     * @param tenantCode the tenant to configure
     * @param request    plaintext bot token and chat ID
     * @return the persisted config (credentials omitted from return value)
     * @throws RuntimeException if the tenant does not exist
     */
    @Transactional
    public TenantTelegramConfig upsertConfig(String tenantCode, TelegramConfigRequest request) {
        // Guard: ensure the tenant exists before creating a dangling config
        tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> {
                    log.error("[TelegramConfig] Tenant not found: {}", tenantCode);
                    return new RuntimeException("Tenant not found: " + tenantCode);
                });

        // Encrypt credentials at service layer — DB stores only ciphertext
        String encryptedBotToken = encryptionService.encrypt(request.getBotToken());
        String encryptedChatId   = encryptionService.encrypt(request.getChatId());

        TenantTelegramConfig config = telegramConfigRepository
                .findByTenantCode(tenantCode)
                .map(existing -> {
                    log.info("[TelegramConfig] Updating existing config for tenant={}", tenantCode);
                    existing.setBotToken(encryptedBotToken);
                    existing.setChatId(encryptedChatId);
                    existing.setActive(true);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("[TelegramConfig] Creating new config for tenant={}", tenantCode);
                    return TenantTelegramConfig.builder()
                            .tenantCode(tenantCode)
                            .botToken(encryptedBotToken)
                            .chatId(encryptedChatId)
                            .active(true)
                            .build();
                });

        TenantTelegramConfig saved = telegramConfigRepository.save(config);
        log.info("[TelegramConfig] Config saved for tenant={}, configId={}",
                tenantCode, saved.getId());

        return saved;
    }

    /**
     * Deactivates (soft-deletes) the Telegram config for a tenant.
     * The record is retained for audit purposes.
     *
     * @param tenantCode the tenant whose config should be deactivated
     * @throws RuntimeException if no config exists for the tenant
     */
    @Transactional
    public void deactivateConfig(String tenantCode) {
        TenantTelegramConfig config = telegramConfigRepository
                .findByTenantCode(tenantCode)
                .orElseThrow(() -> new RuntimeException(
                        "No Telegram config found for tenant: " + tenantCode));

        config.setActive(false);
        telegramConfigRepository.save(config);
        log.info("[TelegramConfig] Config deactivated for tenant={}", tenantCode);
    }

    public TenantTelegramConfig getTelegramConfig(String tenantCode) {
        TenantTelegramConfig config = telegramConfigRepository
                .findByTenantCode(tenantCode)
                .orElseThrow(() -> new RuntimeException(
                        "No Telegram config found for tenant: " + tenantCode));
        return config;
    }
}