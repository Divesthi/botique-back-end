package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.repository.enums.BillStatus;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.OffsetDateTime;

public class BillDetails implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
    private CustomerDetails customerDetails;

}
