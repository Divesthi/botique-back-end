package com.dreamworks.bqom.security;

import com.dreamworks.bqom.repository.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds the authenticated user's context extracted from the Supabase JWT + DB
 * lookup.
 * Available via SecurityContextHolder after successful authentication.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatedUser {

    private Long id;
    private String supabaseUid;
    private String email;
    private String displayName;
    private String tenantCode;
    private UserRole role;

    public boolean isPlatformAdmin() {
        return UserRole.PLATFORM_ADMIN.equals(role);
    }

    public boolean isAdmin() {
        return UserRole.TENANT_ADMIN.equals(role) || UserRole.PLATFORM_ADMIN.equals(role);
    }

    public boolean isTenantUser() {
        return UserRole.TENANT_USER.equals(role);
    }
}
