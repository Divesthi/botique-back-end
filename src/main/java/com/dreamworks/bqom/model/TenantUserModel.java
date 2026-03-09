package com.dreamworks.bqom.model;

import com.dreamworks.bqom.repository.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenantUserModel {

    private Long id;
    private String supabaseUid;
    private String email;
    private String displayName;
    private String tenantCode;
    private String tenantName;
    private String phoneNumber;
    private UserRole role;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
