package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estimate_amount")
    private String estimateAmount;

    @Column(name = "updated_date")
    private OffsetDateTime updatedDate;

    @Column(name = "delivered_date")
    private OffsetDateTime deliveredDate;

    @Column(name = "tenant_code", insertable = false, updatable = false)
    private String tenantCode;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no", updatable = false),
            @JoinColumn(name = "tenant_code", referencedColumnName = "tenant_code", updatable = false)
    })
    private CustomerDetails customerDetails;

    @OneToMany(
            fetch = FetchType.LAZY,
            mappedBy = "orderDetails"
    )
    @BatchSize(size = 50)
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
                .updatedDate(updatedDate)
                .deliveredDate(deliveredDate)
                .orderItems(orderItems != null ? orderItems.stream().map(OrderItemDetails::toModel).toList() : List.of())
                .build();
    }
}
