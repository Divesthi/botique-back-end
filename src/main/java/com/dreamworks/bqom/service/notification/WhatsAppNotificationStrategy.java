package com.dreamworks.bqom.service.notification;

import com.dreamworks.bqom.model.notification.NotificationChannel;
import com.dreamworks.bqom.model.notification.NotificationMessage;
import com.dreamworks.bqom.model.notification.WhatsAppMessageRequest;
import com.dreamworks.bqom.repository.TenantWhatsAppConfigRepository;
import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import com.dreamworks.bqom.service.EncryptionService;
import com.dreamworks.bqom.service.NotificationConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp notification strategy — full Meta Cloud API implementation.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Fetches the active {@link TenantWhatsAppConfig} for the tenant.</li>
 *   <li>Decrypts {@code accessToken} via {@link EncryptionService}.</li>
 *   <li>POSTs to the Meta WhatsApp Cloud API:
 *       {@code POST /{phone_number_id}/messages} with a Bearer token.</li>
 *   <li>Throws {@link NotificationStrategy.NotificationException} on any
 *       failure so the {@link NotificationDispatcher} logs it with full
 *       context — never silently swallowed.</li>
 * </ol>
 *
 * <h2>Meta Cloud API notes</h2>
 * <ul>
 *   <li>Endpoint: {@code POST https://graph.facebook.com/{version}/{phoneNumberId}/messages}</li>
 *   <li>Auth: Bearer {@code accessToken} in the Authorization header.</li>
 *   <li>Message type: {@code text} with a plain-text body.</li>
 *   <li>4096-char message limit — messages are truncated with a visible suffix
 *       and a warning log entry.</li>
 *   <li>The recipient ({@code to}) must be in E.164 format and opted in.</li>
 * </ul>
 *
 * <h2>Error classification</h2>
 * <ul>
 *   <li>{@code 4xx} — bad token, invalid phone number, policy violation.
 *       Configuration problem, not transient. Logged at ERROR.</li>
 *   <li>{@code 5xx} — Meta-side transient error. Logged at ERROR.</li>
 *   <li>Network failure — timeout, DNS. Logged at ERROR.</li>
 *   <li>Decryption failure — key rotation mismatch. Surfaces as a distinct
 *       error outside the HTTP try/catch so it is never masked.</li>
 * </ul>
 */
@Service
@Slf4j
public class WhatsAppNotificationStrategy implements NotificationStrategy {

    private static final int    MAX_MESSAGE_LENGTH = 4096;
    private static final String TRUNCATION_SUFFIX  = "... [truncated]";
    private static final String META_API_URL = "https://graph.facebook.com/v19.0/%s/messages";

    @Value("${bqom.whatsapp.api-url:https://graph.facebook.com/v19.0}")
    private String apiBaseUrl;

    @Autowired
    private TenantWhatsAppConfigRepository whatsAppConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private RestTemplate restTemplate;

    // ── NotificationStrategy contract ─────────────────────────────────────────

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public void sendMeasurement(NotificationMessage message) {
        String tenantCode = message.getTenantCode();
        log.info("[WhatsApp] Initiating notification for tenant={}", tenantCode);

        // ── Fetch config ────────────────────────────────────────────────────
        TenantWhatsAppConfig config = getTenantWhatsAppConfig(tenantCode);
        // ── Decrypt access token ────────────────────────────────────────────
        // Intentionally outside the HTTP try/catch — a decryption failure is a
        // key management problem, not a transient delivery failure, and must
        // surface as a distinct error type.
        String accessToken = getAccessToken(config, tenantCode);
        WhatsAppMessageRequest whatsAppMessageRequest = WhatsAppMessageRequest.builder()
                .languageCode("en")
                .toPhoneNumber(normalizePhoneNumber(message.getToPhoneNumber()))
                .templateName("measurement_details")
                .phoneNumberId(config.getPhoneNumberId())
                .tenantCode(tenantCode)
                .parameters(List.of(message.getParameters().getOrDefault(NotificationConstants.DRESS_TYPE, ""),
                                    message.getParameters().getOrDefault(NotificationConstants.CUSTOMER_NAME, ""),
                        message.getParameters().getOrDefault(NotificationConstants.MEASUREMENTS, "")))
                .parameterNames(List.of("dress_type", "customer_name", "measurements"))
                .build();
        // ── Build and dispatch ──────────────────────────────────────────────
        try {
            // Build the API payload
            Map<String, Object> payload = buildPayload(whatsAppMessageRequest);
            // Build request URL
            String url = String.format(META_API_URL, config.getPhoneNumberId());
            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            // Create HTTP entity
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(payload, headers);
            // Send request
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );
            handleResponse(response, tenantCode);

        } catch (NotificationException e) {
            throw e; // already structured — re-throw as-is

        } catch (Exception e) {
            log.error("Error while sending notification for tenant={}: {}", tenantCode, e.getMessage(), e);
            throw new NotificationException(tenantCode,
                    "Error while sending WhatsApp notification for tenant: " + tenantCode, e);
        }
    }

    @Override
    public void sendOrderStatus(NotificationMessage message) {
        String tenantCode = message.getTenantCode();
        try {
            log.info("Initiating notification for tenant={}", tenantCode);

            // ── Fetch config ────────────────────────────────────────────────────
            TenantWhatsAppConfig config = getTenantWhatsAppConfig(tenantCode);
            // ── Decrypt access token ────────────────────────────────────────────
            // Intentionally outside the HTTP try/catch — a decryption failure is a
            // key management problem, not a transient delivery failure, and must
            // surface as a distinct error type.
            String accessToken = getAccessToken(config, tenantCode);
            WhatsAppMessageRequest whatsAppMessageRequest = WhatsAppMessageRequest.builder()
                    .languageCode("en")
                    .toPhoneNumber(normalizePhoneNumber(message.getToPhoneNumber()))
                    .templateName("order_ready_pickup")
                    .phoneNumberId(config.getPhoneNumberId())
                    .tenantCode(tenantCode)
                    .parameters(List.of(message.getParameters().getOrDefault(NotificationConstants.CUSTOMER_NAME, ""),
                            message.getParameters().getOrDefault(NotificationConstants.BOUTIQUE_NAME, ""),
                            message.getParameters().getOrDefault(NotificationConstants.ORDER_ID, "")))
                    .parameterNames(List.of("customer_name", "boutique_name", "order_id"))
                    .build();
            try {
                // Build the API payload
                Map<String, Object> payload = buildPayload(whatsAppMessageRequest);
                //  Build request URL
                String url = String.format(META_API_URL, config.getPhoneNumberId());
                // Build headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(accessToken);
                // Create HTTP entity
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(payload, headers);
                // Send request
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        httpEntity,
                        String.class
                );
                handleResponse(response, tenantCode);
            } catch (Exception e) {
                log.error("Error while sending notification for tenant={}: {}", tenantCode, e.getMessage(), e);
                throw new NotificationException(tenantCode,
                        "Error while sending WhatsApp notification for tenant: " + tenantCode, e);
            }
        }catch (Exception e) {
                log.error("Error while sending notification for tenant={}: {}", tenantCode, e.getMessage(), e);
                throw new NotificationException(tenantCode,
                        "Error while sending WhatsApp notification for tenant: " + tenantCode, e);
        }
    }

    private String getAccessToken(TenantWhatsAppConfig config, String tenantCode) {
        try {
            return encryptionService.decrypt(config.getAccessToken());
        } catch (EncryptionService.EncryptionException e) {
            log.error("[WhatsApp] Failed to decrypt accessToken for tenant={} — " +
                            "possible key rotation mismatch. configId={}",
                    tenantCode, config.getId(), e);
            throw new NotificationException(tenantCode,
                    "Credential decryption failed for tenant: " + tenantCode, e);
        }
    }

    private TenantWhatsAppConfig getTenantWhatsAppConfig(String tenantCode) {
        TenantWhatsAppConfig config = whatsAppConfigRepository
                .findActiveByTenantCode(tenantCode)
                .orElseThrow(() -> {
                    log.error("[WhatsApp] No active WhatsApp config found for tenant={}. " +
                                    "Create a config via POST /v1/bqom/tenants/{code}/whatsapp-config",
                            tenantCode);
                    return new NotificationException(tenantCode,
                            "No active WhatsApp config for tenant: " + tenantCode);
                });
        return config;
    }

    /**
     * Validates the Meta API response.
     * Meta returns HTTP 200 with a JSON body on success.
     * On failure it returns non-2xx or a body with an {@code error} key.
     */
    private void handleResponse(ResponseEntity<String> response, String tenantCode) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("[WhatsApp] Non-2xx status={} for tenant={}",
                    response.getStatusCode(), tenantCode);
            throw new NotificationException(tenantCode,
                    "WhatsApp API returned status " + response.getStatusCode()
                            + " for tenant: " + tenantCode);
        }

        String body = response.getBody();
        if (body != null && body.contains("\"error\"")) {
            log.error("[WhatsApp] API returned error for tenant={}, body={}", tenantCode, body);
            throw new NotificationException(tenantCode,
                    "WhatsApp API returned an error for tenant: " + tenantCode
                            + ". Body: " + body);
        }

        log.info("[WhatsApp] Notification sent successfully for tenant={}", tenantCode);
    }

    /**
     * Truncates messages exceeding Meta's 4096-char limit.
     */
    private String truncateIfNeeded(String text, String tenantCode) {
        if (text == null) return "";
        if (text.length() <= MAX_MESSAGE_LENGTH) return text;

        log.warn("[WhatsApp] Message exceeds {}ch limit for tenant={} — truncating. " +
                        "Original length={}",
                MAX_MESSAGE_LENGTH, tenantCode, text.length());

        return text.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length())
                + TRUNCATION_SUFFIX;
    }

    /**
     * Masks a phone number for safe logging — shows only the last 4 digits.
     * Example: {@code +15556580205} → {@code +*******0205}
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "+" + "*".repeat(Math.max(0, phone.length() - 5))
                + phone.substring(phone.length() - 4);
    }

    private Map<String, Object> buildPayload(WhatsAppMessageRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", request.getToPhoneNumber());
        payload.put("type", "template");

        Map<String, Object> template = new HashMap<>();
        template.put("name", request.getTemplateName());

        Map<String, String> language = new HashMap<>();
        language.put("code", request.getLanguageCode() != null ? request.getLanguageCode() : "en");
        template.put("language", language);

        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            List<String> paramNames = request.getParameterNames(); // ← need to add this to request
            List<Map<String, String>> paramList = new ArrayList<>();

            for (int i = 0; i < request.getParameters().size(); i++) {
                Map<String, String> p = new HashMap<>();
                p.put("type", "text");
                p.put("parameter_name", paramNames.get(i));        // ← named key
                p.put("text", request.getParameters().get(i));
                paramList.add(p);
            }

            Map<String, Object> bodyComponent = new HashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", paramList);

            template.put("components", List.of(bodyComponent));
        }

        payload.put("template", template);
        return payload;
    }

    /**
     * Normalizes Indian mobile numbers to WhatsApp format.
     * Input:  "9876543210" or "+919876543210" or "919876543210"
     * Output: "919876543210"
     */
    private String normalizePhoneNumber(String mobileNo) {
        if (mobileNo == null) return "";
        String cleaned = mobileNo.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        // Add India country code if not present
        if (cleaned.length() == 10) {
            cleaned = "91" + cleaned;
        }
        return cleaned;
    }
}