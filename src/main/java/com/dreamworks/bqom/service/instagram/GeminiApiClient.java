package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.config.GeminiProperties;
import com.dreamworks.bqom.exception.CaptionGenerationException.GeminiApiException;
import com.dreamworks.bqom.exception.CaptionGenerationException.GeminiParseException;
import com.dreamworks.bqom.exception.CaptionGenerationException.GeminiTimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for the Google Gemini Flash multimodal API.
 *
 * <h2>Responsibility</h2>
 * <p>This class has exactly one job: make a single POST to Gemini's
 * {@code generateContent} endpoint with an image and a prompt, then parse
 * and return the raw text response. All business logic (prompt building,
 * caption validation, fallback) lives in {@link CaptionGenerationService}.
 *
 * <h2>Gemini Flash API call format</h2>
 * <pre>
 * POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=API_KEY
 * Content-Type: application/json
 *
 * {
 *   "contents": [{
 *     "parts": [
 *       {
 *         "inline_data": {
 *           "mime_type": "image/jpeg",
 *           "data":      "&lt;base64-encoded image bytes&gt;"
 *         }
 *       },
 *       {
 *         "text": "Your prompt here"
 *       }
 *     ]
 *   }],
 *   "generationConfig": {
 *     "maxOutputTokens": 300,
 *     "temperature":     0.7
 *   }
 * }
 * </pre>
 *
 * <h2>Response parsing</h2>
 * <p>Extracts text from:
 * {@code candidates[0].content.parts[0].text}
 *
 * <h2>Dedicated RestTemplate</h2>
 * <p>Uses a separate {@code RestTemplate} bean ({@code geminiRestTemplate})
 * with its own connect/read timeouts tuned for Gemini's latency profile.
 * This avoids polluting the shared {@code RestTemplate} used by other services
 * (Meta Graph API, Telegram, WhatsApp) with Gemini-specific timeout values.
 *
 * <h2>Error classification</h2>
 * <ul>
 *   <li>{@code 400} — bad request (prompt too long, invalid image) → {@link GeminiApiException}</li>
 *   <li>{@code 429} — quota exceeded → {@link GeminiApiException} with {@code gemini_quota_exceeded}</li>
 *   <li>{@code 5xx} — Gemini server error → {@link GeminiApiException}</li>
 *   <li>Timeout — {@link GeminiTimeoutException}</li>
 *   <li>Parse failure — {@link GeminiParseException}</li>
 * </ul>
 */
@Service
@Slf4j
public class GeminiApiClient {

    // JSON path constants — if Gemini's schema changes, only update here
    private static final String JSON_PATH_CANDIDATES = "candidates";
    private static final String JSON_PATH_CONTENT    = "content";
    private static final String JSON_PATH_PARTS      = "parts";
    private static final String JSON_PATH_TEXT        = "text";
    private static final String JSON_PATH_ERROR       = "error";

    private final GeminiProperties geminiProperties;
    private final RestTemplate     geminiRestTemplate;
    private final ObjectMapper     objectMapper;

    public GeminiApiClient(GeminiProperties geminiProperties,
                           @Qualifier("geminiRestTemplate") RestTemplate geminiRestTemplate,
                           ObjectMapper objectMapper) {
        this.geminiProperties   = geminiProperties;
        this.geminiRestTemplate = geminiRestTemplate;
        this.objectMapper       = objectMapper;
    }

