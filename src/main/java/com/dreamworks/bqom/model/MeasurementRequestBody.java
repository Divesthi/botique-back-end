package com.dreamworks.bqom.model;

import lombok.Data;
import lombok.NonNull;

import java.util.Map;

@Data
public class MeasurementRequestBody {
    @NonNull
    private String mobileNo;
    private String name;
    private String dressType;
    private Map<String, Object> measurement;
    private String remarks;
}
