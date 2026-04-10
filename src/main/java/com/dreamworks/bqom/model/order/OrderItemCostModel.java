package com.dreamworks.bqom.model.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemCostModel {
    private Long id;
    private Double cost;
    private String type;
    private String mobileNo;
    private Long orderItemId;
}
