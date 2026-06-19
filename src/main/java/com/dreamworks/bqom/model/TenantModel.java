package com.dreamworks.bqom.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantModel {
    private Long id;
    @NonNull
    private String code;
    private String name;
    private String address;
    private String phoneNumber;
    private LocalDate startedDate;
    private LocalDate churnedDate;
    private Boolean active;
    private Map<String, Object> preferences;
}
