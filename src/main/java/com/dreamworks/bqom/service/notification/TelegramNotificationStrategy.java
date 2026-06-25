package com.dreamworks.bqom.service.notification;

import com.dreamworks.bqom.model.notification.NotificationChannel;
import com.dreamworks.bqom.model.notification.NotificationMessage;
import com.dreamworks.bqom.repository.entity.TenantTelegramConfig;
import com.dreamworks.bqom.repository.TenantTelegramConfigRepository;
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

import java.util.Map;

/**
 * Telegram notification strategy.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Fetches the active {@link TenantTelegramConfig} for the tenant.</li>
 *   <li>Decrypts {@code botToken} and {@code chatId} via {@link EncryptionService}.</li>
 *   <li>POSTs to {@code https://api.telegram.org/bot{token}/sendMessage}.</li>
 *   <li>Throws {@link NotificationStrategy.NotificationException} on any failure
 *       so the {@link NotificationDispatcher} can log it with full context.</li>
 * </ol>
 *
 * <h2>Telegram Bot API notes</h2>
 * <ul>
 *   <li>The bot must be added to the target chat/channel before sending.</li>
 *   <li>{@code parse_mode=HTML} allows basic formatting in messages.</li>
 *   <li>4096-char message limit enforced by Telegram — callers should truncate
 *       long messages before calling this strategy.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>{@code 4xx} from Telegram — bad token / chat ID — logged as ERROR,
 *       thrown as {@link NotificationStrategy.NotificationException}.
 *       These are configuration problems, not transient failures.</li>
 *   <li>{@code 5xx} from Telegram — transient — same treatment for now.
 *       A retry mechanism (e.g., Spring Retry) can be layered here without
 *       changing the dispatcher or the strategy interface.</li>
 *   <li>Decryption failure — throws immediately; indicates key rotation issue.</li>
 * </ul>
 */
@Service
@Slf4j
public class TelegramNotificationStrategy implements NotificationStrategy {

    /**
     * Telegram Bot API base URL.
     * Overridable via env var for integration testing against a mock server.
     */
    @Value("${bqom.telegram.api-url:https://api.telegram.org}")
    private String telegramApiBaseUrl;

    /**
     * Maximum message length enforced by Telegram.
     * Messages exceeding this are truncated with a suffix to signal truncation.
     */
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final String TRUNCATION_SUFFIX = "... [truncated]";

    @Autowired
    private TenantTelegramConfigRepository telegramConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private RestTemplate restTemplate;

    // ── NotificationStrategy contract ─────────────────────────────────────────

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public void sendMeasurement(NotificationMessage message) {
        String tenantCode = message.getTenantCode();
        log.info("[Telegram] Initiating notification for tenant={}", tenantCode);

        // ── 1. Fetch config ────────────────────────────────────────────────────
        TenantTelegramConfig config = telegramConfigRepository
                .findActiveByTenantCode(tenantCode)
                .orElseThrow(() -> {
                    log.error("[Telegram] No active Telegram config found for tenant={}. " +
                                    "Create a config via POST /v1/bqom/tenants/{code}/telegram-config",
                            tenantCode);
                    return new NotificationException(tenantCode,
                            "No active Telegram config for tenant: " + tenantCode);
                });

        // ── 2. Decrypt credentials ─────────────────────────────────────────────
        //    Decryption is intentionally outside the try/catch below so that a
        //    key misconfiguration surfaces as a distinct error, not as a
        //    "delivery failed" error that might trigger a retry.
        String botToken;
        String chatId;
        try {
            botToken = encryptionService.decrypt(config.getBotToken());
            chatId   = encryptionService.decrypt(config.getChatId());
        } catch (EncryptionService.EncryptionException e) {
            log.error("[Telegram] Failed to decrypt credentials for tenant={} — " +
                    "possible key rotation mismatch. configId={}", tenantCode, config.getId(), e);
            throw new NotificationException(tenantCode,
                    "Credential decryption failed for tenant: " + tenantCode, e);
        }

        // ── 3. Build and send request ──────────────────────────────────────────
        try {
            String url         = buildSendMessageUrl(botToken);

            String safeMessage = constructMeasurementMessage(message.getParameters());

            HttpEntity<Map<String, Object>> request = buildRequest(chatId, safeMessage);

            log.info("[Telegram] POSTing to sendMessage API for tenant={}, chatId={}",
                    tenantCode, maskChatId(chatId));

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            handleResponse(response, tenantCode);

        } catch (NotificationException e) {
            throw e; // already structured — re-throw as-is
        } catch (HttpClientErrorException e) {
            // 4xx — bad token / chat ID — configuration error, not transient
            log.error("[Telegram] Client error ({}): {} — tenant={}, body={}",
                    e.getStatusCode(), e.getMessage(), tenantCode,
                    e.getResponseBodyAsString(), e);
            throw new NotificationException(tenantCode,
                    "Telegram API client error (" + e.getStatusCode() + ") for tenant: "
                            + tenantCode + ". Check bot token and chat ID.", e);
        } catch (HttpServerErrorException e) {
            // 5xx — Telegram-side transient error
            log.error("[Telegram] Server error ({}): {} — tenant={}",
                    e.getStatusCode(), e.getMessage(), tenantCode, e);
            throw new NotificationException(tenantCode,
                    "Telegram API server error (" + e.getStatusCode() + ") for tenant: "
                            + tenantCode + ". Retry later.", e);
        } catch (RestClientException e) {
            // Network-level failure (timeout, DNS, etc.)
            log.error("[Telegram] Network error sending notification for tenant={}: {}",
                    tenantCode, e.getMessage(), e);
            throw new NotificationException(tenantCode,
                    "Network failure sending Telegram notification for tenant: " + tenantCode, e);
        }
    }

