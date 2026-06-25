package com.dreamworks.bqom.service.instagram;

import com.dreamworks.bqom.exception.InstagramPostException.InsufficientPermissionException;
import com.dreamworks.bqom.exception.InstagramPostException.MetaApiPostException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Thin HTTP client for Meta Graph API calls related to Instagram content publishing.
 *
 * <h2>Content Publishing API flow</h2>
 * <pre>
 * Single-image Feed post:
 *   1. POST /{ig-user-id}/media          → create IMAGE container → creationId
 *   2. POST /{ig-user-id}/media_publish  → publish container      → igPostId
 *
 * Carousel Feed post (2–10 images):
 *   1. POST /{ig-user-id}/media  (for each image, is_carousel_item=true) → [childId1, childId2, ...]
 *   2. POST /{ig-user-id}/media  (media_type=CAROUSEL, children=[...])   → carouselContainerId
 *   3. POST /{ig-user-id}/media_publish                                   → igPostId
 * </pre>
 *
 * <h2>Error classification</h2>
 * <ul>
 *   <li>Meta error code {@code 10} or {@code 200} with OAuthException type
 *       → permission missing → {@link InsufficientPermissionException}</li>
 *   <li>HTTP 4xx (bad token, invalid param) → {@link MetaApiPostException}</li>
 *   <li>HTTP 5xx / network → {@link MetaApiPostException}</li>
 * </ul>
 *
 * <h2>No business logic here</h2>
 * <p>This class is intentionally dumb — it only makes HTTP calls and parses
 * responses. All orchestration (which images succeeded, partial-success tracking,
 * retry decisions) lives in {@link InstagramPostService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaGraphApiClient {

    private static final String GRAPH_API_VERSION = "v19.0";

    // Meta error types that indicate a missing OAuth permission scope
    private static final String OAUTH_EXCEPTION_TYPE    = "OAuthException";
    // Meta error codes that signal permission problems
    private static final int    PERMISSION_ERROR_CODE   = 10;
    private static final int    APP_NOT_AUTHORIZED_CODE = 200;

    @Value("${bqom.instagram.graph-api-url:https://graph.facebook.com}")
    private String graphApiBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Creates a single-image media container on Instagram.
     *
     * <p>This is step 1 of a single-image feed post. The container is in
     * {@code IN_PROGRESS} status until published via {@link #publishContainer}.
     *
     * @param igUserId    Instagram Business Account ID
     * @param imageUrl    publicly accessible HTTPS URL of the image
     * @param caption     post caption (may be empty)
     * @param accessToken decrypted long-lived Meta access token
     * @param tenantCode  for logging context
     * @return {@code creation_id} of the media container
     */
    public String createSingleImageContainer(String igUserId, String imageUrl,
                                             String caption, String accessToken,
                                             String tenantCode) {
        log.info("[Meta] Creating single-image container for tenant={}, igUserId={}",
                tenantCode, igUserId);

        String url = UriComponentsBuilder
                .fromUriString(graphApiBaseUrl + "/" + GRAPH_API_VERSION + "/{igUserId}/media")
                .buildAndExpand(igUserId)
                .toUriString();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
                .queryParam("image_url",    imageUrl)
                .queryParam("media_type",   "IMAGE")
                .queryParam("access_token", accessToken);

        if (caption != null && !caption.isBlank()) {
            builder.queryParam("caption", caption);
        }

        return callMetaPostAndExtractId(builder.build().encode().toUri(), tenantCode, "single-image container");
    }

    /**
     * Creates a carousel child media container.
     *
     * <p>Called once per image in a carousel request. Each child container
     * must be created before the parent carousel container.
     *
     * @param igUserId    Instagram Business Account ID
     * @param imageUrl    publicly accessible HTTPS URL for this carousel item
     * @param accessToken decrypted Meta access token
     * @param tenantCode  for logging context
     * @return {@code creation_id} of the child container
     */
    public String createCarouselChildContainer(String igUserId, String imageUrl,
                                               String accessToken, String tenantCode) {
        log.info("[Meta] Creating carousel child container for tenant={}, igUserId={}",
                tenantCode, igUserId);

        String url = UriComponentsBuilder
                .fromUriString(graphApiBaseUrl + "/" + GRAPH_API_VERSION + "/{igUserId}/media")
                .buildAndExpand(igUserId)
                .toUriString();

        URI fullUri = UriComponentsBuilder.fromUriString(url)
                .queryParam("image_url",          imageUrl)
                .queryParam("is_carousel_item",   "true")
                .queryParam("access_token",        accessToken)
                .build()
                .encode()
                .toUri();

        return callMetaPostAndExtractId(fullUri, tenantCode, "carousel child container");
    }

    /**
     * Creates the parent carousel media container that groups child containers.
     *
     * <p>This is step 2 of a carousel post. All child creation IDs must be
     * available before calling this method.
     *
     * @param igUserId    Instagram Business Account ID
     * @param childIds    ordered list of child container creation IDs
     * @param caption     post caption (may be empty)
     * @param accessToken decrypted Meta access token
     * @param tenantCode  for logging context
     * @return {@code creation_id} of the carousel container
     */
    public String createCarouselContainer(String igUserId, List<String> childIds,
                                          String caption, String accessToken,
                                          String tenantCode) {
        log.info("[Meta] Creating carousel container for tenant={}, igUserId={}, childCount={}",
                tenantCode, igUserId, childIds.size());

        String url = UriComponentsBuilder
                .fromUriString(graphApiBaseUrl + "/" + GRAPH_API_VERSION + "/{igUserId}/media")
                .buildAndExpand(igUserId)
                .toUriString();

        // children must be a comma-separated list
        String children = String.join(",", childIds);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
                .queryParam("media_type",   "CAROUSEL")
                .queryParam("children",     children)
                .queryParam("access_token", accessToken);

        if (caption != null && !caption.isBlank()) {
            builder.queryParam("caption", caption);
        }

        return callMetaPostAndExtractId(builder.build().encode().toUri(), tenantCode, "carousel container");
    }

    /**
     * Publishes a previously created media container.
     *
     * <p>This is the final step for both single-image and carousel posts.
     * After a successful call, the post is live on Instagram.
     *
     * @param igUserId         Instagram Business Account ID
     * @param creationId       container ID returned by a prior create call
     * @param accessToken      decrypted Meta access token
     * @param tenantCode       for logging context
     * @return Instagram post ID (the live content ID)
     */
    public String publishContainer(String igUserId, String creationId,
                                   String accessToken, String tenantCode) {
        log.info("[Meta] Publishing container={} for tenant={}, igUserId={}",
                creationId, tenantCode, igUserId);

        String url = UriComponentsBuilder
                .fromUriString(graphApiBaseUrl + "/" + GRAPH_API_VERSION + "/{igUserId}/media_publish")
                .buildAndExpand(igUserId)
                .toUriString();

        URI fullUri = UriComponentsBuilder.fromUriString(url)
                .queryParam("creation_id", creationId)
                .queryParam("access_token", accessToken)
                .build()
                .encode()
                .toUri();

        return callMetaPostAndExtractId(fullUri, tenantCode, "media publish");
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * POSTs to a fully-built Meta API URL and extracts the {@code id} field
     * from the JSON response. All Meta content publishing endpoints return
     * {@code { "id": "..." }} on success.
     */
    private String callMetaPostAndExtractId(URI uri, String tenantCode, String step) {
        try {
            // Meta's content publishing endpoints accept POST with URL-encoded params;
            // no request body is needed when all params are in the query string.
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.postForEntity(uri, entity, String.class);

            return parseIdFromResponse(response, tenantCode, step);

        } catch (MetaApiPostException | InsufficientPermissionException e) {
            throw e; // already structured

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("[Meta] Client error during {} for tenant={}: status={}, body={}",
                    step, tenantCode, e.getStatusCode(), body);

            // Try to parse Meta's structured error before falling back
            return handleMetaClientError(body, tenantCode, step, e);

        } catch (RestClientException e) {
            log.error("[Meta] Network error during {} for tenant={}", step, tenantCode, e);
            throw new MetaApiPostException(tenantCode, -1,
                    "Network error calling Meta API during " + step, e);
        }
    }

    /**
     * Parses the {@code id} field from a successful Meta API response.
     * Also checks Meta's application-level error envelope on HTTP 200 responses.
     */
    private String parseIdFromResponse(ResponseEntity<String> response,
                                       String tenantCode, String step) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("[Meta] Non-2xx status during {} for tenant={}: status={}",
                    step, tenantCode, response.getStatusCode());
            throw new MetaApiPostException(tenantCode, -1,
                    "Meta API returned status " + response.getStatusCode() + " during " + step);
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());

            // Meta can return HTTP 200 with an error body in some edge cases
            if (root.has("error")) {
                handleMetaErrorNode(root.get("error"), tenantCode, step);
            }

            String id = root.path("id").asText(null);
            if (id == null || id.isBlank()) {
                log.error("[Meta] Missing 'id' in response during {} for tenant={}: body={}",
                        step, tenantCode, response.getBody());
                throw new MetaApiPostException(tenantCode, -1,
                        "Meta API response missing 'id' field during " + step);
            }

            log.debug("[Meta] Step='{}' succeeded for tenant={}, id={}", step, tenantCode, id);
            return id;

        } catch (MetaApiPostException | InsufficientPermissionException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Meta] Failed to parse response during {} for tenant={}", step, tenantCode, e);
            throw new MetaApiPostException(tenantCode, -1,
                    "Failed to parse Meta API response during " + step, e);
        }
    }

    /**
     * Parses a {@code 4xx} error body from Meta and maps it to the correct
     * exception type. Detects permission errors specifically so the controller
     * can surface an actionable re-auth message.
     */
    private String handleMetaClientError(String rawBody, String tenantCode,
                                         String step, HttpClientErrorException cause) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root.has("error")) {
                handleMetaErrorNode(root.get("error"), tenantCode, step);
            }
        } catch (MetaApiPostException | InsufficientPermissionException e) {
            throw e;
        } catch (Exception ignored) {
            // Body not parseable — fall through to generic error
        }

        throw new MetaApiPostException(tenantCode, -1,
                "Meta API client error during " + step + ": " + cause.getStatusCode(), cause);
    }

    /**
     * Reads a Meta {@code error} JSON node and maps it to the right exception.
     *
     * <p>Meta's error envelope:
     * <pre>
     * {
     *   "error": {
     *     "message":    "...",
     *     "type":       "OAuthException",
     *     "code":       10,
     *     "error_subcode": 458
     *   }
     * }
     * </pre>
     */
    private void handleMetaErrorNode(JsonNode errorNode, String tenantCode, String step) {
        String type    = errorNode.path("type").asText("");
        int    code    = errorNode.path("code").asInt(-1);
        String message = errorNode.path("message").asText("unknown error");

        log.error("[Meta] API error during {} for tenant={}: type={}, code={}, message={}",
                step, tenantCode, type, code, message);

        // Detect permission / authorization errors
        boolean isPermissionError = OAUTH_EXCEPTION_TYPE.equals(type)
                && (code == PERMISSION_ERROR_CODE || code == APP_NOT_AUTHORIZED_CODE);

        if (isPermissionError) {
            throw new InsufficientPermissionException(tenantCode, "instagram_content_publish");
        }

        throw new MetaApiPostException(tenantCode, code,
                "Meta API error during " + step + ": " + message);
    }
}