package com.dreamworks.bqom.model.order;

import com.dreamworks.bqom.repository.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
@Data
public class OrderModel {
    private Long id;
    private OffsetDateTime receivedDate;
    private OffsetDateTime cuttingDate;
    private OffsetDateTime packagingDate;
    private OffsetDateTime deliveryDate;
    private int totalItems;
    private String remarks;
    private OrderStatus status;
    private Double total;
    private Double advance;
    private Double balance;
    private String mobileNo;
    private String estimateAmount;
    private List<OrderItemModel> orderItems;
}
