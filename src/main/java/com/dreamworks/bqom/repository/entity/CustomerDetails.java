package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Slf4j
@Entity
@Table(name = "customer_details",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mobile_no", "tenant_code"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDetails implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "mobile_no")
    private String mobileNo;

    @Column(name = "alternate_contact_no")
    private String alternateContactNo;

    @Column(name = "tenant_code")
    private String tenantCode;

    @Column(name = "creation_date")
    private OffsetDateTime creationDate;

    public CustomerDetailsModel toModel() {
        return CustomerDetailsModel.builder()
        .name(name)
        .address(address)
        .tenantCode(tenantCode)
        .creationDate(creationDate)
        .alternateContactNo(alternateContactNo)
        .id(id)
        .mobileNo(mobileNo).build();
    }

    public static CustomerDetails toEntity(CustomerDetailsModel customerDetailsModel) {
        return CustomerDetails.builder()
                .name(customerDetailsModel.getName())
                .address(customerDetailsModel.getAddress())
                .mobileNo(customerDetailsModel.getMobileNo())
                .alternateContactNo(customerDetailsModel.getAlternateContactNo())
                .tenantCode(customerDetailsModel.getTenantCode())
                .build();
    }
}
