package com.dreamworks.bqom.model.whatsapp;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WhatsAppMessageRequest {
    private String tenantCode;
    private String toPhoneNumber;       // customer's mobile_no with country code
    private String templateName;
    private String languageCode;
    private List<String> parameters;    // ordered list matching {{1}}, {{2}}, etc.
    private List<String> parameterNames;
}
