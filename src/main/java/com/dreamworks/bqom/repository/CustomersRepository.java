package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.CustomerDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomersRepository extends JpaRepository<CustomerDetails, Long> {

    List<CustomerDetails> findAllByTenantCode(String tenantCode);

    @Query("select cd from CustomerDetails cd where cd.mobileNo = :mobileNo and cd.tenantCode = :tenantCode")
    CustomerDetails getCustomerDetailsByMobileNumber(@Param("mobileNo") String mobileNo,
                                                     @Param("tenantCode") String tenantCode);

    @Query("select cd from CustomerDetails cd where " +
           "(lower(cd.name) like lower(concat('%', :searchTerm, '%')) or " +
           "cd.mobileNo like concat('%', :searchTerm, '%') or " +
           "cd.alternateContactNo like concat('%', :searchTerm, '%')) and " +
           "cd.tenantCode = :tenantCode")
    List<CustomerDetails> searchCustomers(@Param("searchTerm") String searchTerm,
                                          @Param("tenantCode") String tenantCode);
}
