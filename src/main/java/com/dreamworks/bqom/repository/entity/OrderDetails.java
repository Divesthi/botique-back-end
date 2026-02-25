package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetails extends BaseEntity implements Serializable {

    @Column(name = "received_date")
    private OffsetDateTime receivedDate;

    @Column(name = "cutting_date")
    private OffsetDateTime cuttingDate;

    @Column(name = "delivery_date")
    private OffsetDateTime deliveryDate;

    @Column(name = "packaging_date")
    private OffsetDateTime packagingDate;

    @Column(name = "total_items")
    private int totalItems;

    @Column(name = "remarks")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "total")
    private Double total;

    @Column(name = "advance")
    private Double advance;

    @Column(name = "balance")
    private Double balance;

    @Column(name = "estimate_amount", columnDefinition = "JSON")
    private String estimateAmount;

    @Column(name = "tenant_code", insertable = false, updatable = false)
    private String tenantCode;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no"),
            @JoinColumn(name = "tenant_code", referencedColumnName = "tenant_code")
    })
    private CustomerDetails customerDetails;

    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL
    )
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    private List<OrderItemDetails> orderItems;

    public static OrderDetails toEntity(OrderModel model, CustomerDetails customerDetails) {
        return OrderDetails.builder()
                .status(model.getStatus())
                .advance(model.getAdvance())
                .balance(model.getBalance())
                .total(model.getTotal())
                .totalItems(model.getTotalItems())
                .customerDetails(customerDetails)
                .cuttingDate(model.getCuttingDate())
                .receivedDate(model.getReceivedDate())
                .packagingDate(model.getPackagingDate())
                .deliveryDate(model.getDeliveryDate())
                .remarks(model.getRemarks())
                .estimateAmount(model.getEstimateAmount())
                .build();
    }

    public OrderModel toModel() {
        return OrderModel.builder()
                .id(id)
                .status(status)
                .totalItems(totalItems)
                .receivedDate(receivedDate)
                .cuttingDate(cuttingDate)
                .packagingDate(packagingDate)
                .deliveryDate(deliveryDate)
                .total(total)
                .advance(advance)
                .balance(balance)
                .remarks(remarks)
                .mobileNo(customerDetails.getMobileNo())
                .tenantCode(customerDetails.getTenantCode())
                .estimateAmount(estimateAmount)
                .orderItems(orderItems != null ? orderItems.stream().map(OrderItemDetails::toModel).toList() : List.of())
                .build();
    }
}
