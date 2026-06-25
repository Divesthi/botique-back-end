package com.dreamworks.bqom.model.instagram;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Internal request model for an Instagram post operation.
 *
 * <p>This is NOT a {@code @RequestBody} DTO — it is assembled by the controller
 * from a {@code multipart/form-data} request and passed to the service layer.
 * Keeping it separate from the HTTP binding means the service has no dependency
 * on Spring MVC types.
 *
 * <h2>Post type rules</h2>
 * <ul>
 *   <li>1 image  → single-image Feed post (IMAGE container)</li>
 *   <li>2–10 images → Carousel Feed post (CAROUSEL container)</li>
 * </ul>
 */
@Data
@Builder
public class InstagramPostRequest {

    /** Tenant initiating the post — set by the controller from the path variable. */
    private String tenantCode;

    /**
     * Images to publish. Validated in the controller before this object is built:
     * <ul>
     *   <li>At least 1 image required.</li>
     *   <li>Maximum 10 images (Meta Carousel limit).</li>
     *   <li>Each file must be non-empty and an accepted MIME type.</li>
     * </ul>
     */
    private List<MultipartFile> images;

    /**
     * Optional caption. Empty string by default (caller may leave null).
     * Meta allows up to 2,200 characters — the service truncates if needed.
     */
    private String caption;
}