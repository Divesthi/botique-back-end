package com.dreamworks.bqom.security;

import com.dreamworks.bqom.repository.TenantUserRepository;
import com.dreamworks.bqom.repository.entity.TenantUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that validates Supabase JWT tokens on every request.
 * <p>
 * Flow:
 * 1. Extract Bearer token from Authorization header
 * 2. Validate and parse JWT using Supabase JWT secret
 * 3. Look up the user in tenant_users by supabase_uid (JWT "sub" claim)
 * 4. Verify the URL's tenantCode matches the user's tenantCode (tenant
 * isolation)
 * 5. Set AuthenticatedUser in SecurityContext
 */
@Component
@Slf4j
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // Pattern to extract tenantCode from URL paths like
    // /v1/bqom/tenants/{tenantCode}/...
    private static final Pattern TENANT_URL_PATTERN = Pattern.compile("/v1/bqom/tenants/([^/]+)");

    private final TenantUserRepository tenantUserRepository;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    public SupabaseJwtAuthenticationFilter(
            TenantUserRepository tenantUserRepository,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper) {
        this.tenantUserRepository = tenantUserRepository;
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // 1. Parse and validate the JWT using JWK endpoint
            Jwt jwt = jwtDecoder.decode(token);

            String supabaseUid = jwt.getSubject();
            if (supabaseUid == null || supabaseUid.isBlank()) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: missing subject");
                return;
            }

            // 2. Look up user in our DB
            Optional<TenantUser> userOpt = tenantUserRepository.findBySupabaseUid(supabaseUid);
            if (userOpt.isEmpty()) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "User not registered. Please contact your tenant admin.");
                return;
            }

            TenantUser tenantUser = userOpt.get();

            // 3. Check if user is active
            if (!Boolean.TRUE.equals(tenantUser.getActive())) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "User account is deactivated. Please contact your tenant admin.");
                return;
            }

            // 4. Tenant isolation: verify URL tenantCode matches user's tenantCode
            // PLATFORM_ADMIN can access any tenant's data
            String urlTenantCode = extractTenantCodeFromUrl(request.getRequestURI());
            boolean isPlatformAdmin = com.dreamworks.bqom.repository.enums.UserRole.PLATFORM_ADMIN
                    .equals(tenantUser.getRole());
            if (!isPlatformAdmin && urlTenantCode != null && !urlTenantCode.equals(tenantUser.getTenantCode())) {
                log.warn("Tenant isolation violation: user {} (tenant: {}) tried to access tenant: {}",
                        tenantUser.getEmail(), tenantUser.getTenantCode(), urlTenantCode);
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: you do not have access to this tenant's data.");
                return;
            }

            // 5. Build AuthenticatedUser and set in SecurityContext
            AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                    .id(tenantUser.getId())
                    .supabaseUid(supabaseUid)
                    .email(tenantUser.getEmail())
                    .displayName(tenantUser.getDisplayName())
                    .tenantCode(tenantUser.getTenantCode())
                    .role(tenantUser.getRole())
                    .build();

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + tenantUser.getRole().name())));

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authenticated user: {} (tenant: {}, role: {})",
                    tenantUser.getEmail(), tenantUser.getTenantCode(), tenantUser.getRole());

        } catch (JwtValidationException e) {
            log.error("JWT validation failed: {}", e.getMessage(), e);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Token validation failed or expired. Error: " + e.getMessage());
            return;
        } catch (JwtException e) {
            log.error("JWT exception: {}", e.getMessage(), e);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token. Error: " + e.getMessage());
            return;
        } catch (Exception e) {
            log.error("JWT authentication error", e);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractTenantCodeFromUrl(String uri) {
        Matcher matcher = TENANT_URL_PATTERN.matcher(uri);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new HashMap<>();
        body.put("error", true);
        body.put("message", message);
        body.put("status", status);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
