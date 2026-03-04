package com.dreamworks.bqom.security;

import com.dreamworks.bqom.repository.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that enforces @RequireRole annotations on controller
 * methods/classes.
 * Method-level annotations take precedence over class-level ones.
 */
@Component
@Slf4j
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public RoleAuthorizationInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Check method-level annotation first, then class-level
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        // No role annotation = accessible to any authenticated user
        if (requireRole == null) {
            return true;
        }

        UserRole requiredRole = requireRole.value();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
            return false;
        }

        // PLATFORM_ADMIN has universal access to everything
        if (user.isPlatformAdmin()) {
            return true;
        }

        // TENANT_ADMIN has all permissions within their tenant (superset of
        // TENANT_USER)
        if (user.isAdmin()) {
            return true;
        }

        // If the required role is TENANT_ADMIN and user is not admin, deny access
        if (UserRole.TENANT_ADMIN.equals(requiredRole)) {
            log.warn("Role violation: user {} (role: {}) tried to access admin-only endpoint: {} {}",
                    user.getEmail(), user.getRole(), request.getMethod(), request.getRequestURI());
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: this operation requires Tenant Admin privileges.");
            return false;
        }

        return true;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new HashMap<>();
        body.put("error", true);
        body.put("message", message);
        body.put("status", status);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
