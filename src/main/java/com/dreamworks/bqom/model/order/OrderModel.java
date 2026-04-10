package com.dreamworks.bqom.model.order;

import com.dreamworks.bqom.repository.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    private String tenantCode;
    private String estimateAmount;
    private OffsetDateTime updatedDate;
    private OffsetDateTime deliveredDate;
    private List<OrderItemModel> orderItems;
}
