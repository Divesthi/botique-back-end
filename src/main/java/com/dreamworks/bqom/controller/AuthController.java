package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.TenantUserModel;
import com.dreamworks.bqom.repository.enums.UserRole;
import com.dreamworks.bqom.security.AuthenticatedUser;
import com.dreamworks.bqom.security.RequireRole;
import com.dreamworks.bqom.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/v1/bqom/auth", produces = "application/json")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Register a new user for a tenant.
     * - PLATFORM_ADMIN must provide tenantCode in request body (can register for
     * any tenant)
     * - TENANT_ADMIN has tenantCode auto-set from their context (can only register
     * for own tenant)
     * Creates the user in Supabase Auth and sends an invite email automatically.
     *
     * Request body: { "email": "...", "displayName": "...", "role": "TENANT_USER",
     * "tenantCode": "..." }
     */
    @PostMapping("/register")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantUserModel> registerUser(@RequestBody TenantUserModel model) {
        AuthenticatedUser currentUser = getAuthenticatedUser();
        // TENANT_ADMIN: auto-set tenantCode from context; PLATFORM_ADMIN: must provide
        // in body
        if (!currentUser.isPlatformAdmin()) {
            model.setTenantCode(currentUser.getTenantCode());
        } else if (model.getTenantCode() == null || model.getTenantCode().isBlank()) {
            // PLATFORM_ADMIN must specify which tenant to register the user for
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        TenantUserModel created = authService.registerUser(model, currentUser);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Get the current authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<TenantUserModel> getCurrentUser() {
        AuthenticatedUser currentUser = getAuthenticatedUser();
        TenantUserModel userModel = authService.getCurrentUser(currentUser);
        return new ResponseEntity<>(userModel, HttpStatus.OK);
    }

    /**
     * List users.
     * - TENANT_ADMIN can only list their own tenant's users.
     * - PLATFORM_ADMIN can list all users, or filter by tenantCode.
     */
    @GetMapping("/users")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<List<TenantUserModel>> getUsers(@RequestParam(name = "tenantCode", required = false) String tenantCode) {
        AuthenticatedUser currentUser = getAuthenticatedUser();
        List<TenantUserModel> users = authService.getUsers(tenantCode, currentUser);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    /**
     * Update a user's role. Only TENANT_ADMIN can call this.
     */
    @PutMapping("/users/{userId}/role")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantUserModel> updateUserRole(
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, String> body) {
        AuthenticatedUser currentUser = getAuthenticatedUser();
        UserRole newRole = UserRole.valueOf(body.get("role"));
        TenantUserModel updated = authService.updateUserRole(userId, newRole, currentUser);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    /**
     * Activate or deactivate a user. Only TENANT_ADMIN can call this.
     */
    @PutMapping("/users/{userId}/status")
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantUserModel> updateUserStatus(
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, Boolean> body) {
        AuthenticatedUser currentUser = getAuthenticatedUser();
        boolean active = Boolean.TRUE.equals(body.get("active"));
        TenantUserModel updated = authService.updateUserStatus(userId, active, currentUser);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
