package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.OrderDetails;
import com.dreamworks.bqom.repository.entity.OrderItemDetails;
import com.dreamworks.bqom.repository.entity.OrderItemCost;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdersRepository extends JpaRepository<BaseEntity, Long> {

    @Query("select od from OrderDetails od")
    List<OrderDetails> getOrders();

    @Query("select od from OrderDetails od where od.id in (:orderIds)")
    List<OrderDetails> getOrdersByIds(@Param("orderIds") List<Long> orderIds);

    @Query("select od from OrderDetails od where od.id = :orderId")
    OrderDetails getOrderById(@Param("orderId") Long orderId);

    @Query("select od from OrderDetails od where od.customerDetails.mobileNo = :mobileNo")
    List<OrderDetails> getOrdersByMobileNumber(@Param("mobileNo") String mobileNo);

    @Query("select oid from OrderItemDetails oid where oid.orderDetails.id = :orderId")
    List<OrderItemDetails> getOrderItemsByOrderId(@Param("orderId") Long orderId);

    @Query("select oic from OrderItemCost oic where oic.orderItemDetails.id = :itemId")
    List<OrderItemCost> getOrderItemCostByItemId(@Param("itemId") Long itemId);

    @Query("select od from OrderDetails od where " +
           "lower(od.customerDetails.name) like lower(concat('%', :searchTerm, '%')) or " +
           "od.customerDetails.mobileNo like concat('%', :searchTerm, '%') or " +
           "cast(od.id as string) like concat('%', :searchTerm, '%')")
    List<OrderDetails> searchOrders(@Param("searchTerm") String searchTerm);
}
