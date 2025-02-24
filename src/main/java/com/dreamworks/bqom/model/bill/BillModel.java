package com.dreamworks.bqom.model.bill;

import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.enums.BillStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
@Data
public class BillModel {
    private Long id;
    private OffsetDateTime createdDate;
    private Double totalAmount;
    private Double advancePaid;
    private Double balanceAmount;
    private BillStatus status;
    private String discount;
    private String mobileNo;
    private List<OrderModel> orders;
}
