package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.whatsapp.WhatsAppMessageRequest;
import com.dreamworks.bqom.repository.entity.TenantWhatsAppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppNotificationService {

    private static final String META_API_URL = "https://graph.facebook.com/v19.0/%s/messages";

    private final WhatsAppConfigService configService;
    private final ObjectMapper objectMapper;

    /**
     * Core method — sends any WhatsApp template message for a given tenant.
     */
    public boolean sendTemplateMessage(WhatsAppMessageRequest request) {
        try {
            // 1. Fetch tenant's decrypted WhatsApp config
            TenantWhatsAppConfig config = configService.getDecryptedConfig(request.getTenantCode());

            // 2. Build the API payload
            Map<String, Object> payload = buildPayload(request);
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // 3. Build HTTP request
            String url = String.format(META_API_URL, config.getPhoneNumberId());
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // 4. Send request
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // 5. Handle response
            if (response.statusCode() == 200) {
                log.info("WhatsApp message sent successfully to {} for tenant {}",
                        request.getToPhoneNumber(), request.getTenantCode());
                return true;
            } else {
                log.error("WhatsApp API error. Status: {}, Body: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {} for tenant {}: {}",
                    request.getToPhoneNumber(), request.getTenantCode(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convenience method — sends order ready notification.
     * Template: order_ready_pickup
     * Parameters: {{1}} = customerName, {{2}} = boutiqueName, {{3}} = orderId
     */
    public boolean sendOrderReadyNotification(String tenantCode, String boutiqueName,
                                               String customerName, String customerMobileNo,
                                               Long orderId) {
        // Normalize phone number — ensure it has country code, no + or spaces
        String normalizedPhone = normalizePhoneNumber(customerMobileNo);

        WhatsAppMessageRequest request = WhatsAppMessageRequest.builder()
                .tenantCode(tenantCode)
                .toPhoneNumber(normalizedPhone)
                .templateName("order_ready_pickup")
                .languageCode("en")
                .parameters(List.of(customerName, boutiqueName, String.valueOf(orderId)))
                .parameterNames(List.of("customer_name", "boutique_name", "order_id"))
                .build();

        return sendTemplateMessage(request);
    }

    /**
     * Convenience method — sends measurement.
     * Template: measurement_details
     * Parameters: {{1}} = dressType, {{2}} = customerName, {{3}} = measurement
     */
    public void sendMeasurement(String tenantCode, String customerName,
                                   String dressType,
                                   Map<String, Object> measurement,
                                   String mobileNumber) {
        // Normalize phone number — ensure it has country code, no + or spaces
        String normalizedPhone = normalizePhoneNumber(mobileNumber);
        // Format measurements into separate lines
        String formattedMeasurements = measurement.entrySet().stream()
                .map(e -> formatKey(e.getKey()) + ": " + e.getValue())
                .collect(Collectors.joining("  |  "));

        WhatsAppMessageRequest request = WhatsAppMessageRequest.builder()
                .tenantCode(tenantCode)
                .toPhoneNumber(normalizedPhone)
                .templateName("measurement_details")
                .languageCode("en")
                .parameters(List.of(dressType, customerName, formattedMeasurements))
                .parameterNames(List.of("dress_type", "customer_name", "measurements"))
                .build();

        sendTemplateMessage(request);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    // Converts "back_length" → "Back Length"
    private String formatKey(String key) {
        return Arrays.stream(key.split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
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
