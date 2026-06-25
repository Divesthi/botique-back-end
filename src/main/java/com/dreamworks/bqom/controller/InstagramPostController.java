package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.exception.InstagramPostException;
import com.dreamworks.bqom.exception.InstagramPostException.*;
import com.dreamworks.bqom.model.instagram.InstagramPostRequest;
import com.dreamworks.bqom.model.instagram.InstagramPostResponse;
import com.dreamworks.bqom.service.instagram.InstagramPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.dreamworks.bqom.model.instagram.TempFileMultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * REST controller for Instagram content publishing.
 *
 * <h2>Endpoint</h2>
 * <pre>
 * POST /v1/bqom/tenants/{tenantCode}/instagram/posts
 * Content-Type: multipart/form-data
 *
 * Form fields:
 *   images[]  — 1 to 10 image files (JPEG or PNG, max 10MB each)
 *   caption   — optional post caption (max 2,200 chars; ignored for now)
 * </pre>
 *
 * <h2>Response contract</h2>
 * <ul>
 *   <li>HTTP 202 Accepted — publishing process successfully initiated in the background.</li>
 *   <li>HTTP 400 Bad Request — invalid input (no images or too many images).</li>
 *   <li>HTTP 409 Conflict — tenant has not connected their Instagram account.</li>
 * </ul>
 *
 * <h2>Async design</h2>
 * <p>{@link InstagramPostService#publishAsync} runs on a background thread.
 * The controller performs fast synchronous validation and checks that the tenant is
 * connected, and then immediately returns HTTP 202 Accepted. The actual publishing
 * is completed asynchronously in a separate task executor thread pool, avoiding
 * network/gateway timeouts.
 *
 * <h2>Security</h2>
 * <p>Tenant isolation is enforced by {@link com.dreamworks.bqom.security.SupabaseJwtAuthenticationFilter}
 * which verifies the URL {@code tenantCode} matches the JWT's tenant.
 * {@code TENANT_ADMIN} role is required (enforced by
 * {@link com.dreamworks.bqom.security.RoleAuthorizationInterceptor} via
 * {@link com.dreamworks.bqom.security.RequireRole}).
 */
@RestController
@RequestMapping(path = "/v1/bqom/tenants/{tenantCode}/instagram", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class InstagramPostController {

    private final InstagramPostService instagramPostService;

    /**
     * Initiates the publishing of images to the tenant's connected Instagram account asynchronously.
     *
     * @param tenantCode path variable — tenant initiating the post
     * @param images     1–10 image files (JPEG/PNG, max 10MB each)
     * @param caption    optional caption (may be omitted)
     * @return HTTP 202 Accepted, 400 Bad Request, or 409 Conflict
     */
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> publishPost(
            @PathVariable("tenantCode") String tenantCode,
            @RequestPart("images") List<MultipartFile> images,
            @RequestPart(value = "caption", required = false) String caption) {

        log.info("[InstagramPost] POST /posts received for tenant={}, imageCount={}",
                tenantCode, images != null ? images.size() : 0);

        // ── Early input guard — fast fail before spinning up async work ────────
        if (images == null || images.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody(
                    "invalid_request",
                    "At least one image is required.",
                    HttpStatus.BAD_REQUEST.value()));
        }

        if (images.size() > 10) {
            return ResponseEntity.badRequest().body(errorBody(
                    "invalid_request",
                    "Maximum 10 images allowed per post. Received: " + images.size(),
                    HttpStatus.BAD_REQUEST.value()));
        }

        // ── Check if tenant is connected (fast fail before async execution) ──
        try {
            instagramPostService.loadActiveConfig(tenantCode);
        } catch (TenantNotConnectedException e) {
            return mapExceptionToHttp(e, tenantCode);
        }

        // ── Convert files to temporary files on disk to survive request thread termination without OOM ──
        List<MultipartFile> tempFiles = new ArrayList<>();
        try {
            for (MultipartFile img : images) {
                File tempFile = File.createTempFile("ig-upload-", ".tmp");
                img.transferTo(tempFile);

                tempFiles.add(new TempFileMultipartFile(
                        img.getName(),
                        img.getOriginalFilename(),
                        img.getContentType(),
                        tempFile
                ));
            }
        } catch (IOException e) {
            log.error("[InstagramPost] Failed to write temporary files for tenant={}", tenantCode, e);
            // Clean up any files that were already created before the error
            for (MultipartFile file : tempFiles) {
                if (file instanceof TempFileMultipartFile tempFileMultipart) {
                    tempFileMultipart.clean();
                }
            }
            return ResponseEntity.badRequest().body(errorBody(
                    "invalid_request",
                    "Failed to process uploaded images. Please try again.",
                    HttpStatus.BAD_REQUEST.value()));
        }

        // ── Build service request ──────────────────────────────────────────────
        InstagramPostRequest request = InstagramPostRequest.builder()
                .tenantCode(tenantCode)
                .images(tempFiles)
                .caption(caption)
                .build();

        // ── Submit async work ──────────────────────────────────────────────────
        CompletableFuture<InstagramPostResponse> future =
                instagramPostService.publishAsync(request);

        // Handle outcomes in the background thread (logging/monitoring)
        future.whenComplete((response, ex) -> {
            if (ex != null) {
                log.error("[InstagramPost] Background publishing failed for tenant={}", tenantCode, ex);
            } else {
                log.info("[InstagramPost] Background publishing completed for tenant={}: status={}, igPostId={}",
                        tenantCode, response.getStatus(), response.getIgPostId());
            }
        });

        // Return HTTP 202 Accepted immediately
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "PENDING",
                "message", "Post publishing has been initiated in the background."
        ));
    }

    // ── Response mapping ───────────────────────────────────────────────────────

    /**
     * Maps the service response to the correct HTTP status code.
     *
     * <ul>
     *   <li>SUCCESS  → 200 OK</li>
     *   <li>PARTIAL  → 207 Multi-Status</li>
     *   <li>FAILED   → 422 Unprocessable Entity</li>
     * </ul>
     */
    private ResponseEntity<?> mapResponseToHttp(InstagramPostResponse response) {
        return switch (response.getStatus()) {
            case SUCCESS -> ResponseEntity.ok(response);
            case PARTIAL -> ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
            case FAILED  -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        };
    }

    /**
     * Maps checked exceptions from the async future to structured HTTP error responses.
     * Uses the exception hierarchy from {@link InstagramPostException}.
     */
    private ResponseEntity<?> mapExceptionToHttp(Throwable cause, String tenantCode) {
        if (cause instanceof InvalidPostRequestException e) {
            log.warn("[InstagramPost] Invalid request for tenant={}: {}", tenantCode, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(errorBody(e.getErrorCode(), e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        }

        if (cause instanceof TenantNotConnectedException e) {
            log.warn("[InstagramPost] Tenant not connected: {}", tenantCode);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(errorBody(e.getErrorCode(), e.getMessage(), HttpStatus.CONFLICT.value()));
        }

        if (cause instanceof InsufficientPermissionException e) {
            log.warn("[InstagramPost] Insufficient permissions for tenant={}: {}",
                    tenantCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorBody(e.getErrorCode(), e.getMessage(), HttpStatus.FORBIDDEN.value()));
        }

        if (cause instanceof MetaApiPostException e) {
            log.error("[InstagramPost] Meta API error for tenant={}: [code={}] {}",
                    tenantCode, e.getMetaErrorCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(errorBody(e.getErrorCode(), e.getMessage(), HttpStatus.BAD_GATEWAY.value()));
        }

        if (cause instanceof InstagramPostException e) {
            log.error("[InstagramPost] Post exception for tenant={}: [{}] {}",
                    tenantCode, e.getErrorCode(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(e.getErrorCode(), e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }

        // Truly unexpected errors
        log.error("[InstagramPost] Unexpected error for tenant={}", tenantCode, cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("unexpected_error",
                        "An unexpected error occurred. Please try again later.",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    /** Builds a consistent error body map that matches the project's existing error envelope. */
    private Map<String, Object> errorBody(String errorCode, String message, int status) {
        return Map.of(
                "error",     true,
                "errorCode", errorCode,
                "message",   message,
                "status",    status
        );
    }
}