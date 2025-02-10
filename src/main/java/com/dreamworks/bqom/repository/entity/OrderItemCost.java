package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.OrderItemCostModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemCost implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cost")
    private Double cost;

    @Column(name = "type")
    private String type;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
    private CustomerDetails customerDetails;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "item_id", referencedColumnName = "id")
    private OrderItemDetails orderItemDetails;

    public static OrderItemCost toEntity(OrderItemCostModel model, CustomerDetails customerDetails) {
        return OrderItemCost.builder().build();
    }

    public OrderItemCostModel toModel() {
        return OrderItemCostModel.builder()
                .id(id)
                .cost(cost)
                .orderId(orderItemDetails.getId())
                .type(type)
                .build();
    }

}
