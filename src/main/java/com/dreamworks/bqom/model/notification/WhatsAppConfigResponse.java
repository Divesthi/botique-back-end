package com.dreamworks.bqom.model.notification;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WhatsAppConfigResponse {
    private Long id;
    private String tenantCode;
    private String phoneNumberId;
    private String wabaId;
    private String businessPhoneNumber; // access token is never returned
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
