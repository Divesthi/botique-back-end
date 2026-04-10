package com.dreamworks.bqom.model.order;

import com.dreamworks.bqom.repository.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemModel {
    private Long id;
    private Long orderId;
    private Long measurementId;
    private String mobileNo;
    private String tenantCode;
    private String remarks;
    private int quantity;
    private Double costPerQuantity;
    private OrderStatus status;
    private List<OrderItemCostModel> itemsCost;
}
