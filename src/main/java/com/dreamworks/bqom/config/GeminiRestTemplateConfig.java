package com.dreamworks.bqom.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Dedicated {@link RestTemplate} bean for the Gemini Flash API.
 *
 * <h2>Why a separate bean?</h2>
 * <p>The existing {@code RestTemplate} (defined in {@code RestTemplateConfig}) is
 * shared by Meta, Telegram, WhatsApp, and Supabase clients. Its timeouts (5s
 * connect, 10s read) are tuned for fast JSON APIs.
 *
 * <p>Gemini Flash with multimodal (image) input has a different latency profile:
 * <ul>
 *   <li>Connection is fast — same Google CDN</li>
 *   <li>Read can be 5–15s for image analysis + generation</li>
 *   <li>Larger request bodies (base64 image) require a longer write timeout</li>
 * </ul>
 *
 * <p>Using a separate bean means Gemini timeouts can be tuned independently
 * without affecting notification delivery or Meta API calls.
 *
 * <h2>Bean naming</h2>
 * <p>Named {@code "geminiRestTemplate"} and injected by qualifier in
 * {@link com.dreamworks.bqom.service.instagram.GeminiApiClient}.
 *
 * <h2>Timeouts sourced from config</h2>
 * <p>Both connect and read timeouts are read from {@link GeminiProperties}
 * (bound to {@code gemini.connect-timeout-seconds} and
 * {@code gemini.read-timeout-seconds}) so they can be tuned via environment
 * variables without a rebuild.
 */
@Configuration
@Slf4j
public class GeminiRestTemplateConfig {

    @Bean(name = "geminiRestTemplate")
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder,
                                           GeminiProperties geminiProperties) {

        int connectTimeoutSeconds = geminiProperties.getConnectTimeoutSeconds();
        int readTimeoutSeconds    = geminiProperties.getReadTimeoutSeconds();

        log.info("[GeminiConfig] Creating geminiRestTemplate: connectTimeout={}s, readTimeout={}s",
                connectTimeoutSeconds, readTimeoutSeconds);

        return builder
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
    }
}