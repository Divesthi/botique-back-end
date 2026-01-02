package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.order.OrderItemCostModel;
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
@Table(name = "order_item_cost")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemCost extends BaseEntity implements Serializable {

    @Column(name = "cost")
    private Double cost;

    @Column(name = "type")
    private String type;

    @Column(name = "remarks")
    private String remarks;

    @OneToOne(
            fetch = FetchType.EAGER)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
    private CustomerDetails customerDetails;

    @OneToOne(
            fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", referencedColumnName = "id")
    private OrderItemDetails orderItemDetails;

    public static OrderItemCost toEntity(OrderItemCostModel model, CustomerDetails customerDetails, OrderItemDetails itemDetails) {
        return OrderItemCost.builder()
                .cost(model.getCost())
                .orderItemDetails(itemDetails)
                .type(model.getType())
                .customerDetails(customerDetails)
                .build();
    }

    public OrderItemCostModel toModel() {
        return OrderItemCostModel.builder()
                .id(id)
                .cost(cost)
                .orderItemId(orderItemDetails.getId())
                .type(type)
                .build();
    }

}
