package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.order.OrderItemModel;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;

@Slf4j
@Entity
@Table(name = "order_item_details")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDetails extends BaseEntity implements Serializable {
    
    ...

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE) // Added cascade type remove
    ...
}
