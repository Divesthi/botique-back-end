package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.TenantUserModel;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.TenantUserRepository;
import com.dreamworks.bqom.repository.entity.TenantUser;
import com.dreamworks.bqom.repository.enums.UserRole;
import com.dreamworks.bqom.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AuthService {

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SupabaseAdminClient supabaseAdminClient;

    /**
     * Register a new user for a tenant.
     * - PLATFORM_ADMIN can register users for any tenant (must provide tenantCode)
     * - TENANT_ADMIN can only register users for their own tenant
     * This method:
     * 1. Finds the user in Supabase Auth via Admin API
     * 2. Saves the user in the local tenant_users table
     */
    public TenantUserModel registerUser(TenantUserModel model, AuthenticatedUser currentUser) {
        // Validate tenant exists
        tenantRepository.findByCode(model.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + model.getTenantCode()));

        // TENANT_ADMIN can only register for their own tenant; PLATFORM_ADMIN can
        // register for any
        if (!currentUser.isPlatformAdmin() && !currentUser.getTenantCode().equals(model.getTenantCode())) {
            throw new RuntimeException("You can only register users for your own tenant.");
        }

        // Check if user already exists in our DB
        tenantUserRepository.findByEmail(model.getEmail()).ifPresent(u -> {
            throw new RuntimeException("User with email " + model.getEmail() + " already exists in the system.");
        });

        // Step 1: Find user in Supabase Auth by email
        String supabaseUid = supabaseAdminClient.getSupabaseUidByEmail(model.getEmail());

        try {
            // Step 2: Save in our local DB
            TenantUser user = TenantUser.builder()
                    .supabaseUid(supabaseUid)
                    .email(model.getEmail())
                    .displayName(model.getDisplayName())
                    .phoneNumber(model.getPhoneNumber())
                    .tenantCode(model.getTenantCode())
                    .role(model.getRole() != null ? model.getRole() : UserRole.TENANT_USER)
                    .active(true)
                    .build();

            user = tenantUserRepository.save(user);
            log.info("User registered: {} for tenant: {} with role: {} (supabase_uid: {})",
                    user.getEmail(), user.getTenantCode(), user.getRole(), supabaseUid);

            return toModel(user);

        } catch (Exception e) {
            throw new RuntimeException("Failed to register user: " + e.getMessage());
        }
    }

    /**
     * Get the current user's profile.
     */
    public TenantUserModel getCurrentUser(AuthenticatedUser currentUser) {
        TenantUser user = tenantUserRepository.findBySupabaseUid(currentUser.getSupabaseUid())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return toModel(user);
    }

    /**
     * List users.
     * - TENANT_ADMIN can only view their own tenant's users.
     * - PLATFORM_ADMIN can view all users, or filter by tenantCode.
     */
    public List<TenantUserModel> getUsers(String tenantCode, AuthenticatedUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            // Force TENANT_ADMIN to only see their own tenant
            return tenantUserRepository.findByTenantCode(currentUser.getTenantCode())
                    .stream()
                    .map(this::toModel)
                    .toList();
        }

        // PLATFORM_ADMIN logic
        if (tenantCode == null || tenantCode.isBlank()) {
            return tenantUserRepository.findAll()
                    .stream()
                    .map(this::toModel)
                    .toList();
        }

        return tenantUserRepository.findByTenantCode(tenantCode)
                .stream()
                .map(this::toModel)
                .toList();
    }

    /**
     * Update a user's role. Admin only.
     */
    public TenantUserModel updateUserRole(Long userId, UserRole newRole, AuthenticatedUser currentUser) {
        TenantUser user = tenantUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // TENANT_ADMIN can only manage their own tenant's users; PLATFORM_ADMIN can
        // manage any
        if (!currentUser.isPlatformAdmin() && !currentUser.getTenantCode().equals(user.getTenantCode())) {
            throw new RuntimeException("You can only manage users within your own tenant.");
        }

        // Prevent admin from changing their own role
        if (user.getSupabaseUid().equals(currentUser.getSupabaseUid())) {
            throw new RuntimeException("You cannot change your own role.");
        }

        user.setRole(newRole);
        user = tenantUserRepository.save(user);
        log.info("User {} role updated to {} by admin {}", user.getEmail(), newRole, currentUser.getEmail());
        return toModel(user);
    }

    /**
     * Activate or deactivate a user. Admin only.
     */
    public TenantUserModel updateUserStatus(Long userId, boolean active, AuthenticatedUser currentUser) {
        TenantUser user = tenantUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // TENANT_ADMIN can only manage their own tenant's users; PLATFORM_ADMIN can
        // manage any
        if (!currentUser.isPlatformAdmin() && !currentUser.getTenantCode().equals(user.getTenantCode())) {
            throw new RuntimeException("You can only manage users within your own tenant.");
        }

        // Prevent admin from deactivating themselves
        if (user.getSupabaseUid().equals(currentUser.getSupabaseUid())) {
            throw new RuntimeException("You cannot deactivate your own account.");
        }

        user.setActive(active);
        user = tenantUserRepository.save(user);
        log.info("User {} status updated to {} by admin {}", user.getEmail(), active ? "active" : "inactive",
                currentUser.getEmail());
        return toModel(user);
    }

    private TenantUserModel toModel(TenantUser user) {
        String tenantName = null;
        if (user.getTenantCode() != null && !user.getTenantCode().isBlank()) {
            tenantName = tenantRepository.findByCode(user.getTenantCode().trim())
                    .map(t -> t.getName())
                    .orElse(null);
        }

        return TenantUserModel.builder()
                .id(user.getId())
                .supabaseUid(user.getSupabaseUid())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .phoneNumber(user.getPhoneNumber())
                .tenantCode(user.getTenantCode())
                .tenantName(tenantName)
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
