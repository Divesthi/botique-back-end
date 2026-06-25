package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.config.ImgBBProperties;
import com.dreamworks.bqom.exception.InstagramPostException.ImgBBUploadException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Service responsible for uploading images to ImgBB and returning public URLs.
 *
 * <h2>Why ImgBB?</h2>
 * <p>Meta's Content Publishing API requires images to be at a publicly
 * accessible HTTPS URL when creating a media container. ImgBB provides
 * a free CDN-backed hosting API that returns stable, publicly accessible
 * URLs immediately after upload — no S3 bucket or additional infrastructure
 * is needed.
 *
 * <h2>Upload wire format</h2>
 * <pre>
 * POST https://api.imgbb.com/1/upload
 * Content-Type: multipart/form-data
 *
 * key=API_KEY
 * expiration=3600          ← optional, seconds; 0 = permanent
 * image=&lt;file_bytes&gt;
 * </pre>
 *
 * <h2>Accepted image types</h2>
 * <p>Meta's Content Publishing API accepts JPEG and PNG for IMAGE containers.
 * We enforce this at upload time to fail fast before any Meta API calls.
 *
 * <h2>Error handling</h2>
 * <p>Each upload is a discrete operation. Failure throws
 * {@link ImgBBUploadException} carrying the original filename so the caller
 * (InstagramPostService) can record a partial-failure and continue with
 * remaining images.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImgBBService {

    private static final Set<String> ACCEPTED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    // ImgBB response JSON path for the public display URL
    private static final String JSON_PATH_URL   = "data.display_url";
    private static final String JSON_PATH_IMAGE = "data.url";

    private final ImgBBProperties imgBBProperties;
    private final RestTemplate    restTemplate;
    private final ObjectMapper    objectMapper;

    /**
     * Uploads a single {@link MultipartFile} to ImgBB and returns the
     * permanent public URL.
     *
     * <p>The returned URL is the {@code data.display_url} from ImgBB's response —
     * a direct-link URL suitable for use as the {@code image_url} parameter
     * in Meta's media container API.
     *
     * @param file       the image file from the multipart request
     * @param tenantCode tenant context for structured logging
     * @return public HTTPS URL of the hosted image
     * @throws ImgBBUploadException if the file is invalid, too large, wrong type,
     *                              or ImgBB rejects the upload
     */
    public String upload(MultipartFile file, String tenantCode) {
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";

        log.info("[ImgBB] Uploading image='{}' for tenant={}, size={}bytes",
                fileName, tenantCode, file.getSize());

        // ── Validate before upload ─────────────────────────────────────────────
        validateFile(file, fileName, tenantCode);

        // ── Build multipart body ───────────────────────────────────────────────
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("[ImgBB] Failed to read bytes from file='{}' for tenant={}",
                    fileName, tenantCode, e);
            throw new ImgBBUploadException(tenantCode, fileName,
                    "Failed to read image file: " + fileName, e);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("key", imgBBProperties.getApiKey());
        body.add("image", new NamedByteArrayResource(bytes, fileName));

        if (imgBBProperties.getExpirationSeconds() > 0) {
            body.add("expiration", String.valueOf(imgBBProperties.getExpirationSeconds()));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // ── Call ImgBB API ─────────────────────────────────────────────────────
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    imgBBProperties.getUploadUrl(), requestEntity, String.class);

            return parseUploadResponse(response, fileName, tenantCode);

        } catch (ImgBBUploadException e) {
            throw e; // already structured

        } catch (HttpClientErrorException e) {
            log.error("[ImgBB] Client error uploading '{}' for tenant={}: status={}, body={}",
                    fileName, tenantCode, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ImgBBUploadException(tenantCode, fileName,
                    "ImgBB rejected the upload (status=" + e.getStatusCode() +
                            "). Check API key and file format.", e);

        } catch (RestClientException e) {
            log.error("[ImgBB] Network error uploading '{}' for tenant={}", fileName, tenantCode, e);
            throw new ImgBBUploadException(tenantCode, fileName,
                    "Network error communicating with ImgBB for file: " + fileName, e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Validates file size and MIME type before making any network calls.
     * Fail-fast avoids wasting network bandwidth on clearly invalid files.
     */
    private void validateFile(MultipartFile file, String fileName, String tenantCode) {
        if (file.isEmpty()) {
            throw new ImgBBUploadException(tenantCode, fileName,
                    "Image file '" + fileName + "' is empty.");
        }

        if (file.getSize() > imgBBProperties.getMaxFileSizeBytes()) {
            long maxMb = imgBBProperties.getMaxFileSizeBytes() / (1024 * 1024);
            throw new ImgBBUploadException(tenantCode, fileName,
                    "Image '" + fileName + "' exceeds the maximum allowed size of " + maxMb + "MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ImgBBUploadException(tenantCode, fileName,
                    "Image '" + fileName + "' has unsupported type '" + contentType +
                            "'. Accepted types: JPEG, PNG.");
        }
    }

    /**
     * Parses ImgBB's JSON response and extracts the public image URL.
     *
     * <p>ImgBB success response:
     * <pre>
     * {
     *   "data": {
     *     "url":         "https://i.ibb.co/xxx/image.jpg",
     *     "display_url": "https://i.ibb.co/xxx/image.jpg",
     *     ...
     *   },
     *   "success": true,
     *   "status":  200
     * }
     * </pre>
     */
    private String parseUploadResponse(ResponseEntity<String> response,
                                       String fileName, String tenantCode) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ImgBBUploadException(tenantCode, fileName,
                    "ImgBB returned non-2xx status: " + response.getStatusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());

            // Check ImgBB's own success flag
            if (!root.path("success").asBoolean(false)) {
                String errMsg = root.path("error").path("message").asText("unknown error");
                log.error("[ImgBB] Upload failed for '{}', tenant={}: {}", fileName, tenantCode, errMsg);
                throw new ImgBBUploadException(tenantCode, fileName,
                        "ImgBB rejected the image '" + fileName + "': " + errMsg);
            }

            // Prefer display_url (direct link), fall back to url
            String url = root.at("/data/display_url").asText(null);
            if (url == null || url.isBlank()) {
                url = root.at("/data/url").asText(null);
            }

            if (url == null || url.isBlank()) {
                log.error("[ImgBB] No URL in response for '{}', tenant={}: {}",
                        fileName, tenantCode, response.getBody());
                throw new ImgBBUploadException(tenantCode, fileName,
                        "ImgBB response did not contain a usable URL for: " + fileName);
            }

            log.info("[ImgBB] Successfully uploaded '{}' for tenant={}, url={}",
                    fileName, tenantCode, url);
            return url;

        } catch (ImgBBUploadException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ImgBB] Failed to parse response for '{}', tenant={}",
                    fileName, tenantCode, e);
            throw new ImgBBUploadException(tenantCode, fileName,
                    "Failed to parse ImgBB response for: " + fileName, e);
        }
    }

    // ── Inner resource class ───────────────────────────────────────────────────

    /**
     * A {@link ByteArrayResource} that also carries the original filename.
     * Spring's {@code RestTemplate} uses {@code getFilename()} when building
     * the multipart body's {@code Content-Disposition: form-data; filename="..."} header.
     * Without an explicit filename, some servers reject the upload.
     */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}