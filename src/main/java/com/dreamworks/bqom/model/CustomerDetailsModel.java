package com.dreamworks.bqom.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.OffsetDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailsModel {
    private Long id;
    private String name;
    @NonNull
    private String mobileNo;
    private String address;
    private String alternateContactNo;
    private String tenantId;
    private OffsetDateTime creationDate;
}
