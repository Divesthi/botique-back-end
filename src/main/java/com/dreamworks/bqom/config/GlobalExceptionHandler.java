package com.dreamworks.bqom.config;

import com.dreamworks.bqom.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <h2>Handled cases</h2>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — JSR-380 {@code @Valid} failures
 *       (e.g., null {@code channel}, unknown enum value). Returns {@code 400}
 *       with a structured list of field errors so the client knows exactly
 *       which field failed and why.</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON in the request
 *       body (e.g., unrecognised enum value that Jackson cannot deserialise,
 *       syntax errors). Returns {@code 400}.</li>
 *   <li>{@link EncryptionService.EncryptionException} — AES-256-GCM failure
 *       during credential encrypt/decrypt. Returns {@code 500} with a safe
 *       message — never exposes internal crypto details to the caller.</li>
 *   <li>Catch-all {@link Exception} — returns {@code 500} with a safe message
 *       and logs the full stack trace for operator visibility.</li>
 * </ul>
 *
 * <h2>Response shape</h2>
 * All error responses share the same envelope:
 * <pre>
 * {
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "Validation failed",
 *   "timestamp": "2024-07-01T10:30:00Z",
 *   "errors":    [ { "field": "notifications.channel", "message": "must not be null" } ]
 * }
 * </pre>
 * The {@code errors} array is omitted for non-validation failures.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── JSR-380 validation failures ───────────────────────────────────────────

    /**
     * Handles {@code @Valid} failures on {@code @RequestBody} parameters.
     *
     * <p>Collects all field errors into a list so clients receive the complete
     * picture in a single response (rather than fixing one field at a time).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorMap)
                .collect(Collectors.toList());

        log.warn("[Validation] Request failed validation: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorBody(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed",
                        fieldErrors
                ));
    }

    // ── malformed JSON / unrecognised enum ────────────────────────────────────

    /**
     * Handles unreadable request bodies — malformed JSON syntax or an enum
     * value that Jackson cannot map (e.g., {@code "channel": "fax"}).
     *
     * <p>The root cause message is included to give the client actionable
     * feedback without leaking internal class names.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {

        String safeMessage = extractSafeMessage(ex);
        log.warn("[Parsing] Unreadable HTTP message: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorBody(HttpStatus.BAD_REQUEST, safeMessage, null));
    }

    // ── encryption failures ───────────────────────────────────────────────────

    /**
     * Handles AES-256-GCM encryption/decryption failures.
     *
     * <p>Returns {@code 500} with a safe, generic message. The full exception
     * is logged for operators — never exposed to the client because it may
     * contain information about the crypto setup.
     */
    @ExceptionHandler(EncryptionService.EncryptionException.class)
    public ResponseEntity<Map<String, Object>> handleEncryptionException(
            EncryptionService.EncryptionException ex) {

        log.error("[Encryption] Encryption/decryption failure: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "A credential processing error occurred. " +
                                "Please contact your administrator.",
                        null
                ));
    }

    // ── catch-all ─────────────────────────────────────────────────────────────

    /**
     * Catch-all for any unhandled exception. Logs the full stack trace and
     * returns a generic {@code 500} — no internal details leak to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("[Unhandled] Unexpected exception: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again later.",
                        null
                ));
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Builds the standard error response envelope.
     * {@code errors} is omitted when {@code null} (non-validation failures).
     */
    private Map<String, Object> buildErrorBody(HttpStatus status,
                                               String message,
                                               List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("timestamp", OffsetDateTime.now().toString());
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        return body;
    }

    /**
     * Converts a Spring {@link FieldError} into a simple two-key map
     * ({@code field}, {@code message}) suitable for JSON serialisation.
     */
    private Map<String, String> toFieldErrorMap(FieldError fieldError) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("field",   fieldError.getField());
        map.put("message", fieldError.getDefaultMessage());
        return map;
    }

    /**
     * Extracts a client-safe message from a {@link HttpMessageNotReadableException}.
     *
     * <p>Jackson's exception messages can be verbose and may include internal
     * class names. We surface just the most useful part — the invalid value
     * and the accepted values — if it's an enum parse failure, otherwise
     * return a generic "malformed JSON" message.
     */
    private String extractSafeMessage(HttpMessageNotReadableException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("not one of the values accepted for Enum class")) {
            // e.g., "...not one of the values accepted for Enum class: [whatsapp, telegram]"
            int idx = msg.indexOf("not one of the values");
            return "Invalid value: " + msg.substring(idx);
        }
        return "Malformed or unreadable JSON request body.";
    }
}