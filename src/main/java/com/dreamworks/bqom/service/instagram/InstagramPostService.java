package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.exception.InstagramPostException;
import com.dreamworks.bqom.exception.InstagramPostException.*;
import com.dreamworks.bqom.model.instagram.InstagramPostRequest;
import com.dreamworks.bqom.model.instagram.TempFileMultipartFile;
import com.dreamworks.bqom.model.instagram.InstagramPostResponse;
import com.dreamworks.bqom.model.instagram.InstagramPostResponse.FailedImageDetail;
import com.dreamworks.bqom.model.instagram.InstagramPostResponse.PostStatus;
import com.dreamworks.bqom.repository.TenantInstagramConfigRepository;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.entity.Tenant;
import com.dreamworks.bqom.repository.entity.TenantInstagramConfig;
import com.dreamworks.bqom.service.EncryptionService;
import com.dreamworks.bqom.service.NotificationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates Instagram content publishing — image hosting, optional AI caption
 * generation, Meta container creation, and publishing — with full partial-success semantics.
 *
 * <h2>Caption resolution order</h2>
 * <ol>
 *   <li>If the caller supplied a non-blank caption → use it as-is.</li>
 *   <li>If the caller supplied no caption AND the tenant has
 *       {@code preferences.instagram.autoCaption = true} →
 *       call {@link CaptionGenerationService} with the first image.</li>
 *   <li>If caption generation fails or returns blank → proceed with empty caption.</li>
 * </ol>
 *
 * <h2>Tenant preference key</h2>
 * <p>Checked via {@code tenant.preferences → instagram → autoCaption (Boolean)}.
 * If the key is absent or false, caption generation is skipped entirely.
 *
 * <h2>Post type decision</h2>
 * <ul>
 *   <li>1 image → single-image Feed post</li>
 *   <li>2–10 images → Carousel Feed post</li>
 * </ul>
 *
 * <h2>Partial-success semantics</h2>
 * <p>Images are uploaded to ImgBB independently. If one upload fails:
 * <ol>
 *   <li>The failure is recorded in {@code failedImages}.</li>
 *   <li>The remaining images continue processing.</li>
 *   <li>If ≥1 image succeeds, the post is published with the successful images.</li>
 *   <li>If ALL images fail, the method returns {@code FAILED} without calling Meta.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstagramPostService {

    // Meta allows 2–10 items in a carousel; we also accept 1 (single-image feed)
    private static final int MAX_IMAGES = 10;
    private static final int MAX_CAPTION_LENGTH = 2200;

    private final TenantInstagramConfigRepository instagramConfigRepository;
    private final TenantRepository                tenantRepository;
    private final EncryptionService               encryptionService;
    private final ImgBBService                    imgBBService;
    private final MetaGraphApiClient              metaGraphApiClient;
    private final CaptionGenerationService        captionGenerationService;

    /**
     * Publishes images to Instagram asynchronously.
     *
     * <p>The method is {@code @Async} — the caller receives a
     * {@link CompletableFuture} immediately and the actual work runs on
     * the {@code instagramPostExecutor} thread pool.
     *
     * @param request validated post request assembled by the controller
     * @return a future that resolves to the post outcome
     */
    @Async("instagramPostExecutor")
    public CompletableFuture<InstagramPostResponse> publishAsync(InstagramPostRequest request) {
        String tenantCode = request.getTenantCode();
        log.info("[InstagramPost] Starting async publish for tenant={}, imageCount={}",
                tenantCode, request.getImages().size());

        try {
            InstagramPostResponse response = publish(request);
            return CompletableFuture.completedFuture(response);

        } catch (InstagramPostException e) {
            log.error("[InstagramPost] Publish failed for tenant={}: [{}] {}",
                    tenantCode, e.getErrorCode(), e.getMessage());
            return CompletableFuture.failedFuture(e);

        } catch (Exception e) {
            log.error("[InstagramPost] Unexpected error for tenant={}", tenantCode, e);
            return CompletableFuture.failedFuture(
                    new InstagramPostException(tenantCode, "unexpected_error",
                            "An unexpected error occurred during Instagram publishing", e));
        } finally {
            cleanTempFiles(request.getImages());
        }
    }

    private void cleanTempFiles(List<MultipartFile> files) {
        if (files == null) return;
        for (MultipartFile file : files) {
            if (file instanceof TempFileMultipartFile tempFile) {
                try {
                    tempFile.clean();
                    log.debug("[InstagramPost] Cleaned up temporary file: {}",
                            tempFile.getOriginalFilename());
                } catch (Exception e) {
                    log.error("[InstagramPost] Failed to delete temporary file: {}",
                            tempFile.getOriginalFilename(), e);
                }
            }
        }
    }

    // ── Core orchestration (package-private for unit testing without @Async) ──

    InstagramPostResponse publish(InstagramPostRequest request) {
        String tenantCode  = request.getTenantCode();
        List<MultipartFile> images = request.getImages();

        // ── Step 0: Validate request ───────────────────────────────────────────
        validateRequest(request);

        // ── Step 1: Load and decrypt credentials ──────────────────────────────
        TenantInstagramConfig config = loadActiveConfig(tenantCode);
        String accessToken = decryptToken(config, tenantCode);
        String igUserId    = config.getIgUserId();

        // ── Step 2: Resolve caption ────────────────────────────────────────────
        // Priority: user-supplied → AI-generated → empty string
        String caption = resolveCaption(request, tenantCode);

        // ── Step 3: Upload all images to ImgBB (with partial-failure tracking) ─
        List<String>            publicUrls   = new ArrayList<>();
        List<FailedImageDetail> failedImages = new ArrayList<>();

        for (int i = 0; i < images.size(); i++) {
            MultipartFile file     = images.get(i);
            String        fileName = safeFileName(file, i);

            try {
                String publicUrl = imgBBService.upload(file, tenantCode);
                publicUrls.add(publicUrl);
                log.info("[InstagramPost] Image[{}]='{}' uploaded to ImgBB for tenant={}",
                        i, fileName, tenantCode);

            } catch (ImgBBUploadException e) {
                log.error("[InstagramPost] Image[{}]='{}' failed ImgBB upload for tenant={}: {}",
                        i, fileName, tenantCode, e.getMessage());
                failedImages.add(FailedImageDetail.builder()
                        .fileName(fileName)
                        .index(i)
                        .failureStage("IMGBB_UPLOAD")
                        .reason(e.getMessage())
                        .build());
            }
        }

        // ── Step 4: All images failed → abort ─────────────────────────────────
        if (publicUrls.isEmpty()) {
            log.error("[InstagramPost] All {} images failed upload for tenant={}",
                    images.size(), tenantCode);
            return InstagramPostResponse.builder()
                    .status(PostStatus.FAILED)
                    .totalImagesRequested(images.size())
                    .imagesPublished(0)
                    .failedImages(failedImages)
                    .completedAt(OffsetDateTime.now())
                    .message("All images failed to upload. No post was created.")
                    .build();
        }

        // ── Step 5: Create and publish on Meta ────────────────────────────────
        String igPostId;
        String postType;

        if (publicUrls.size() == 1) {
            igPostId = publishSingleImagePost(igUserId, publicUrls.get(0),
                    caption, accessToken, tenantCode);
            postType = "FEED";
        } else {
            igPostId = publishCarouselPost(igUserId, publicUrls,
                    caption, accessToken, tenantCode);
            postType = "CAROUSEL_FEED";
        }

        // ── Step 6: Build response ─────────────────────────────────────────────
        PostStatus status = failedImages.isEmpty() ? PostStatus.SUCCESS : PostStatus.PARTIAL;

        log.info("[InstagramPost] Publish complete for tenant={}: status={}, igPostId={}, " +
                        "published={}/{}, failed={}, captionSource={}",
                tenantCode, status, igPostId, publicUrls.size(), images.size(),
                failedImages.size(), captionSource(request.getCaption(), caption));

        return InstagramPostResponse.builder()
                .status(status)
                .igPostId(igPostId)
                .postType(postType)
                .totalImagesRequested(images.size())
                .imagesPublished(publicUrls.size())
                .failedImages(failedImages)
                .completedAt(OffsetDateTime.now())
                .message(buildSummaryMessage(status, publicUrls.size(),
                        failedImages.size(), images.size()))
                .build();
    }

    // ── Caption resolution ─────────────────────────────────────────────────────

    /**
     * Resolves the final caption to use for the post.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Non-blank caller-supplied caption → sanitize and return</li>
     *   <li>No caption + tenant {@code autoCaption} enabled → generate via Gemini</li>
     *   <li>Gemini returns null / autoCaption disabled → return empty string</li>
     * </ol>
     *
     * <p>This method never throws — Gemini failures are already swallowed by
     * {@link CaptionGenerationService#generateCaption}.
     */
    private String resolveCaption(InstagramPostRequest request, String tenantCode) {
        String suppliedCaption = request.getCaption();

        // Case 1: caller supplied a caption — use it
        if (StringUtils.isNotBlank(suppliedCaption)) {
            log.info("[InstagramPost] Using caller-supplied caption for tenant={}", tenantCode);
            return sanitizeCaption(suppliedCaption);
        }

        // Case 2: no caption — check tenant preference before calling Gemini
        if (!isAutoCaptionEnabled(tenantCode)) {
            log.info("[InstagramPost] autoCaption disabled for tenant={} — skipping Gemini",
                    tenantCode);
            return "";
        }

        // Case 3: autoCaption enabled — generate via Gemini using first image
        log.info("[InstagramPost] autoCaption enabled for tenant={} — calling Gemini", tenantCode);
        MultipartFile firstImage = request.getImages().get(0);
        String generated = captionGenerationService.generateCaption(firstImage, tenantCode);

        if (StringUtils.isBlank(generated)) {
            log.info("[InstagramPost] Gemini returned empty caption for tenant={} — " +
                    "proceeding without caption", tenantCode);
            return "";
        }

        log.info("[InstagramPost] AI caption generated for tenant={}, length={}",
                tenantCode, generated.length());
        return generated;
    }

    /**
     * Checks whether AI caption auto-generation is enabled for this tenant.
     *
     * <p>Reads {@code tenant.preferences.instagram.autoCaption}.
     * Missing key or non-boolean value → {@code false} (opt-in, not opt-out).
     *
     * <p>On any error (tenant not found, preferences malformed), returns
     * {@code false} and logs — never throws to the caller.
     */
    @SuppressWarnings("unchecked")
    private boolean isAutoCaptionEnabled(String tenantCode) {
        try {
            Tenant tenant = tenantRepository.findByCode(tenantCode).orElse(null);
            if (tenant == null || tenant.getPreferences() == null) {
                return false;
            }

            Map<String, Object> preferences = tenant.getPreferences();
            Object instagramPrefs = preferences.get(NotificationConstants.PREF_INSTAGRAM);

            if (!(instagramPrefs instanceof Map)) {
                return false;
            }

            Object autoCaptionValue = ((Map<String, Object>) instagramPrefs)
                    .get(NotificationConstants.PREF_AUTO_CAPTION);

            if (autoCaptionValue instanceof Boolean) {
                return (Boolean) autoCaptionValue;
            }

            // Handle string "true" / "false" stored from JSON
            if (autoCaptionValue instanceof String s) {
                return Boolean.parseBoolean(s);
            }

            return false;

        } catch (Exception e) {
            log.warn("[InstagramPost] Failed to read autoCaption preference for tenant={}: {} — " +
                    "defaulting to disabled", tenantCode, e.getMessage());
            return false;
        }
    }

    // ── Meta publish helpers ───────────────────────────────────────────────────

    private String publishSingleImagePost(String igUserId, String imageUrl,
                                          String caption, String accessToken,
                                          String tenantCode) {
        log.info("[InstagramPost] Publishing single-image feed post for tenant={}", tenantCode);
        String creationId = metaGraphApiClient.createSingleImageContainer(
                igUserId, imageUrl, caption, accessToken, tenantCode);
        return metaGraphApiClient.publishContainer(igUserId, creationId, accessToken, tenantCode);
    }

    private String publishCarouselPost(String igUserId, List<String> publicUrls,
                                       String caption, String accessToken,
                                       String tenantCode) {
        log.info("[InstagramPost] Publishing carousel post for tenant={}, itemCount={}",
                tenantCode, publicUrls.size());

        List<String> childIds = new ArrayList<>();
        for (int i = 0; i < publicUrls.size(); i++) {
            String url = publicUrls.get(i);
            try {
                String childId = metaGraphApiClient.createCarouselChildContainer(
                        igUserId, url, accessToken, tenantCode);
                childIds.add(childId);
                log.info("[InstagramPost] Carousel child[{}] container created: {}", i, childId);
            } catch (MetaApiPostException e) {
                log.error("[InstagramPost] Failed to create carousel child[{}] for tenant={}: {}",
                        i, tenantCode, e.getMessage());
            }
        }

        if (childIds.isEmpty()) {
            throw new MetaApiPostException(tenantCode, -1,
                    "All carousel child container creations failed for tenant: " + tenantCode);
        }

        if (childIds.size() == 1) {
            log.warn("[InstagramPost] Only 1 carousel child succeeded for tenant={}; " +
                    "degrading to single-image feed post", tenantCode);
            return metaGraphApiClient.publishContainer(
                    igUserId, childIds.get(0), accessToken, tenantCode);
        }

        String carouselContainerId = metaGraphApiClient.createCarouselContainer(
                igUserId, childIds, caption, accessToken, tenantCode);
        return metaGraphApiClient.publishContainer(
                igUserId, carouselContainerId, accessToken, tenantCode);
    }

    // ── Validation and helpers ─────────────────────────────────────────────────

    private void validateRequest(InstagramPostRequest request) {
        String tenantCode   = request.getTenantCode();
        List<MultipartFile> images = request.getImages();

        if (images == null || images.isEmpty()) {
            throw new InvalidPostRequestException(tenantCode,
                    "At least one image is required.");
        }

        if (images.size() > MAX_IMAGES) {
            throw new InvalidPostRequestException(tenantCode,
                    "Maximum " + MAX_IMAGES + " images allowed per post. " +
                            "Received: " + images.size());
        }
    }

    public TenantInstagramConfig loadActiveConfig(String tenantCode) {
        return instagramConfigRepository
                .findActiveByTenantCode(tenantCode)
                .orElseThrow(() -> new TenantNotConnectedException(tenantCode));
    }

    private String decryptToken(TenantInstagramConfig config, String tenantCode) {
        try {
            return encryptionService.decrypt(config.getAccessToken());
        } catch (EncryptionService.EncryptionException e) {
            log.error("[InstagramPost] Token decryption failed for tenant={}, configId={}",
                    tenantCode, config.getId(), e);
            throw new InstagramPostException(tenantCode, "token_decrypt_failed",
                    "Failed to decrypt Instagram access token. Contact your administrator.", e);
        }
    }

    private String sanitizeCaption(String caption) {
        if (caption == null) return "";
        if (caption.length() > MAX_CAPTION_LENGTH) {
            log.warn("[InstagramPost] Caption truncated from {} to {} chars",
                    caption.length(), MAX_CAPTION_LENGTH);
            return caption.substring(0, MAX_CAPTION_LENGTH);
        }
        return caption;
    }

    private String safeFileName(MultipartFile file, int index) {
        String name = file.getOriginalFilename();
        return (name != null && !name.isBlank()) ? name : "image_" + index;
    }

    private String buildSummaryMessage(PostStatus status, int published, int failed, int total) {
        return switch (status) {
            case SUCCESS -> "Post published successfully with " + published + " image(s).";
            case PARTIAL -> "Post published with " + published + "/" + total + " images. " +
                    failed + " image(s) failed — check failedImages for details.";
            case FAILED  -> "Post failed — all " + total + " image(s) could not be processed.";
        };
    }

    /** Returns a short label for logging indicating where the caption came from. */
    private String captionSource(String suppliedCaption, String resolvedCaption) {
        if (StringUtils.isNotBlank(suppliedCaption)) return "user-supplied";
        if (StringUtils.isNotBlank(resolvedCaption)) return "ai-generated";
        return "empty";
    }
}