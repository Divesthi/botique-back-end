package com.dreamworks.bqom.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Application-wide {@link RestTemplate} configuration.
 *
 * <h2>Why a shared bean?</h2>
 * <p>{@code RestTemplate} instances are thread-safe and expensive to construct
 * (they initialise a full set of message converters and interceptors on each
 * {@code new RestTemplate()} call). A singleton bean shared across all services
 * that make outbound HTTP calls ({@link com.dreamworks.bqom.service.SupabaseAdminClient},
 * {@link com.dreamworks.bqom.service.notification.TelegramNotificationStrategy},
 * {@link com.dreamworks.bqom.service.notification.WhatsAppNotificationStrategy})
 * avoids this overhead.
 *
 * <h2>Timeouts</h2>
 * <p>Connect and read timeouts are set conservatively for notification
 * delivery calls:
 * <ul>
 *   <li><b>Connect timeout (5 s):</b> how long to wait for the TCP handshake.</li>
 *   <li><b>Read timeout (10 s):</b> how long to wait for the API to respond.
 *       Telegram and WhatsApp APIs are typically sub-second; 10 s provides
 *       headroom without blocking an {@code @Async} thread indefinitely.</li>
 * </ul>
 *
 * <p>If different services need different timeout profiles, create additional
 * named beans (e.g., {@code @Bean("supabaseRestTemplate")}) and qualify
 * injection sites with {@code @Qualifier}.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}