package com.dreamworks.bqom.model.whatsapp;

import lombok.Data;

@Data
public class WhatsAppConfigRequest {
    private String tenantCode;
    private String phoneNumberId;
    private String wabaId;
    private String accessToken;       // plain text — encrypted before saving
    private String businessPhoneNumber;
    private Boolean isActive;
}