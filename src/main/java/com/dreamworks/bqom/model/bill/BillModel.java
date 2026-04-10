package com.dreamworks.bqom.model.bill;

import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.enums.BillStatus;
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
public class BillModel {
    private Long id;
    private OffsetDateTime createdDate;
    private OffsetDateTime updatedDate;
    private Double totalAmount;
    private Double advancePaid;
    private Double balanceAmount;
    private BillStatus status;
    private String discount;
    private String mobileNo;
    private String tenantCode;
    private List<OrderModel> orders;
    private String remarks;
}