    private String constructMeasurementMessage(Map<String, String> parameters) {
        String template = "Hi,\n\n Here are the %s measurements details for the customer %s. \n\n %s \n\n Thank you! 🙏";
        String measurement =  parameters.getOrDefault(NotificationConstants.MEASUREMENTS, "")
                .replace("|", "\n");
        return String.format(
                template,
                parameters.getOrDefault(NotificationConstants.DRESS_TYPE, ""),
                parameters.getOrDefault(NotificationConstants.CUSTOMER_NAME, ""),
                measurement);
    }

    @Override
    public void sendOrderStatus(NotificationMessage notificationMessage) {
        // TODO:
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Constructs the Telegram Bot API endpoint URL.
     * Format: {@code https://api.telegram.org/bot{token}/sendMessage}
     */
    private String buildSendMessageUrl(String botToken) {
        return telegramApiBaseUrl + "/bot" + botToken + "/sendMessage";
    }

    /**
     * Builds the HTTP request entity with JSON body and headers.
     *
     * <p>{@code parse_mode=HTML} enables bold, italic, and code formatting.
     * If the message body contains untrusted user input, sanitise HTML
     * characters before calling this method.
     */
    private HttpEntity<Map<String, Object>> buildRequest(String chatId, String messageBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "chat_id",    chatId,
                "text",       messageBody,
                "parse_mode", "HTML"
        );

        return new HttpEntity<>(body, headers);
    }

    /**
     * Validates the API response. Telegram returns HTTP 200 with
     * {@code {"ok": true}} on success, or {@code {"ok": false, "description": "..."}}
     * on logical failure (even with HTTP 200 in some edge cases).
     */
    private void handleResponse(ResponseEntity<String> response, String tenantCode) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("[Telegram] Unexpected non-2xx status={} for tenant={}",
                    response.getStatusCode(), tenantCode);
            throw new NotificationException(tenantCode,
                    "Telegram API returned status " + response.getStatusCode()
                            + " for tenant: " + tenantCode);
        }

        // Check Telegram's logical ok flag in the body if present
        String body = response.getBody();
        if (body != null && body.contains("\"ok\":false")) {
            log.error("[Telegram] API returned ok=false for tenant={}, body={}",
                    tenantCode, body);
            throw new NotificationException(tenantCode,
                    "Telegram API returned ok=false for tenant: " + tenantCode
                            + ". Body: " + body);
        }

        log.info("[Telegram] Notification sent successfully for tenant={}", tenantCode);
    }

    /**
     * Truncates messages that exceed Telegram's 4096-char limit.
     * Logs a warning so operators know a message was cut.
     */
    private String truncateIfNeeded(String text, String tenantCode) {
        if (text == null) return "";
        if (text.length() <= MAX_MESSAGE_LENGTH) return text;

        log.warn("[Telegram] Message exceeds {}ch limit for tenant={} — truncating. " +
                        "Original length={}",
                MAX_MESSAGE_LENGTH, tenantCode, text.length());

        return text.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length())
                + TRUNCATION_SUFFIX;
    }

    /**
     * Masks a chat ID for safe logging — shows only the last 4 characters.
     * Telegram chat IDs are numeric strings (e.g., "-1001234567890").
     */
    private String maskChatId(String chatId) {
        if (chatId == null || chatId.length() <= 4) return "****";
        return "****" + chatId.substring(chatId.length() - 4);
    }
}