package com.dreamworks.bqom.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class CustomerMeasurementModel {
    private Long id;
    private String mobileNo;
    private String dressType;
    private String remarks;
    private String name;
    private Map<String, Object> measurement;
    private OffsetDateTime creationDate;
}
