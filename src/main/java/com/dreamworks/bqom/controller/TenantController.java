package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.TenantModel;
import com.dreamworks.bqom.model.notification.TelegramConfigRequest;
import com.dreamworks.bqom.model.notification.TenantPreferencesRequest;
import com.dreamworks.bqom.model.notification.WhatsAppConfigRequest;
import com.dreamworks.bqom.repository.entity.TenantTelegramConfig;
import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import com.dreamworks.bqom.repository.enums.UserRole;
import com.dreamworks.bqom.security.RequireRole;
import com.dreamworks.bqom.service.TelegramConfigService;
import com.dreamworks.bqom.service.TenantService;
import com.dreamworks.bqom.service.WhatsAppConfigService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for tenant management.
 *
 * <h2>New endpoints</h2>
 * <ul>
 *   <li>{@code PATCH /v1/bqom/tenants/{code}/preferences} — deep-merge
 *       notification preferences into the tenant's JSONB column.</li>
 *   <li>{@code POST /v1/bqom/tenants/{code}/telegram-config} — create or
 *       update Telegram credentials (stored AES-256-GCM encrypted).</li>
 *   <li>{@code DELETE /v1/bqom/tenants/{code}/telegram-config} — soft-delete
 *       (deactivate) the Telegram config.</li>
 * </ul>
 *
 * <h2>Security</h2>
 * <p>All mutating endpoints require {@code TENANT_ADMIN} (or higher).
 * {@code PLATFORM_ADMIN} inherits all permissions via
 * {@link com.dreamworks.bqom.security.RoleAuthorizationInterceptor}.
 *
 * <h2>Validation</h2>
 * <p>{@code @Valid} on request bodies triggers JSR-380 validation before
 * the service layer is reached. Constraint violations return {@code 400}
 * automatically via Spring's {@code MethodArgumentNotValidException} handler.
 */
