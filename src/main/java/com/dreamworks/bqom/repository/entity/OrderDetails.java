package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.OrdersModel;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Slf4j
@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetails implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "received_date")
    private OffsetDateTime receivedDate;

    @Column(name = "cutting_date")
    private OffsetDateTime cuttingDate;

    @Column(name = "delivery_date")
    private OffsetDateTime deliveryDate;

    @Column(name = "packaging_date")
    private OffsetDateTime packagingDate;

    @Column(name = "total_items")
    private int totalItems;

    @Column(name = "remarks")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "total")
    private Double total;

    @Column(name = "advance")
    private Double advance;

    @Column(name = "balance")
    private Double balance;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
    private CustomerDetails customerDetails;

    public static OrderDetails toEntity(OrdersModel model, CustomerDetails customerDetails) {
        model.getOrderItems();

        return OrderDetails.builder()
                .status(model.getStatus())
                .advance(model.getAdvance())
                .balance(model.getBalance())
                .total(model.getTotal())
                .totalItems(model.getTotalItems())
                .customerDetails(customerDetails)
                .cuttingDate(model.getCuttingDate())
                .receivedDate(model.getReceivedDate())
                .packagingDate(model.getPackagingDate())
                .deliveryDate(model.getDeliveryDate())
                .remarks(model.getRemarks())
                .build();
    }

    public OrdersModel toModel() {
        return OrdersModel.builder()
                .id(id)
                .status(status)
                .totalItems(totalItems)
                .receivedDate(receivedDate)
                .cuttingDate(cuttingDate)
                .packagingDate(packagingDate)
                .deliveryDate(deliveryDate)
                .total(total)
                .advance(advance)
                .balance(balance)
                .remarks(remarks)
                .mobileNo(customerDetails.getMobileNo())
                .build();
    }
}
