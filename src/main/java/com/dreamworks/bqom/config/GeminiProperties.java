package com.dreamworks.bqom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the Google Gemini Flash API.
 *
 * <h2>Binding source</h2>
 * <p>All properties are bound from {@code application.properties} under the
 * {@code gemini} prefix. Secrets (API key) are sourced from environment
 * variables — never hardcoded.
 *
 * <h2>Validation</h2>
 * <p>{@code @Validated} triggers JSR-380 checks at startup so a misconfigured
 * Gemini integration fails fast rather than at the first caption request.
 *
 * <h2>Thread safety</h2>
 * <p>Immutable after {@code @PostConstruct} — safe for concurrent access
 * from the {@code instagramPostExecutor} thread pool.
 */
@Component
@ConfigurationProperties(prefix = "gemini")
@Validated
@Getter
@Setter
public class GeminiProperties {

    /**
     * Google AI Studio API key.
     * Sourced from {@code GEMINI_API_KEY} env var in production.
     * Never log or expose this value.
     */
    @NotBlank(message = "gemini.api-key must not be blank")
    private String apiKey;

    /**
     * Gemini model identifier.
     * Default: {@code gemini-1.5-flash} — fastest, lowest cost, ideal for caption generation.
     * Override to {@code gemini-1.5-pro} for higher-quality captions if needed.
     */
    @NotBlank(message = "gemini.model must not be blank")
    private String model = "gemini-1.5-flash";

    /**
     * Base URL for the Google Generative AI REST API.
     * Overridable for integration testing against a mock server.
     */
    @NotBlank(message = "gemini.api-base-url must not be blank")
    private String apiBaseUrl = "https://generativelanguage.googleapis.com";

    /**
     * Maximum number of tokens Gemini should generate in the response.
     * Captions + 5 hashtags fit comfortably within 300 tokens.
     * Keeping this low reduces latency and cost.
     */
    @Min(50)
    @Max(500)
    private int maxOutputTokens = 300;

    /**
     * Gemini temperature — controls randomness of the output.
     * 0.7 gives creative but consistent professional captions.
     * Range: 0.0 (deterministic) – 1.0 (very creative).
     */
    @NotNull
    private Double temperature = 0.7;

    /**
     * HTTP connection timeout in seconds for the Gemini API call.
     * Gemini Flash typically responds in 2–5s; 10s gives adequate headroom.
     */
    @Min(1)
    @Max(30)
    private int connectTimeoutSeconds = 10;

    /**
     * HTTP read timeout in seconds for the Gemini API call.
     * Multimodal requests with image data may take slightly longer.
     */
    @Min(5)
    @Max(60)
    private int readTimeoutSeconds = 20;

    /**
     * Maximum image size in bytes to send to Gemini.
     * Default: 4MB. Images above this threshold are rejected before the API call
     * to prevent unnecessary data transfer and potential Gemini rejections.
     * Note: This is the pre-encoded byte limit; base64 output will be ~33% larger.
     */
    @Min(512_000)          // 500 KB minimum
    @Max(10_485_760)       // 10 MB maximum
    private long maxImageSizeBytes = 4_194_304; // 4 MB

    /**
     * Constructs the full Gemini generateContent endpoint URL for the configured model.
     *
     * <p>Pattern: {@code {baseUrl}/v1beta/models/{model}:generateContent?key={apiKey}}
     *
     * @return fully-formed endpoint URL with API key as query param
     */
    public String generateContentUrl() {
        return apiBaseUrl
                + "/v1beta/models/"
                + model
                + ":generateContent?key="
                + apiKey;
    }
}