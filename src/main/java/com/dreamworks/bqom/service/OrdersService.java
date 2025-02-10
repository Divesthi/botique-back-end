package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.OrdersModel;
import com.dreamworks.bqom.repository.CustomersRepository;
import com.dreamworks.bqom.repository.OrdersRepository;
import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.repository.entity.OrderDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OrdersService {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private CustomersRepository customersRepository;

    public List<OrdersModel> getOrders() {
        List<OrderDetails> orders = ordersRepository.findAll();
        return orders.stream().map((order) -> order.toModel()).toList();
    }

    public OrdersModel createOrder(OrdersModel ordersModel) {
        try {
            CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(ordersModel.getMobileNo());
            //TODO: Do we need to check for the null against CustomerDetails??


        } catch (Exception e) {
            log.error("Error while creating the order for the customer - {}", ordersModel.getMobileNo());
            throw e;
        }
        return null;
    }
}
