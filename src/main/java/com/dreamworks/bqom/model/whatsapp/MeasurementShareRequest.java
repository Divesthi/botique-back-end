package com.dreamworks.bqom.model.whatsapp;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MeasurementShareRequest {
    private String toPhoneNumber;       // customer's mobile_no with country code
}
