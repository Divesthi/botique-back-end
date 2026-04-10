package com.dreamworks.bqom.model.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMeasurementModel {
    private Long id;
    private String mobileNo;
    private String tenantCode;
    private String dressType;
    private String remarks;
    private String name;
    private Map<String, Object> measurement;
    private OffsetDateTime creationDate;
    private OffsetDateTime updatedDate;
}
