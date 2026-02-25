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
import org.apache.commons.lang3.StringUtils;
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

    public List<OrderModel> getOrders(String tenantCode) {
        List<OrderDetails> orders = ordersRepository.getOrders(tenantCode);
        return orders.stream().map((order) -> {
            log.info("VV :{}", order.getOrderItems() != null ? order.getOrderItems().size() : 0);
            return order.toModel();
        }).toList();
    }

    public List<OrderModel> searchOrders(String searchTerm, String tenantCode) {
        if (StringUtils.isBlank(searchTerm)) {
            return getOrders(tenantCode);
        }
        List<OrderDetails> orders = ordersRepository.searchOrders(searchTerm, tenantCode);
        return orders.stream().map((order) -> {
            log.info("VV :{}", order.getOrderItems() != null ? order.getOrderItems().size() : 0);
            return order.toModel();
        }).toList();
    }

    public List<OrderModel> getOrdersByDateRange(OffsetDateTime fromDate, OffsetDateTime toDate, String tenantCode) {
        List<OrderDetails> orders = ordersRepository.getOrdersByDateRange(fromDate, toDate, tenantCode);
        return orders.stream().map(OrderDetails::toModel).toList();
    }

    public List<OrderModel> searchOrdersWithDateRange(String searchTerm, OffsetDateTime fromDate, OffsetDateTime toDate, String tenantCode) {
        List<OrderDetails> orders;
        if (StringUtils.isBlank(searchTerm)) {
            orders = ordersRepository.getOrdersByDateRange(fromDate, toDate, tenantCode);
        } else {
            orders = ordersRepository.searchOrdersWithDateRange(searchTerm, fromDate, toDate, tenantCode);
        }
        return orders.stream().map(OrderDetails::toModel).toList();
    }

    @Transactional
    public void createOrder(OrderModel orderModel, String tenantCode) {
        try {
            CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(
                    orderModel.getMobileNo(), tenantCode);
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

    @Transactional
    public OrderModel updateOrder(OrderModel orderModel, String tenantCode) {
        try {
            OrderDetails orderDetails = ordersRepository.getOrderById(orderModel.getId(), tenantCode);
            if (orderDetails != null) {
                orderDetails.setStatus(orderModel.getStatus());
                orderDetails.setTotalItems(orderModel.getTotalItems());
                orderDetails.setTotal(orderModel.getTotal());
                orderDetails.setAdvance(orderModel.getAdvance());
                orderDetails.setBalance(orderModel.getBalance());
                orderDetails.setDeliveryDate(orderModel.getDeliveryDate());
                orderDetails.setCuttingDate(orderModel.getCuttingDate());
                orderDetails.setPackagingDate(orderModel.getPackagingDate());
                orderDetails.setRemarks(orderModel.getRemarks());
                orderDetails.setEstimateAmount(orderModel.getEstimateAmount());
                orderDetails = ordersRepository.save(orderDetails);

                if (orderModel.getOrderItems() != null && !orderModel.getOrderItems().isEmpty()) {
                    CustomerDetails customerDetails = orderDetails.getCustomerDetails();

                    List<OrderItemDetails> existingItems = ordersRepository.getOrderItemsByOrderId(orderModel.getId(), tenantCode);
                    for (OrderItemDetails existingItem : existingItems) {
                        List<OrderItemCost> existingCosts = ordersRepository.getOrderItemCostByItemId(existingItem.getId(), tenantCode);
                        ordersRepository.deleteAll(existingCosts);
                    }
                    ordersRepository.deleteAll(existingItems);

                    List<OrderItemDetails> orderItemDetails = new ArrayList<>();

                    for (OrderItemModel itemModel : orderModel.getOrderItems()) {
                        log.info("Looking for measurement ID: {}", itemModel.getMeasurementId());
                        Optional<CustomerMeasurementDetails> customerMeasurementDetailsOpt =
                                customerMeasurementRepository.findById(itemModel.getMeasurementId());

                        log.info("Measurement found: {}", customerMeasurementDetailsOpt.isPresent());
                        if (customerMeasurementDetailsOpt.isEmpty()) {
                            List<CustomerMeasurementDetails> allMeasurements = customerMeasurementRepository.findAll();
                            log.error("Measurement not found with ID: {}. Total measurements in DB: {}",
                                itemModel.getMeasurementId(), allMeasurements.size());
                            throw new RuntimeException("Measurement not found with ID: " + itemModel.getMeasurementId());
                        }

                        OrderItemDetails itemDetails = OrderItemDetails.toEntity(itemModel, customerDetails,
                                customerMeasurementDetailsOpt.get(), orderDetails);
                        itemDetails.setStatus(itemModel.getStatus() != null ? itemModel.getStatus() : OrderStatus.in_progress);
                        orderItemDetails.add(itemDetails);
                    }

                    orderItemDetails = ordersRepository.saveAll(orderItemDetails);

                    List<OrderItemCost> costsEntity = new ArrayList<>();
                    int itemIndex = 0;
                    for (OrderItemModel itemModel : orderModel.getOrderItems()) {
                        OrderItemDetails savedItem = orderItemDetails.get(itemIndex);
                        List<OrderItemCostModel> itemCosts = itemModel.getItemsCost();
                        if (itemCosts != null) {
                            for (OrderItemCostModel itemCostModel : itemCosts) {
                                costsEntity.add(OrderItemCost.toEntity(itemCostModel, customerDetails, savedItem));
                            }
                        }
                        itemIndex++;
                    }
                    ordersRepository.saveAll(costsEntity);
                    log.info("Order items and costs updated for order - {}", orderDetails.getId());
                }

                log.info("Order - {} updated successfully", orderDetails.getId());
                return orderDetails.toModel();
            } else {
                log.error("Order with id - {} doesn't exist", orderModel.getId());
                throw new RuntimeException("Order not found");
            }
        } catch (Exception e) {
            log.error("Error while updating the order - {}", orderModel.getId(), e);
            throw e;
        }
    }
}
