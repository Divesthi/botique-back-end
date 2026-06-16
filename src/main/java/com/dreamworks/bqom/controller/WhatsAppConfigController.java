package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.whatsapp.WhatsAppConfigRequest;
import com.dreamworks.bqom.model.whatsapp.WhatsAppConfigResponse;
import com.dreamworks.bqom.service.WhatsAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/bqom/tenants/{tenantCode}/whatsapp/config")
@RequiredArgsConstructor
public class WhatsAppConfigController {

    private final WhatsAppConfigService configService;

    /**
     * Save or update WhatsApp config for a tenant.
     * Called from the BQOM admin panel WhatsApp Setup screen.
     * POST /api/whatsapp/config
     */
    @PostMapping
    public ResponseEntity<WhatsAppConfigResponse> saveConfig(@PathVariable String tenantCode, 
                                                             @RequestBody WhatsAppConfigRequest request) {
        log.info("Saving WhatsApp config for tenant: {}", tenantCode);
        WhatsAppConfigResponse response = configService.saveConfig(tenantCode, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get WhatsApp config for a tenant (token excluded).
     * GET /api/whatsapp/config/{tenantCode}
     */
    @GetMapping
    public ResponseEntity<WhatsAppConfigResponse> getConfig(@PathVariable String tenantCode) {
        WhatsAppConfigResponse response = configService.getConfig(tenantCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Enable or disable WhatsApp notifications for a tenant.
     * PATCH /api/whatsapp/config/{tenantCode}/toggle?active=true
     */
    @PatchMapping("/toggle")
    public ResponseEntity<Void> toggleActive(@PathVariable String tenantCode,
                                              @RequestParam boolean active) {
        configService.toggleActive(tenantCode, active);
        return ResponseEntity.ok().build();
    }
}
