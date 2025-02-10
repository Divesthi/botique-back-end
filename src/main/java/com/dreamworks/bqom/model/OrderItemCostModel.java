package com.dreamworks.bqom.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class OrderItemCostModel {
    private Long id;
    private Double cost;
    private String type;
    private String mobileNo;
    private Long orderId;
}
