package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.OrderDetails;
import com.dreamworks.bqom.repository.entity.OrderItemDetails;
import com.dreamworks.bqom.repository.entity.OrderItemCost;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface OrdersRepository extends JpaRepository<BaseEntity, Long> {

    @Query("select od from OrderDetails od where od.customerDetails.tenantCode = :tenantCode")
    List<OrderDetails> getOrders(@Param("tenantCode") String tenantCode);

    @Query("select od from OrderDetails od where od.id in (:orderIds) and od.customerDetails.tenantCode = :tenantCode")
    List<OrderDetails> getOrdersByIds(@Param("orderIds") List<Long> orderIds,
                                      @Param("tenantCode") String tenantCode);

    @Query("select od from OrderDetails od where od.id = :orderId and od.customerDetails.tenantCode = :tenantCode")
    OrderDetails getOrderById(@Param("orderId") Long orderId, @Param("tenantCode") String tenantCode);

    @Query("select od from OrderDetails od where od.customerDetails.mobileNo = :mobileNo and od.customerDetails.tenantCode = :tenantCode")
    List<OrderDetails> getOrdersByMobileNumber(@Param("mobileNo") String mobileNo,
                                               @Param("tenantCode") String tenantCode);

    @Query("select oid from OrderItemDetails oid where oid.orderDetails.id = :orderId and oid.customerDetails.tenantCode = :tenantCode")
    List<OrderItemDetails> getOrderItemsByOrderId(@Param("orderId") Long orderId,
                                                  @Param("tenantCode") String tenantCode);

    @Query("select oic from OrderItemCost oic where oic.orderItemDetails.id = :itemId and oic.customerDetails.tenantCode = :tenantCode")
    List<OrderItemCost> getOrderItemCostByItemId(@Param("itemId") Long itemId,
                                                 @Param("tenantCode") String tenantCode);

    @Query("select od from OrderDetails od where " +
           "(lower(od.customerDetails.name) like lower(concat('%', :searchTerm, '%')) or " +
           "od.customerDetails.mobileNo like concat('%', :searchTerm, '%') or " +
           "cast(od.id as string) like concat('%', :searchTerm, '%')) and " +
           "od.customerDetails.tenantCode = :tenantCode")
    List<OrderDetails> searchOrders(@Param("searchTerm") String searchTerm,
                                    @Param("tenantCode") String tenantCode);

    @Query("select od from OrderDetails od where od.deliveryDate >= :fromDate and od.deliveryDate <= :toDate and od.customerDetails.tenantCode = :tenantCode")
    List<OrderDetails> getOrdersByDateRange(@Param("fromDate") OffsetDateTime fromDate,
                                            @Param("toDate") OffsetDateTime toDate,
                                            @Param("tenantCode") String tenantCode);

    @Query("select od from OrderDetails od where " +
           "(lower(od.customerDetails.name) like lower(concat('%', :searchTerm, '%')) or " +
           "od.customerDetails.mobileNo like concat('%', :searchTerm, '%') or " +
           "cast(od.id as string) like concat('%', :searchTerm, '%')) and " +
           "od.deliveryDate >= :fromDate and od.deliveryDate <= :toDate and " +
           "od.customerDetails.tenantCode = :tenantCode")
    List<OrderDetails> searchOrdersWithDateRange(@Param("searchTerm") String searchTerm,
                                                  @Param("fromDate") OffsetDateTime fromDate,
                                                  @Param("toDate") OffsetDateTime toDate,
                                                  @Param("tenantCode") String tenantCode);
}