    /**
     * Sends an image + text prompt to Gemini Flash and returns the raw text response.
     *
     * <p>The image is supplied as base64-encoded bytes — Gemini's inline_data
     * format. No pre-upload step is required (unlike OpenAI's Files API).
     *
     * @param base64ImageData base64-encoded image bytes (NOT a data URI — no {@code data:...} prefix)
     * @param mimeType        MIME type of the image (e.g., {@code "image/jpeg"}, {@code "image/png"})
     * @param prompt          the text prompt to send alongside the image
     * @param tenantCode      for structured logging context
     * @return raw text output from Gemini (may include newlines and markdown)
     * @throws GeminiApiException     on HTTP error responses
     * @throws GeminiTimeoutException on network timeout
     * @throws GeminiParseException   if the response cannot be parsed
     */
    public String generateContent(String base64ImageData, String mimeType,
                                  String prompt, String tenantCode) {
        log.info("[Gemini] Calling generateContent for tenant={}, model={}, mimeType={}",
                tenantCode, geminiProperties.getModel(), mimeType);

        String url = geminiProperties.generateContentUrl();

        // Build request payload
        Map<String, Object> requestBody = buildRequestBody(base64ImageData, mimeType, prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = geminiRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            String rawText = parseResponseText(response, tenantCode);
            log.info("[Gemini] generateContent succeeded for tenant={}, responseLength={}",
                    tenantCode, rawText != null ? rawText.length() : 0);
            return rawText;

        } catch (GeminiApiException | GeminiTimeoutException | GeminiParseException e) {
            throw e; // already structured — re-throw

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();

            // 429 = quota exceeded — distinct from other 4xx
            if (status == 429) {
                log.error("[Gemini] Quota exceeded for tenant={}: {}", tenantCode, body);
                throw new GeminiApiException(tenantCode, status,
                        "Gemini API quota exceeded. Caption generation skipped.", e);
            }

            log.error("[Gemini] Client error ({}): body={}, tenant={}",
                    status, body, tenantCode);
            throw new GeminiApiException(tenantCode, status,
                    "Gemini API client error (" + status + ") for tenant: " + tenantCode, e);

        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            log.error("[Gemini] Server error ({}) for tenant={}: {}",
                    status, tenantCode, e.getMessage());
            throw new GeminiApiException(tenantCode, status,
                    "Gemini API server error (" + status + "). Caption generation skipped.", e);

        } catch (ResourceAccessException e) {
            // Covers both connect timeout and read timeout from Spring's RestTemplate
            log.error("[Gemini] Timeout calling Gemini API for tenant={}: {}",
                    tenantCode, e.getMessage());
            throw new GeminiTimeoutException(tenantCode,
                    "Gemini API timed out for tenant: " + tenantCode
                            + ". Caption generation skipped.", e);

        } catch (Exception e) {
            log.error("[Gemini] Unexpected error for tenant={}: {}", tenantCode, e.getMessage(), e);
            throw new GeminiApiException(tenantCode, -1,
                    "Unexpected Gemini error for tenant: " + tenantCode, e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds the Gemini {@code generateContent} request payload.
     *
     * <p>Structure mirrors the Gemini REST API schema exactly:
     * <pre>
     * {
     *   "contents": [{ "parts": [ {inline_data}, {text} ] }],
     *   "generationConfig": { "maxOutputTokens": N, "temperature": T }
     * }
     * </pre>
     */
    private Map<String, Object> buildRequestBody(String base64ImageData,
                                                 String mimeType, String prompt) {
        // Image part — inline_data format
        Map<String, Object> inlineData = Map.of(
                "mime_type", mimeType,
                "data",      base64ImageData
        );
        Map<String, Object> imagePart = Map.of("inline_data", inlineData);

        // Text prompt part
        Map<String, Object> textPart = Map.of("text", prompt);

        // Content block
        Map<String, Object> content = Map.of("parts", List.of(imagePart, textPart));

        // Generation config
        Map<String, Object> generationConfig = Map.of(
                "maxOutputTokens", geminiProperties.getMaxOutputTokens(),
                "temperature",     geminiProperties.getTemperature()
        );

        return Map.of(
                "contents",         List.of(content)
               // "generationConfig", generationConfig
        );
    }

    /**
     * Parses the text from a successful Gemini {@code generateContent} response.
     *
     * <p>Gemini response structure:
     * <pre>
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{ "text": "Generated caption..." }]
     *     }
     *   }]
     * }
     * </pre>
     *
     * @throws GeminiApiException   if the response contains an error node
     * @throws GeminiParseException if the expected fields are absent
     */
    private String parseResponseText(ResponseEntity<String> response, String tenantCode) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new GeminiApiException(tenantCode, response.getStatusCode().value(),
                    "Gemini returned non-2xx status: " + response.getStatusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());

            // Check for application-level error
            if (root.has(JSON_PATH_ERROR)) {
                String errorMsg  = root.path(JSON_PATH_ERROR).path("message").asText("unknown");
                int    errorCode = root.path(JSON_PATH_ERROR).path("code").asInt(-1);
                log.error("[Gemini] API-level error for tenant={}: code={}, message={}",
                        tenantCode, errorCode, errorMsg);
                throw new GeminiApiException(tenantCode, errorCode,
                        "Gemini API error: " + errorMsg);
            }

            // Navigate: candidates[0].content.parts[0].text
            JsonNode candidates = root.path(JSON_PATH_CANDIDATES);
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("[Gemini] No candidates in response for tenant={}. Body={}",
                        tenantCode, response.getBody());
                throw new GeminiParseException(tenantCode,
                        "Gemini response contained no candidates", null);
            }

            String text = candidates
                    .get(0)
                    .path(JSON_PATH_CONTENT)
                    .path(JSON_PATH_PARTS)
                    .get(0)
                    .path(JSON_PATH_TEXT)
                    .asText(null);

            if (text == null || text.isBlank()) {
                log.warn("[Gemini] Empty text in response for tenant={}", tenantCode);
                throw new GeminiParseException(tenantCode,
                        "Gemini returned an empty text response", null);
            }

            return text.trim();

        } catch (GeminiApiException | GeminiParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Gemini] Failed to parse response for tenant={}: {}",
                    tenantCode, e.getMessage(), e);
            throw new GeminiParseException(tenantCode,
                    "Failed to parse Gemini response for tenant: " + tenantCode, e);
        }
    }
}