package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.CustomerMeasurementDetails;
import com.dreamworks.bqom.repository.entity.OrderDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrdersRepository extends JpaRepository<OrderDetails, Long> {
}
