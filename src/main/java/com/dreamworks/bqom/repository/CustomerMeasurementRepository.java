package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.repository.entity.CustomerMeasurementDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerMeasurementRepository extends JpaRepository<CustomerMeasurementDetails, Long> {

    @Query("select cmd from CustomerMeasurementDetails cmd where cmd.customerDetails.mobileNo = :mobileNo")
    List<CustomerMeasurementDetails> getMeasurementByMobileNo(@Param("mobileNo") String mobileNo);

    @Query("select cmd from CustomerMeasurementDetails cmd where cmd.customerDetails.mobileNo = :mobileNo and cmd.name = :name")
    List<CustomerMeasurementDetails> getMeasurementByMobileNoAndName(@Param("mobileNo") String mobileNo,
                                                                     @Param("name") String name);

    @Query("select cmd from CustomerMeasurementDetails cmd where cmd.customerDetails.mobileNo = :mobileNo and cmd.name = :name and cmd.dressType = :dressType")
    List<CustomerMeasurementDetails> getMeasurementByMobileNameDress(@Param("mobileNo") String mobileNo,
                                                                     @Param("name") String name,
                                                                     @Param("dressType") String dressType);

    @Query("select cmd from CustomerMeasurementDetails cmd where " +
           "lower(cmd.customerDetails.name) like lower(concat('%', :searchTerm, '%')) or " +
           "cmd.customerDetails.mobileNo like concat('%', :searchTerm, '%') or " +
           "lower(cmd.name) like lower(concat('%', :searchTerm, '%')) or " +
           "lower(cmd.dressType) like lower(concat('%', :searchTerm, '%'))")
    List<CustomerMeasurementDetails> searchMeasurements(@Param("searchTerm") String searchTerm);

}
