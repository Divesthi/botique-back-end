package com.dreamworks.bqom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for the Supabase Admin API (GoTrue).
 * Uses the service_role key to create/manage users programmatically.
 * <p>
 * API docs: https://supabase.com/docs/reference/api/auth-admin
 */
@Service
@Slf4j
public class SupabaseAdminClient {

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SupabaseAdminClient(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            ObjectMapper objectMapper) {
        this.supabaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * Create a user in Supabase Auth using the Admin API.
     * The user will receive an invite email with a magic link to set their
     * password.
     *
     * @param email       User's email address
     * @param displayName User's display name (stored in user_metadata)
     * @return The Supabase user UUID (id)
     */
    public String createUser(String email, String displayName) {
        String url = supabaseUrl + "/auth/v1/admin/users";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("email_confirm", false); // User will confirm via invite email

        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("display_name", displayName);
        requestBody.put("user_metadata", userMetadata);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String userId = responseJson.get("id").asText();
                log.info("Supabase user created: {} (uid: {})", email, userId);
                return userId;
            }

            throw new RuntimeException("Unexpected response from Supabase: " + response.getStatusCode());

        } catch (HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("Supabase Admin API error: {} - {}", e.getStatusCode(), errorBody);

            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                throw new RuntimeException("User with email " + email + " already exists in Supabase.");
            }

            throw new RuntimeException("Failed to create user in Supabase: " + errorBody);
        } catch (Exception e) {
            log.error("Error calling Supabase Admin API", e);
            throw new RuntimeException("Failed to create user in Supabase: " + e.getMessage());
        }
    }

    /**
     * Send an invite email to a user via the Supabase Admin API.
     * The user will receive a magic link to set their password.
     *
     * @param email User's email address
     */
    public void inviteUser(String email) {
        String url = supabaseUrl + "/auth/v1/invite";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Invite email sent to: {}", email);
        } catch (HttpClientErrorException e) {
            log.error("Failed to send invite email to {}: {}", email, e.getResponseBodyAsString());
            // Don't throw — user is already created, invite can be resent later
        }
    }

    /**
     * Delete a user from Supabase Auth.
     *
     * @param supabaseUid The Supabase user UUID
     */
    public void deleteUser(String supabaseUid) {
        String url = supabaseUrl + "/auth/v1/admin/users/" + supabaseUid;

        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            log.info("Supabase user deleted: {}", supabaseUid);
        } catch (HttpClientErrorException e) {
            log.error("Failed to delete Supabase user {}: {}", supabaseUid, e.getResponseBodyAsString());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);
        return headers;
    }
}
