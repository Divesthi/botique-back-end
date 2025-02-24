package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.order.OrderItemCostModel;
import com.dreamworks.bqom.model.order.OrderItemModel;
import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.CustomerMeasurementRepository;
import com.dreamworks.bqom.repository.CustomersRepository;
import com.dreamworks.bqom.repository.OrdersRepository;
import com.dreamworks.bqom.repository.entity.*;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
public class OrdersService {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private CustomersRepository customersRepository;
    @Autowired
    private CustomerMeasurementRepository customerMeasurementRepository;

    public List<OrderModel> getOrders() {
        List<OrderDetails> orders = ordersRepository.getOrders();
        return orders.stream().map((order) -> {
            log.info("VV :{}", order.getOrderItems().size());
            return order.toModel();
        }).toList();
    }

    @Transactional
    public void createOrder(OrderModel orderModel) {
        try {
            //TODO: Do we need to check for the null against CustomerDetails??
            CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(orderModel.getMobileNo());
            OrderDetails orderDetails = OrderDetails.toEntity(orderModel, customerDetails);
            orderDetails.setStatus(OrderStatus.fresh);
            orderDetails.setReceivedDate(OffsetDateTime.now());
            orderDetails = ordersRepository.save(orderDetails);
            List<OrderItemModel> orderItemModels = orderModel.getOrderItems();
            List<OrderItemDetails> orderItemDetails = new ArrayList<>(1);
            Map<Long, List<OrderItemCostModel>> itemCostMap = new HashMap<>(1);
            for (OrderItemModel itemModel : orderItemModels) {
                Optional<CustomerMeasurementDetails> customerMeasurementDetailsOpt =
                        customerMeasurementRepository.findById(itemModel.getMeasurementId());
                OrderItemDetails itemDetails = OrderItemDetails.toEntity(itemModel, customerDetails, customerMeasurementDetailsOpt.get()
                        , orderDetails);
                itemDetails.setStatus(OrderStatus.in_progress);
                orderItemDetails.add(itemDetails);
                //Store the Order Item Cost to retrieve it faster
                itemCostMap.put(itemModel.getMeasurementId(), itemModel.getItemsCost());
            }
            orderItemDetails = ordersRepository.saveAll(orderItemDetails);
            List<OrderItemCost> costsEntity = new ArrayList<>(1);
            for (OrderItemDetails orderItem: orderItemDetails) {
                List<OrderItemCostModel> itemCosts = itemCostMap.get(orderItem.getCustomerMeasurementDetails().getId());
                for (OrderItemCostModel itemCostModel : itemCosts) {
                    costsEntity.add(OrderItemCost.toEntity(itemCostModel, customerDetails, orderItem));
                }
            }
            ordersRepository.saveAll(costsEntity);
            log.info("Order - {} created successfully for the customer - {}", orderDetails.getId(), customerDetails.getMobileNo());
        } catch (Exception e) {
            log.error("Error while creating the order for the customer - {}", orderModel.getMobileNo());
            throw e;
        }
    }
}
