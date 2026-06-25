package com.dreamworks.bqom.model.instagram;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API response for a POST /instagram/posts request.
 *
 * <h2>Status semantics</h2>
 * <ul>
 *   <li>{@code SUCCESS} — all images uploaded and published successfully.</li>
 *   <li>{@code PARTIAL} — at least one image failed, but the post was still
 *       published with the successfully uploaded images. The {@code failedImages}
 *       list details which files failed and why.</li>
 *   <li>{@code FAILED} — all images failed; no post was created.</li>
 * </ul>
 *
 * <h2>Frontend contract</h2>
 * <p>The response is returned synchronously with HTTP 202 (Accepted) because
 * the heavy work runs {@code @Async}. The status field tells the frontend
 * whether to show a success toast, a partial-warning banner, or a full error.
 */
@Data
@Builder
public class InstagramPostResponse {

    public enum PostStatus {
        SUCCESS,
        PARTIAL,
        FAILED
    }

    /** Overall outcome of the post operation. */
    private PostStatus status;

    /**
     * Instagram post/media ID returned by Meta after successful publish.
     * {@code null} when {@code status == FAILED}.
     */
    private String igPostId;

    /**
     * Type of post that was created.
     * {@code "FEED"} for single-image or carousel feed posts.
     * {@code null} when {@code status == FAILED}.
     */
    private String postType;

    /**
     * Total number of images submitted in the request.
     */
    private int totalImagesRequested;

    /**
     * Number of images successfully uploaded to ImgBB and included in the post.
     */
    private int imagesPublished;

    /**
     * Detail of each image that failed during upload or container creation.
     * Empty list when {@code status == SUCCESS}.
     */
    private List<FailedImageDetail> failedImages;

    /** Server-side timestamp when the async job completed. */
    private OffsetDateTime completedAt;

    /** Human-readable summary message for the frontend. */
    private String message;

    // ── Nested DTO ─────────────────────────────────────────────────────────────

    /**
     * Detail record for an image that failed at any stage of the pipeline.
     */
    @Data
    @Builder
    public static class FailedImageDetail {

        /** Original filename as received from the frontend. */
        private String fileName;

        /**
         * Zero-based index of this image in the original request list.
         * Helps the frontend highlight the specific thumbnail that failed.
         */
        private int index;

        /**
         * Stage at which the failure occurred:
         * {@code "IMGBB_UPLOAD"}, {@code "META_CONTAINER"}, {@code "META_PUBLISH"}.
         */
        private String failureStage;

        /** Human-readable error message (safe to show in frontend). */
        private String reason;
    }
}