package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.bill.BillModel;
import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import com.dreamworks.bqom.repository.enums.BillStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Entity
@Table(name = "bill_details")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BillDetails extends BaseEntity implements Serializable {

    @Column(name = "created_date")
    private OffsetDateTime createdDate;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "advance_paid")
    private Double advancePaid;

    @Column(name = "balance_amount")
    private Double balanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BillStatus status;

    @Column(name = "discount")
    private String discount;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "tenant_code", insertable = false, updatable = false)
    private String tenantCode;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumns({
            @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no"),
            @JoinColumn(name = "tenant_code", referencedColumnName = "tenant_code")
    })
    private CustomerDetails customerDetails;

    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL
    )
    @JoinColumn(name = "bill_id", referencedColumnName = "id")
    private List<BillOrdersAssociation> ordersAssociations;

    public static BillDetails toEntity(BillModel model, CustomerDetails customerDetails) {
        return BillDetails.builder()
                .advancePaid(model.getAdvancePaid())
                .status(model.getStatus())
                .discount(model.getDiscount())
                .balanceAmount(model.getBalanceAmount())
                .totalAmount(model.getTotalAmount())
                .customerDetails(customerDetails)
                .build();
    }

    public BillModel toModel() {
        List<OrderModel> orders = ordersAssociations.stream().map((order) ->
            order.getOrderDetails().toModel()
        ).toList();
        return BillModel.builder()
                .id(id)
                .advancePaid(advancePaid)
                .balanceAmount(balanceAmount)
                .totalAmount(totalAmount)
                .status(status)
                .discount(discount)
                .createdDate(createdDate)
                .mobileNo(customerDetails.getMobileNo())
                .tenantCode(customerDetails.getTenantCode())
                .orders(orders)
                .remarks(remarks)
                .build();
    }
}
