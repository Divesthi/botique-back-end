package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.order.OrderItemModel;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.BatchSize;

import java.io.Serializable;
import java.util.List;

@Slf4j
@Entity
@Table(name = "order_item_details")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDetails extends BaseEntity implements Serializable {

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "cost_per_quantity")
    private Double costPerQuantity;

    @Column(name = "quantity")
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "tenant_code", insertable = false, updatable = false)
    private String tenantCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no", updatable = false),
            @JoinColumn(name = "tenant_code", referencedColumnName = "tenant_code", updatable = false)
    })
    private CustomerDetails customerDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    private OrderDetails orderDetails;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_id", referencedColumnName = "id")
    private CustomerMeasurementDetails customerMeasurementDetails;

    @OneToMany(
            fetch = FetchType.LAZY,
            mappedBy = "orderItemDetails"
    )
    @BatchSize(size = 50)
    private List<OrderItemCost> itemCosts;

    public static OrderItemDetails toEntity(OrderItemModel model, CustomerDetails customerDetails,
                                            CustomerMeasurementDetails customerMeasurementDetails,
                                            OrderDetails orderDetails) {
        return OrderItemDetails.builder()
                .quantity(model.getQuantity())
                .costPerQuantity(model.getCostPerQuantity())
                .remarks(model.getRemarks())
                .status(model.getStatus())
                .customerDetails(customerDetails)
                .orderDetails(orderDetails)
                .customerMeasurementDetails(customerMeasurementDetails)
                .build();
    }

    public OrderItemModel toModel() {
        return OrderItemModel.builder()
                .id(id)
                .quantity(quantity)
                .costPerQuantity(costPerQuantity)
                .remarks(remarks)
                .status(status)
                .mobileNo(customerDetails.getMobileNo())
                .tenantCode(customerDetails.getTenantCode())
                .orderId(orderDetails.getId())
                .measurementId(customerMeasurementDetails.getId())
                .itemsCost(itemCosts != null ? itemCosts.stream().map(OrderItemCost::toModel).toList() : List.of())
                .build();
    }
}