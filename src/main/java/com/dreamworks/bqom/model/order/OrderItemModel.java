package com.dreamworks.bqom.model.order;

import com.dreamworks.bqom.repository.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class OrderItemModel {
    private Long id;
    private Long orderId;
    private Long measurementId;
    private String mobileNo;
    private String remarks;
    private int quantity;
    private Double costPerQuantity;
    private OrderStatus status;
    private List<OrderItemCostModel> itemsCost;
}
