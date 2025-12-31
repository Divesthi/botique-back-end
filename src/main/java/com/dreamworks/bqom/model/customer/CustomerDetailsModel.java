package com.dreamworks.bqom.model.customer;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.OffsetDateTime;

@Builder
@Data
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
