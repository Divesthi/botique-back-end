package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.CustomerMeasurementModel;
import com.dreamworks.bqom.model.OrderItemModel;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDetails implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "cost_per_quantity")
    private Double costPerQuantity;

    @Column(name = "quantity")
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
    private CustomerDetails customerDetails;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    private OrderDetails orderDetails;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "measurement_id", referencedColumnName = "id")
    private CustomerMeasurementDetails customerMeasurementDetails;

    public static OrderItemDetails toEntity(OrderItemModel model, CustomerDetails customerDetails,
                                            CustomerMeasurementDetails customerMeasurementDetails) {
        return OrderItemDetails.builder()
                .quantity(model.getQuantity())
                .costPerQuantity(model.getCostPerQuantity())
                .remarks(model.getRemarks())
                .status(model.getStatus())
                .customerDetails(customerDetails)
                .customerMeasurementDetails(customerMeasurementDetails)
                .build();
    }
}
