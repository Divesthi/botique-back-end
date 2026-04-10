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

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no", updatable = false),
            @JoinColumn(name = "tenant_code", referencedColumnName = "tenant_code", updatable = false)
    })
    private CustomerDetails customerDetails;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bill_id", referencedColumnName = "id", updatable = false)
    private BillDetails billDetails;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", referencedColumnName = "id", updatable = false)
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