@RestController
@RequestMapping(path = "/v1/bqom/tenants", produces = "application/json")
@CrossOrigin(origins = "*")
@Slf4j
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TelegramConfigService telegramConfigService;

    @Autowired
    private WhatsAppConfigService whatsAppConfigService;

    // ── existing endpoints ─────────────────────────────────────────────────────

    @GetMapping("")
    public ResponseEntity<List<TenantModel>> getTenants() {
        return new ResponseEntity<>(tenantService.getTenants(), HttpStatus.OK);
    }

    @GetMapping("/{code}")
    public ResponseEntity<TenantModel> getTenant(@PathVariable("code") String code) {
        return new ResponseEntity<>(tenantService.getTenant(code), HttpStatus.OK);
    }

    @PostMapping("")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantModel> createTenant(@RequestBody TenantModel model) {
        return new ResponseEntity<>(tenantService.createTenant(model), HttpStatus.CREATED);
    }

    @PutMapping("")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantModel> updateTenant(@RequestBody TenantModel model) {
        return new ResponseEntity<>(tenantService.updateTenant(model), HttpStatus.OK);
    }

    // ── NEW: preferences PATCH ─────────────────────────────────────────────────

    /**
     * Deep-merges the supplied preferences into the tenant's stored JSONB.
     *
     * <p>This is a true PATCH — keys absent from the request body are
     * preserved in the DB. Only the keys present in the request are updated.
     *
     * <p>Example:
     * <pre>
     * PATCH /v1/bqom/tenants/BOUTIQUE_A/preferences
     * {
     *   "notifications": {
     *     "channel": "telegram"
     *   }
     * }
     * </pre>
     *
     * @param code    tenant code (path variable)
     * @param request PATCH body — validated before service call
     * @return the full merged preferences map after update (HTTP 200)
     */
    @PatchMapping("/{code}/preferences")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<?> updatePreferences(
            @PathVariable("code") String code,
            @Valid @RequestBody TenantPreferencesRequest request) {

        log.info("[Preferences] PATCH request for tenant={}", code);

        try {
            Map<String, Object> merged = tenantService.updatePreferences(code, request);
            return ResponseEntity.ok(merged);

        } catch (RuntimeException e) {
            log.error("[Preferences] Failed to update preferences for tenant={}: {}",
                    code, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── NEW: Telegram config ───────────────────────────────────────────────────

    /**
     * Creates or updates the Telegram notification config for a tenant.
     *
     * <p>Credentials ({@code botToken}, {@code chatId}) are accepted in
     * plaintext and encrypted at the service layer before persistence.
     * They are never returned in the response.
     *
     * <p>This is an upsert — if a config already exists for the tenant it is
     * updated. The {@code active} flag is set to {@code true} on upsert.
     *
     * @param code    tenant code
     * @param request plaintext Telegram credentials (validated)
     * @return HTTP 201 with metadata (no credentials)
     */
    @PostMapping("/{code}/telegram-config")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<?> upsertTelegramConfig(
            @PathVariable("code") String code,
            @Valid @RequestBody TelegramConfigRequest request) {

        log.info("[TelegramConfig] Upsert request for tenant={}", code);

        try {
            TenantTelegramConfig saved = telegramConfigService.upsertConfig(code, request);

            // Return only safe metadata — never expose credentials in the response
            Map<String, Object> response = Map.of(
                    "id",          saved.getId(),
                    "tenantCode",  saved.getTenantCode(),
                    "active",      saved.isActive(),
                    "createdAt",   saved.getCreatedAt().toString(),
                    "updatedAt",   saved.getUpdatedAt().toString()
            );

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (RuntimeException e) {
            log.error("[TelegramConfig] Failed for tenant={}: {}", code, e.getMessage());

            HttpStatus status = e.getMessage() != null
                    && e.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.INTERNAL_SERVER_ERROR;

            return ResponseEntity.status(status)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Soft-deletes (deactivates) the Telegram config for a tenant.
     * The record is retained for audit; the dispatcher will no longer use it.
     *
     * @param code tenant code
     * @return HTTP 204 on success
     */
    @DeleteMapping("/{code}/telegram-config")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<?> deactivateTelegramConfig(@PathVariable("code") String code) {

        log.info("[TelegramConfig] Deactivate request for tenant={}", code);

        try {
            telegramConfigService.deactivateConfig(code);
            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            log.error("[TelegramConfig] Deactivation failed for tenant={}: {}",
                    code, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── NEW: WhatsApp config ───────────────────────────────────────────────────

    /**
     * Creates or updates the WhatsApp Business Cloud API config for a tenant.
     *
     * <p>Only {@code accessToken} is encrypted before persistence —
     * {@code phoneNumberId}, {@code wabaId}, and {@code businessPhoneNumber}
     * are non-secret resource identifiers stored in plaintext.
     *
     * <p>This is an upsert — if a config already exists for the tenant it is
     * updated in-place. The {@code active} flag is always set to {@code true}
     * on upsert regardless of any {@code isActive} field in the payload
     * (that field is intentionally ignored for consistency with the Telegram
     * config pattern — deactivation is via {@code DELETE}).
     *
     * <p>Example request:
     * <pre>
     * POST /v1/bqom/tenants/BOUTIQUE_A/whatsapp-config
     * {
     *   "phoneNumberId":       "1178994555292278",
     *   "wabaId":              "1026858233135309",
     *   "accessToken":         "EAAY63j...",
     *   "businessPhoneNumber": "+15556580205"
     * }
     * </pre>
     *
     * @param code    tenant code (path variable)
     * @param request validated WhatsApp credentials (plaintext on ingress)
     * @return HTTP 201 with safe metadata — credentials are never echoed back
     */
    @PostMapping("/{code}/whatsapp-config")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<?> upsertWhatsAppConfig(
            @PathVariable("code") String code,
            @Valid @RequestBody WhatsAppConfigRequest request) {

        log.info("[WhatsAppConfig] Upsert request for tenant={}", code);

        try {
            TenantWhatsAppConfig saved = whatsAppConfigService.upsertConfig(code, request);

            // Return only safe metadata — accessToken is never echoed
            Map<String, Object> response = Map.of(
                    "id",                   saved.getId(),
                    "tenantCode",           saved.getTenantCode(),
                    "phoneNumberId",        saved.getPhoneNumberId(),
                    "wabaId",               saved.getWabaId(),
                    "businessPhoneNumber",  saved.getBusinessPhoneNumber(),
                    "active",               saved.isActive(),
                    "createdAt",            saved.getCreatedAt().toString(),
                    "updatedAt",            saved.getUpdatedAt().toString()
            );

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (RuntimeException e) {
            log.error("[WhatsAppConfig] Failed for tenant={}: {}", code, e.getMessage());

            HttpStatus status = e.getMessage() != null
                    && e.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.INTERNAL_SERVER_ERROR;

            return ResponseEntity.status(status)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get the WhatsApp Business Cloud API config for a tenant
     *
     * <p>Example request:
     * <pre>
     * GET /v1/bqom/tenants/BOUTIQUE_A/whatsapp-config
     *
     * </pre>
     *
     * @param code    tenant code (path variable)
     * @return HTTP 200
     */
    @GetMapping("/{code}/whatsapp-config")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<?> getWhatsAppConfig(
            @PathVariable("code") String code) {

        log.info("[WhatsAppConfig] Get request for tenant={}", code);

        try {
            TenantWhatsAppConfig config = whatsAppConfigService.getConfig(code);

            // Return only safe metadata — accessToken is never echoed
            Map<String, Object> response = Map.of(
                    "id",                   config.getId(),
                    "tenantCode",           config.getTenantCode(),
                    "phoneNumberId",        config.getPhoneNumberId(),
                    "wabaId",               config.getWabaId(),
                    "businessPhoneNumber",  config.getBusinessPhoneNumber(),
                    "active",               config.isActive(),
                    "createdAt",            config.getCreatedAt().toString(),
                    "updatedAt",            config.getUpdatedAt().toString(),
                    "accessToken",          config.getAccessToken()
            );

            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (RuntimeException e) {
            log.error("[WhatsAppConfig] Failed for tenant={}: {}", code, e.getMessage());

            HttpStatus status = e.getMessage() != null
                    && e.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.INTERNAL_SERVER_ERROR;

            return ResponseEntity.status(status)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Soft-deletes (deactivates) the WhatsApp config for a tenant.
     *
     * <p>The record is retained for audit purposes. The notification dispatcher
     * will no longer route to WhatsApp for this tenant because
     * {@code findActiveByTenantCode} filters on {@code active = true}.
     *
     * <p>To re-enable, call {@code POST /{code}/whatsapp-config} again — the
     * upsert will set {@code active = true} automatically.
     *
     * @param code tenant code
     * @return HTTP 204 on success, 404 if no config exists
     */
    @DeleteMapping("/{code}/whatsapp-config")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<?> deactivateWhatsAppConfig(@PathVariable("code") String code) {

        log.info("[WhatsAppConfig] Deactivate request for tenant={}", code);

        try {
            whatsAppConfigService.deactivateConfig(code);
            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            log.error("[WhatsAppConfig] Deactivation failed for tenant={}: {}",
                    code, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}