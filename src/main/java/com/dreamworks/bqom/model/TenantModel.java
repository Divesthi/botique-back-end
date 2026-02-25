package com.dreamworks.bqom.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDate;

@Data
@Builder
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
}
