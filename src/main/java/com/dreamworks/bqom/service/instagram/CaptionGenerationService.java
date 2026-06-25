package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.config.GeminiProperties;
import com.dreamworks.bqom.exception.CaptionGenerationException;
import com.dreamworks.bqom.exception.CaptionGenerationException.ImagePreparationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * Orchestrates AI-powered Instagram caption generation via Gemini Flash.
 *
 * <h2>Responsibility</h2>
 * <p>This service sits between {@link InstagramPostService} and
 * {@link GeminiApiClient}. It owns:
 * <ul>
 *   <li>Prompt construction — deterministic, version-controlled boutique prompt</li>
 *   <li>Image preparation — reading bytes, base64 encoding, MIME type resolution</li>
 *   <li>Caption post-processing — trimming, length enforcement</li>
 *   <li>Graceful degradation — any failure returns {@code null}; the post
 *       continues without a caption rather than blocking publishing</li>
 * </ul>
 *
 * <h2>Graceful degradation contract</h2>
 * <p>{@link #generateCaption} NEVER throws. All exceptions are caught, logged
 * with full context, and {@code null} is returned. The caller
 * ({@link InstagramPostService}) treats {@code null} as "proceed without caption".
 *
 * <h2>Prompt design</h2>
 * <p>The prompt is structured for consistent, professional boutique-style output:
 * <ol>
 *   <li>Role context — tell Gemini it is a boutique social media expert</li>
 *   <li>Task — describe the image, create an engaging caption</li>
 *   <li>Style constraints — professional, promotional, elegant</li>
 *   <li>Output format — explicit format to make parsing reliable</li>
 * </ol>
 *
 * <h2>Image size guard</h2>
 * <p>Images larger than {@link GeminiProperties#getMaxImageSizeBytes()} are
 * rejected before the API call to avoid wasting quota on oversized payloads.
 * The post continues without a caption in this case.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaptionGenerationService {

    // Instagram's max caption length
    private static final int MAX_CAPTION_LENGTH = 2200;

    // Supported MIME types for Gemini inline_data
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_PNG  = "image/png";
    private static final String MIME_WEBP = "image/webp";

    // Default MIME type when content-type header is absent
    private static final String DEFAULT_MIME = MIME_JPEG;

    private final GeminiApiClient   geminiApiClient;
    private final GeminiProperties  geminiProperties;

    /**
     * Generates a professional promotional caption for an Instagram post
     * by sending the first image to Gemini Flash.
     *
     * <p><b>Contract: this method never throws.</b> Any failure (Gemini timeout,
     * quota exceeded, image read error, parse error) is caught, logged, and
     * {@code null} is returned so the post proceeds without a caption.
     *
     * @param firstImage  the first image from the post request (used as visual context)
     * @param tenantCode  for structured logging
     * @return generated caption string, or {@code null} if generation failed
     */
    public String generateCaption(MultipartFile firstImage, String tenantCode) {
        log.info("[CaptionGen] Starting caption generation for tenant={}, image={}",
                tenantCode, firstImage.getOriginalFilename());

        try {
            // Step 1: Prepare image — validate size, read bytes, base64 encode
            String base64Image = prepareImage(firstImage, tenantCode);

            // Step 2: Resolve MIME type
            String mimeType = resolveMimeType(firstImage);

            // Step 3: Build prompt
            String prompt = buildPrompt();

            // Step 4: Call Gemini
            String rawResponse = geminiApiClient.generateContent(
                    base64Image, mimeType, prompt, tenantCode);

            // Step 5: Post-process and return
            String caption = postProcess(rawResponse, tenantCode);

            log.info("[CaptionGen] Caption generated successfully for tenant={}, length={}",
                    tenantCode, caption != null ? caption.length() : 0);

            return caption;

        } catch (CaptionGenerationException e) {
            // Structured failures — log with error code for monitoring
            log.error("[CaptionGen] Caption generation failed for tenant={}: [{}] {} — " +
                            "proceeding with empty caption",
                    tenantCode, e.getErrorCode(), e.getMessage());
            return null;

        } catch (Exception e) {
            // Truly unexpected — must never surface to the caller
            log.error("[CaptionGen] Unexpected error generating caption for tenant={} — " +
                            "proceeding with empty caption",
                    tenantCode, e);
            return null;
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Reads and base64-encodes the image file for Gemini's inline_data format.
     *
     * <p>Guards against oversized images before reading to avoid OOM on huge files.
     * The encoded string uses standard Base64 (not URL-safe) as required by Gemini.
     *
     * @throws ImagePreparationException if the file cannot be read or is too large
     */
    private String prepareImage(MultipartFile image, String tenantCode) {
        // Size guard — reject oversized images before reading bytes
        if (image.getSize() > geminiProperties.getMaxImageSizeBytes()) {
            long maxMb = geminiProperties.getMaxImageSizeBytes() / (1024 * 1024);
            log.warn("[CaptionGen] Image too large for Gemini for tenant={}: size={}bytes, max={}MB",
                    tenantCode, image.getSize(), maxMb);
            throw new ImagePreparationException(tenantCode,
                    "Image exceeds maximum size (" + maxMb + "MB) for caption generation", null);
        }

        try {
            byte[] imageBytes = image.getBytes();
            // Gemini requires standard Base64, not URL-safe Base64
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            log.error("[CaptionGen] Failed to read image bytes for tenant={}: {}",
                    tenantCode, e.getMessage(), e);
            throw new ImagePreparationException(tenantCode,
                    "Failed to read image for caption generation: " + image.getOriginalFilename(), e);
        }
    }

    /**
     * Resolves the MIME type from the MultipartFile's content-type header.
     *
     * <p>Falls back to {@code image/jpeg} if the content-type is absent or
     * unrecognised — Gemini handles this gracefully in most cases.
     */
    private String resolveMimeType(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_MIME;
        }
        // Normalise to lowercase and strip charset if present
        String normalised = contentType.split(";")[0].trim().toLowerCase();
        return switch (normalised) {
            case "image/jpeg", "image/jpg" -> MIME_JPEG;
            case "image/png"               -> MIME_PNG;
            case "image/webp"              -> MIME_WEBP;
            default -> {
                log.warn("[CaptionGen] Unrecognised MIME type '{}', defaulting to {}",
                        contentType, DEFAULT_MIME);
                yield DEFAULT_MIME;
            }
        };
    }

    /**
     * Builds the Gemini prompt for boutique Instagram caption generation.
     *
     * <h2>Prompt design principles</h2>
     * <ul>
     *   <li><b>Role framing</b> — establishes Gemini as a boutique marketing expert</li>
     *   <li><b>Explicit constraints</b> — tone, length, hashtag count</li>
     *   <li><b>Structured output instruction</b> — caption on line 1, hashtags on line 2,
     *       making {@link #postProcess} reliable without regex</li>
     *   <li><b>Emoji guidance</b> — adds warmth while staying professional</li>
     * </ul>
     *
     * <p>The prompt is kept in code (not a config property) intentionally —
     * prompt engineering is a code concern and should be version-controlled,
     * reviewed, and tested like any other logic.
     */
    private String buildPrompt() {
        return """
                You are an elite, high-end social media strategist for a luxury fashion boutique specializing in premium, authentic textiles.\s
                
                Analyze the provided image and write a stunning, magazine-quality Instagram caption.
                
                Strict Writing Guidelines:
                1. Tone: Sophisticated, sensory, and quietly confident. Avoid sounding like a loud salesperson.
                2. Vocabulary: Do NOT use generic marketing clichés like "embrace", "timeless elegance", "exquisite", "must-have", or "tradition". Instead, use sensory words that describe the actual visual texture, drape, weave, or craftsmanship visible in the image.
                3. Structure:\s
                   - Line 1: A powerful, elegant opening hook.
                   - Line 2-3: A deep dive into the craftsmanship, fabric details, or how the piece makes the wearer feel. End this section with 1 or 2 minimal, tasteful emojis (e.g., ✨, 🧵, 🌾).
                4. Formatting: Output the caption, followed by a single empty line, followed by exactly 5 hyper-specific, premium hashtags (e.g., #HandloomSaree, #KanjeevaramSilk, rather than generic tags like #fashion).
                
                Output constraints: Provide ONLY the requested caption and hashtags. No introductions, no pleasantries, and do not URL-encode the text.
                """;
    }

    /**
     * Post-processes Gemini's raw text response into a clean Instagram caption.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Trim surrounding whitespace and markdown artifacts</li>
     *   <li>Remove any accidental code block markers (```)</li>
     *   <li>Enforce Instagram's 2200-character limit with a safe truncation</li>
     * </ol>
     *
     * <p>Returns {@code null} if the processed caption is blank — the caller
     * will proceed with an empty caption in that case.
     */
    private String postProcess(String rawText, String tenantCode) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        // Remove markdown code block delimiters Gemini sometimes wraps output in
        String cleaned = rawText
                .replace("```", "")
                .trim();

        if (cleaned.isBlank()) {
            log.warn("[CaptionGen] Post-processing yielded empty caption for tenant={}",
                    tenantCode);
            return null;
        }

        // Enforce Instagram's 2200-char limit
        if (cleaned.length() > MAX_CAPTION_LENGTH) {
            log.warn("[CaptionGen] Caption truncated from {} to {} chars for tenant={}",
                    cleaned.length(), MAX_CAPTION_LENGTH, tenantCode);
            // Truncate at the last space before the limit to avoid mid-word cuts
            cleaned = cleaned.substring(0, MAX_CAPTION_LENGTH).stripTrailing();
        }

        return cleaned;
    }
}