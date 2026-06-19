package com.dreamworks.bqom.model.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeasurementShareRequest {
    private String toPhoneNumber;       // customer's mobile_no with country code
}
