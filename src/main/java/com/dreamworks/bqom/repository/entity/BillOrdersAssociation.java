package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

@Slf4j
@Entity
@Table(name = "bill_orders_association")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BillOrdersAssociation extends BaseEntity implements Serializable {

    @Column(name = "tenant_code", insertable = false, updatable = false)
    private String tenantCode;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumns({
            @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no"),
            @JoinColumn(name = "tenant_code", referencedColumnName = "tenant_code")
    })
    private CustomerDetails customerDetails;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "bill_id", referencedColumnName = "id")
    private BillDetails billDetails;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    private OrderDetails orderDetails;

    public static BillOrdersAssociation toEntity(BillDetails billDetails, OrderDetails orderDetails,
                                                 CustomerDetails customerDetails) {
        return BillOrdersAssociation.builder()
                .billDetails(billDetails)
                .orderDetails(orderDetails)
                .customerDetails(customerDetails)
                .build();
    }
}
