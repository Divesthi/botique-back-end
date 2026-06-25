package com.dreamworks.bqom.exception;

import com.dreamworks.bqom.service.instagram.InstagramOAuthService;

/**
 * Exception hierarchy for the Instagram post publishing pipeline.
 *
 * <h2>Design rationale</h2>
 * <p>A dedicated hierarchy (separate from {@link InstagramOAuthService.InstagramOAuthException})
 * keeps the OAuth flow and the content publishing flow independently evolvable.
 * The controller maps each subtype to a precise HTTP status and error body.
 *
 * <h2>Hierarchy</h2>
 * <pre>
 * InstagramPostException                 (base — HTTP 500 if unhandled)
 *   ├── InvalidPostRequestException      (HTTP 400 — bad input from frontend)
 *   ├── TenantNotConnectedException      (HTTP 409 — no active IG config)
 *   ├── InsufficientPermissionException  (HTTP 403 — token missing scope)
 *   ├── ImgBBUploadException             (HTTP 502 — image hosting failure)
 *   └── MetaApiPostException             (HTTP 502 — Meta Graph API failure)
 * </pre>
 */
public class InstagramPostException extends RuntimeException {

    private final String tenantCode;
    private final String errorCode;

    public InstagramPostException(String tenantCode, String errorCode, String message) {
        super(message);
        this.tenantCode = tenantCode;
        this.errorCode  = errorCode;
    }

    public InstagramPostException(String tenantCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.tenantCode = tenantCode;
        this.errorCode  = errorCode;
    }

    public String getTenantCode() { return tenantCode; }
    public String getErrorCode()  { return errorCode; }

    // ── Subtypes ───────────────────────────────────────────────────────────────

    /**
     * Thrown when the incoming request is structurally invalid — wrong number
     * of images, unsupported MIME type, file too large, etc.
     * Maps to HTTP 400 Bad Request.
     */
    public static class InvalidPostRequestException extends InstagramPostException {
        public InvalidPostRequestException(String tenantCode, String message) {
            super(tenantCode, "invalid_request", message);
        }
    }

    /**
     * Thrown when no active Instagram config exists for the tenant.
     * The admin must complete the OAuth flow before posting.
     * Maps to HTTP 409 Conflict.
     */
    public static class TenantNotConnectedException extends InstagramPostException {
        public TenantNotConnectedException(String tenantCode) {
            super(tenantCode, "tenant_not_connected",
                    "No active Instagram account connected for tenant: " + tenantCode +
                            ". Complete OAuth onboarding at /instagram/auth-url first.");
        }
    }

    /**
     * Thrown when Meta rejects the API call with an OAuthException indicating
     * a missing permission scope (typically {@code instagram_content_publish}).
     * Maps to HTTP 403 Forbidden.
     */
    public static class InsufficientPermissionException extends InstagramPostException {
        public InsufficientPermissionException(String tenantCode, String missingScope) {
            super(tenantCode, "insufficient_permission",
                    "Instagram permission '" + missingScope + "' is missing for tenant: " +
                            tenantCode + ". Re-authenticate via /instagram/auth-url to grant it.");
        }
    }

    /**
     * Thrown when ImgBB fails to host an individual image.
     * This is a per-image error — the pipeline continues with remaining images
     * (partial-success semantics). The service catches and records these;
     * only if ALL images fail is this exception propagated to abort the request.
     */
    public static class ImgBBUploadException extends InstagramPostException {
        private final String fileName;

        public ImgBBUploadException(String tenantCode, String fileName, String message, Throwable cause) {
            super(tenantCode, "imgbb_upload_failed", message, cause);
            this.fileName = fileName;
        }

        public ImgBBUploadException(String tenantCode, String fileName, String message) {
            super(tenantCode, "imgbb_upload_failed", message);
            this.fileName = fileName;
        }

        public String getFileName() { return fileName; }
    }

    /**
     * Thrown when Meta's Graph API rejects a media container creation or
     * publish call. Carries Meta's numeric error code for operator debugging.
     * Maps to HTTP 502 Bad Gateway.
     */
    public static class MetaApiPostException extends InstagramPostException {
        private final int metaErrorCode;

        public MetaApiPostException(String tenantCode, int metaErrorCode, String message) {
            super(tenantCode, "meta_api_error", message);
            this.metaErrorCode = metaErrorCode;
        }

        public MetaApiPostException(String tenantCode, int metaErrorCode, String message, Throwable cause) {
            super(tenantCode, "meta_api_error", message, cause);
            this.metaErrorCode = metaErrorCode;
        }

        public int getMetaErrorCode() { return metaErrorCode; }
    }
}