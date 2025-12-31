package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.Order;

import java.io.Serializable;

@Slf4j
@Entity
@Table(name = "bill_orders_association")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BillOrdersAssociation extends BaseEntity implements Serializable {

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
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
