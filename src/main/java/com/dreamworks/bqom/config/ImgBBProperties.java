package com.dreamworks.bqom.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the ImgBB image hosting service.
 *
 * <h2>Why ImgBB?</h2>
 * <p>Meta's Content Publishing API requires images to be at a publicly
 * accessible HTTPS URL before a media container can be created. ImgBB
 * provides a free, CDN-backed image hosting API that returns permanent
 * public URLs, making it a lightweight bridge between multipart uploads
 * and Meta's API requirements.
 *
 * <h2>Configuration (application.properties)</h2>
 * <pre>
 * imgbb.api-key=${IMGBB_API_KEY}
 * imgbb.upload-url=https://api.imgbb.com/1/upload
 * imgbb.expiration-seconds=3600
 * </pre>
 *
 * <h2>Environment variables</h2>
 * <ul>
 *   <li>{@code IMGBB_API_KEY} — API key from your ImgBB account dashboard.</li>
 * </ul>
 *
 * <h2>Expiration</h2>
 * <p>{@code expirationSeconds} controls how long ImgBB retains the image.
 * Meta fetches the image during container creation, so a short TTL (e.g.,
 * 1 hour = 3600s) is sufficient and avoids permanent storage of customer
 * images on a third-party host. Set to {@code 0} for permanent retention.
 */
@Configuration
@ConfigurationProperties(prefix = "imgbb")
@Validated
@Getter
@Setter
public class ImgBBProperties {

    /**
     * ImgBB API key. Sourced from {@code IMGBB_API_KEY} env var.
     * Never log or expose this value.
     */
    @NotBlank(message = "ImgBB API key must be configured via imgbb.api-key")
    private String apiKey;

    /**
     * Base URL for the ImgBB upload endpoint.
     * Defaults to the standard API endpoint; override for testing.
     */
    private String uploadUrl = "https://api.imgbb.com/1/upload";

    /**
     * Image expiration in seconds after upload.
     * {@code 0} = permanent (not recommended for PII/customer images).
     * {@code 3600} = 1 hour — sufficient for Meta to fetch during container creation.
     */
    private int expirationSeconds = 3600;

    /**
     * Maximum allowed file size in bytes for a single image upload.
     * ImgBB's free tier supports up to 32MB. We enforce a lower limit
     * to keep uploads fast and avoid memory pressure on the server.
     * Default: 10MB.
     */
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    /**
     * Maximum number of images allowed in a single post request.
     * Meta Carousel supports 2–10 items. Single-image feed posts need exactly 1.
     * We cap at 10 to match Meta's carousel limit.
     */
    private int maxImagesPerPost = 10;
}