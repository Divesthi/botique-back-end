package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.repository.entity.CustomerMeasurementDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomersRepository extends JpaRepository<CustomerDetails, Long> {

    @Query("select cd from CustomerDetails cd where cd.mobileNo = :mobileNo")
    CustomerDetails getCustomerDetailsByMobileNumber(@Param("mobileNo") String mobileNo);
}
