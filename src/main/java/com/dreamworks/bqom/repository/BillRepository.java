package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.BillDetails;
import com.dreamworks.bqom.repository.entity.OrderDetails;
import com.dreamworks.bqom.repository.entity.base.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillRepository extends JpaRepository<BaseEntity, Long> {

    @Query("select bd from BillDetails bd")
    List<BillDetails> getBills();

    @Query("select bd from BillDetails bd where bd.id = :billId")
    BillDetails getBillById(@Param("billId") Long billId);

    @Query("select bd from BillDetails bd where bd.customerDetails.mobileNo = :mobileNo")
    List<BillDetails> getBillsByMobileNumber(@Param("mobileNo") String mobileNo);

    @Query("select boa.orderDetails from BillOrdersAssociation boa where boa.billDetails.id = :billId")
    List<OrderDetails> getOrdersForBillId(@Param("billId") String billId);

    @Query("select bd from BillDetails bd where " +
           "lower(bd.customerDetails.name) like lower(concat('%', :searchTerm, '%')) or " +
           "bd.customerDetails.mobileNo like concat('%', :searchTerm, '%') or " +
           "cast(bd.id as string) like concat('%', :searchTerm, '%')")
    List<BillDetails> searchBills(@Param("searchTerm") String searchTerm);

}
