package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.notification.NotificationMessage;
import com.dreamworks.bqom.model.order.OrderItemCostModel;
import com.dreamworks.bqom.model.order.OrderItemModel;
import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.CustomerMeasurementRepository;
import com.dreamworks.bqom.repository.CustomersRepository;
import com.dreamworks.bqom.repository.OrdersRepository;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.entity.*;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import com.dreamworks.bqom.service.notification.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Transactional(readOnly = true)
    public List<OrderModel> getOrders(String tenantCode) {
        List<OrderDetails> orders = ordersRepository.getOrders(tenantCode);
        return orders.stream().map((order) -> {
            log.info("VV :{}", order.getOrderItems() != null ? order.getOrderItems().size() : 0);
            return order.toModel();
        }).toList();
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<OrderModel> getOrdersByDateRange(OffsetDateTime fromDate, OffsetDateTime toDate, String tenantCode) {
        List<OrderDetails> orders = ordersRepository.getOrdersByDateRange(fromDate, toDate, tenantCode);
        return orders.stream().map(OrderDetails::toModel).toList();
    }

    @Transactional(readOnly = true)
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
            orderDetails.setUpdatedDate(OffsetDateTime.now());
            orderDetails = ordersRepository.save(orderDetails);
            List<OrderItemModel> orderItemModels = orderModel.getOrderItems();
            List<OrderItemDetails> orderItemDetails = new ArrayList<>(1);
            Map<Long, List<OrderItemCostModel>> itemCostMap = new HashMap<>(1);
            for (OrderItemModel itemModel : orderItemModels) {
                Optional<CustomerMeasurementDetails> customerMeasurementDetailsOpt =
                        customerMeasurementRepository.findById(itemModel.getMeasurementId());
                OrderItemDetails itemDetails = OrderItemDetails.toEntity(itemModel, customerDetails, customerMeasurementDetailsOpt.get()
                        , orderDetails);
                itemDetails.setStatus(OrderStatus.fresh);
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
    public void deleteOrder(Long orderId, String tenantCode) {
        OrderDetails orderDetails = ordersRepository.getOrderById(orderId, tenantCode);
        if (orderDetails == null) {
            throw new RuntimeException("Order not found");
        }
        List<OrderItemDetails> items = ordersRepository.getOrderItemsByOrderId(orderId, tenantCode);
        for (OrderItemDetails item : items) {
            List<OrderItemCost> costs = ordersRepository.getOrderItemCostByItemId(item.getId(), tenantCode);
            ordersRepository.deleteAll(costs);
        }
        ordersRepository.deleteAll(items);
        ordersRepository.delete(orderDetails);
        log.info("Order {} deleted successfully", orderId);
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
                orderDetails.setUpdatedDate(OffsetDateTime.now());
                if (OrderStatus.delivered.equals(orderModel.getStatus()) && orderDetails.getDeliveredDate() == null) {
                    orderDetails.setDeliveredDate(OffsetDateTime.now());
                }
                orderDetails = ordersRepository.save(orderDetails);

                if (OrderStatus.delivered.equals(orderModel.getStatus()) &&
                        (orderModel.getOrderItems() == null || orderModel.getOrderItems().isEmpty())) {
                    List<OrderItemDetails> existingItems = ordersRepository.getOrderItemsByOrderId(orderModel.getId(), tenantCode);
                    for (OrderItemDetails item : existingItems) {
                        item.setStatus(OrderStatus.delivered);
                    }
                    ordersRepository.saveAll(existingItems);
                    log.info("Cascaded delivered status to all items for order - {}", orderDetails.getId());
                }

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

                // ── WhatsApp Notification Trigger ──────────────────────────────
                // Fire notification when order transitions to 'completed'

                if (OrderStatus.completed.equals(orderModel.getStatus())) {
                    try {
                        // Fetch customer name from customer_details
                        CustomerDetails customer = customersRepository
                                .getCustomerDetailsByMobileNumber(orderModel.getMobileNo(), tenantCode);
                        // Fetch boutique name from tenant
                        Tenant tenant = tenantRepository.findByCode(tenantCode)
                                .orElse(null);

                        if (customer != null && tenant != null) {
                            NotificationMessage notificationMessage = new NotificationMessage();
                            notificationMessage.setTenantCode(tenantCode);
                            Map<String, String> parameters = new HashMap<>(0);
                            parameters.put(NotificationConstants.CUSTOMER_NAME, customer.getName());
                            parameters.put(NotificationConstants.BOUTIQUE_NAME, tenant.getName());
                            parameters.put(NotificationConstants.ORDER_ID, orderDetails.getId().toString());
                            notificationMessage.setParameters(parameters);
                            notificationMessage.setToPhoneNumber(customer.getMobileNo());
                            notificationMessage.setToTelegramChatId(customer.getTelegramChatId());
                            notificationDispatcher.dispatchOrderStatus(notificationMessage);
                        } else {
                            log.warn("Skipping WhatsApp notification — customer or tenant not found for order {}",
                                    orderDetails.getId());
                        }
                    } catch (Exception e) {
                        // Never block order update if WhatsApp fails
                        log.error("WhatsApp notification failed for order {}: {}", orderDetails.getId() , e.getMessage(), e);
                    }
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
