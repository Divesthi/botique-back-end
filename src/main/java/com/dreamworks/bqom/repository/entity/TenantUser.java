package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.repository.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_users")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "supabase_uid", unique = true, nullable = false)
    private String supabaseUid;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null)
            active = true;
        if (role == null)
            role = UserRole.TENANT_USER;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
