package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.bill.BillModel;
import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.BillRepository;
import com.dreamworks.bqom.repository.CustomersRepository;
import com.dreamworks.bqom.repository.OrdersRepository;
import com.dreamworks.bqom.repository.entity.BillDetails;
import com.dreamworks.bqom.repository.entity.BillOrdersAssociation;
import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.repository.entity.OrderDetails;
import com.dreamworks.bqom.repository.enums.BillStatus;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class BillsService {
    @Autowired
    private BillRepository billRepository;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private CustomersRepository customersRepository;

    public List<BillModel> getBills() {
        return billRepository.getBills().stream().map((bill) -> bill.toModel()).toList();
    }

    public List<BillModel> searchBills(String searchTerm) {
        if (StringUtils.isBlank(searchTerm)) {
            return getBills();
        }
        return billRepository.searchBills(searchTerm).stream().map((bill) -> bill.toModel()).toList();
    }

    public List<BillModel> getBillsByDateRange(OffsetDateTime fromDate, OffsetDateTime toDate) {
        return billRepository.getBillsByDateRange(fromDate, toDate).stream().map(BillDetails::toModel).toList();
    }

    public List<BillModel> searchBillsWithDateRange(String searchTerm, OffsetDateTime fromDate, OffsetDateTime toDate) {
        List<BillDetails> bills;
        if (StringUtils.isBlank(searchTerm)) {
            bills = billRepository.getBillsByDateRange(fromDate, toDate);
        } else {
            bills = billRepository.searchBillsWithDateRange(searchTerm, fromDate, toDate);
        }
        return bills.stream().map(BillDetails::toModel).toList();
    }

    @Transactional
    public void createBill(BillModel billModel) {
        try {
            CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(billModel.getMobileNo());
            List<OrderModel> orderModels = billModel.getOrders();
            if (orderModels == null || orderModels.isEmpty()) {
                log.error("No orders associated with the given bill.");
                throw new RuntimeException("No orders associated with the given bill");
            } else {
                List<Long> orderIds = orderModels.stream().map(OrderModel::getId).toList();
                List<OrderDetails> orders = ordersRepository.getOrdersByIds(orderIds);
                if (orderModels.size() != orders.size()) {
                    log.error("One of the given orders doesn't exists in the system. Provided Order ids - {}",
                            orderIds);
                    throw new RuntimeException("One of the given orders doesn't exists in the system");
                }
                BillDetails billDetails = BillDetails.toEntity(billModel, customerDetails);
                billDetails.setCreatedDate(OffsetDateTime.now());
                billDetails.setStatus(BillStatus.fresh);
                BillDetails bill = billRepository.save(billDetails);
                log.info("Bill created successfully for the customer - {}", billModel.getMobileNo());
                List<BillOrdersAssociation> billOrdersAssociations = orders.stream().map((order) ->
                        BillOrdersAssociation.toEntity(bill, order, customerDetails)).toList();
                billRepository.saveAll(billOrdersAssociations);
                log.info("Bill and Orders association created successfully for the customer - {};" +
                        " Orders Count - {}", billModel.getMobileNo(), billOrdersAssociations.size());
            }
        } catch (Exception e) {
            log.error("Error while creating the bill for the customer - {}", billModel.getMobileNo());
            throw e;
        }
    }

    public BillModel updateBill(BillModel billModel) {
        try {
            BillDetails billDetails = billRepository.getBillById(billModel.getId());
            if (billModel.getStatus() != null) {
                billDetails.setStatus(billModel.getStatus());
            }
            if (billModel.getAdvancePaid() != null) {
                billDetails.setAdvancePaid(billModel.getAdvancePaid());
            }
            if (billModel.getBalanceAmount() != null) {
                billDetails.setBalanceAmount(billModel.getBalanceAmount());
            }
            if (billModel.getTotalAmount() != null) {
                billDetails.setTotalAmount(billModel.getTotalAmount());
            }
            if (billModel.getDiscount() != null) {
                billDetails.setDiscount(billModel.getDiscount());
            }
            if (!StringUtils.isEmpty(billModel.getRemarks())) {
                billDetails.setRemarks(billModel.getRemarks());
            }
            billDetails = billRepository.save(billDetails);
            return billDetails.toModel();
        } catch (Exception e) {
            log.error("Error while updating the bill for the customer - {}", billModel.getMobileNo(), e);
            throw e;
        }
    }
}
