package com.dreamworks.bqom.exception;

/**
 * Exception hierarchy for AI caption generation failures.
 *
 * <h2>Design rationale</h2>
 * <p>Caption generation is a non-critical path — failures must NEVER block
 * the Instagram post from being published. All exceptions in this hierarchy
 * are caught by {@link com.dreamworks.bqom.service.instagram.CaptionGenerationService}
 * which logs them and returns an empty caption, allowing the post to proceed.
 *
 * <h2>Exception types</h2>
 * <ul>
 *   <li>{@link GeminiApiException} — Gemini API returned an error response</li>
 *   <li>{@link GeminiTimeoutException} — Gemini API call timed out</li>
 *   <li>{@link GeminiParseException} — Gemini response could not be parsed</li>
 *   <li>{@link ImagePreparationException} — Image could not be base64-encoded</li>
 * </ul>
 */
public class CaptionGenerationException extends RuntimeException {

    private final String tenantCode;
    private final String errorCode;

    public CaptionGenerationException(String tenantCode, String errorCode, String message) {
        super(message);
        this.tenantCode = tenantCode;
        this.errorCode  = errorCode;
    }

    public CaptionGenerationException(String tenantCode, String errorCode,
                                      String message, Throwable cause) {
        super(message, cause);
        this.tenantCode = tenantCode;
        this.errorCode  = errorCode;
    }

    public String getTenantCode() { return tenantCode; }
    public String getErrorCode()  { return errorCode; }

    // ── Subtypes ──────────────────────────────────────────────────────────────

    /**
     * Thrown when the Gemini API returns a non-2xx HTTP status or an
     * application-level error in the response body.
     */
    public static class GeminiApiException extends CaptionGenerationException {
        private final int httpStatus;

        public GeminiApiException(String tenantCode, int httpStatus, String message) {
            super(tenantCode, "gemini_api_error", message);
            this.httpStatus = httpStatus;
        }

        public GeminiApiException(String tenantCode, int httpStatus,
                                  String message, Throwable cause) {
            super(tenantCode, "gemini_api_error", message, cause);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() { return httpStatus; }
    }

    /**
     * Thrown when the Gemini API call exceeds the configured read/connect timeout.
     */
    public static class GeminiTimeoutException extends CaptionGenerationException {
        public GeminiTimeoutException(String tenantCode, String message, Throwable cause) {
            super(tenantCode, "gemini_timeout", message, cause);
        }
    }

    /**
     * Thrown when the Gemini response body cannot be parsed into a usable caption.
     * This should be rare; indicates an unexpected change in Gemini's response schema.
     */
    public static class GeminiParseException extends CaptionGenerationException {
        public GeminiParseException(String tenantCode, String message, Throwable cause) {
            super(tenantCode, "gemini_parse_error", message, cause);
        }
    }

    /**
     * Thrown when the first image cannot be read or base64-encoded for the
     * Gemini API request. Typically caused by a corrupt or empty temp file.
     */
    public static class ImagePreparationException extends CaptionGenerationException {
        public ImagePreparationException(String tenantCode, String message, Throwable cause) {
            super(tenantCode, "image_preparation_error", message, cause);
        }
    }
}