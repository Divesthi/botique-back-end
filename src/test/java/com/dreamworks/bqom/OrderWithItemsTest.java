package com.dreamworks.bqom;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import com.dreamworks.bqom.model.customer.CustomerMeasurementModel;
import com.dreamworks.bqom.model.order.OrderItemCostModel;
import com.dreamworks.bqom.model.order.OrderItemModel;
import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.repository.enums.OrderStatus;
import com.dreamworks.bqom.service.CustomersService;
import com.dreamworks.bqom.service.OrdersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class OrderWithItemsTest {

    @Autowired
    private OrdersService ordersService;

    @Autowired
    private CustomersService customersService;

    private String testMobileNo;
    private Long testMeasurementId;

    @BeforeEach
    public void setUp() {
        // Create a test customer
        testMobileNo = "9999888877";
        CustomerDetailsModel customer = CustomerDetailsModel.builder()
                .mobileNo(testMobileNo)
                .name("Test Customer")
                .address("Test Address")
                .build();
        customersService.createCustomer(customer);

        // Create a test measurement
        Map<String, Object> measurementData = new HashMap<>();
        measurementData.put("shoulder", "15");
        measurementData.put("chest", "38");
        measurementData.put("waist", "32");
        measurementData.put("length", "40");

        CustomerMeasurementModel measurement = CustomerMeasurementModel.builder()
                .mobileNo(testMobileNo)
                .name("Test Customer")
                .dressType("Shirt")
                .measurement(measurementData)
                .build();

        CustomerMeasurementModel createdMeasurement = customersService.createCustomerMeasurement(measurement);
        testMeasurementId = createdMeasurement.getId();
    }

    @Test
    public void testCreateOrderWithItemsAndCosts() {
        // Prepare order item costs
        List<OrderItemCostModel> itemCosts = new ArrayList<>();
        itemCosts.add(OrderItemCostModel.builder()
                .cost(300.0)
                .type("Material")
                .mobileNo(testMobileNo)
                .build());
        itemCosts.add(OrderItemCostModel.builder()
                .cost(150.0)
                .type("Labor")
                .mobileNo(testMobileNo)
                .build());
        itemCosts.add(OrderItemCostModel.builder()
                .cost(50.0)
                .type("Buttons")
                .mobileNo(testMobileNo)
                .build());

        // Prepare order item
        List<OrderItemModel> orderItems = new ArrayList<>();
        orderItems.add(OrderItemModel.builder()
                .measurementId(testMeasurementId)
                .mobileNo(testMobileNo)
                .quantity(2)
                .costPerQuantity(500.0)
                .status(OrderStatus.fresh)
                .remarks("Test item")
                .itemsCost(itemCosts)
                .build());

        // Prepare order
        OrderModel order = OrderModel.builder()
                .mobileNo(testMobileNo)
                .status(OrderStatus.fresh)
                .totalItems(1)
                .total(1000.0)
                .advance(300.0)
                .balance(700.0)
                .receivedDate(OffsetDateTime.now())
                .deliveryDate(OffsetDateTime.now().plusDays(7))
                .remarks("Test order with items and costs")
                .orderItems(orderItems)
                .build();

        // Create the order (returns void)
        ordersService.createOrder(order);

        // Get all orders to verify
        List<OrderModel> allOrders = ordersService.getOrders();
        OrderModel createdOrder = allOrders.stream()
                .filter(o -> testMobileNo.equals(o.getMobileNo()))
                .findFirst()
                .orElse(null);

        // Assertions
        assertNotNull(createdOrder, "Order should be created");
        assertEquals(testMobileNo, createdOrder.getMobileNo());
        assertEquals(OrderStatus.fresh, createdOrder.getStatus());
        assertEquals(1, createdOrder.getTotalItems());
        assertEquals(1000.0, createdOrder.getTotal());

        // Verify order items are included
        assertNotNull(createdOrder.getOrderItems(), "Order should have items");
        assertEquals(1, createdOrder.getOrderItems().size(), "Order should have 1 item");

        OrderItemModel createdItem = createdOrder.getOrderItems().get(0);
        assertNotNull(createdItem.getId(), "Item should have an ID");
        assertEquals(testMeasurementId, createdItem.getMeasurementId());
        assertEquals(2, createdItem.getQuantity());
        assertEquals(500.0, createdItem.getCostPerQuantity());

        // Verify item costs are included
        assertNotNull(createdItem.getItemsCost(), "Item should have costs");
        assertEquals(3, createdItem.getItemsCost().size(), "Item should have 3 costs");

        double totalCost = createdItem.getItemsCost().stream()
                .mapToDouble(OrderItemCostModel::getCost)
                .sum();
        assertEquals(500.0, totalCost, 0.01); // 300 + 150 + 50
    }

    @Test
    public void testGetOrderIncludesItemsAndCosts() {
        // Create an order with items
        testCreateOrderWithItemsAndCosts();

        // Get all orders
        List<OrderModel> orders = ordersService.getOrders();

        // Find the test order
        OrderModel fetchedOrder = orders.stream()
                .filter(o -> testMobileNo.equals(o.getMobileNo()))
                .findFirst()
                .orElse(null);

        // Assertions
        assertNotNull(fetchedOrder);
        assertNotNull(fetchedOrder.getOrderItems());
        assertTrue(fetchedOrder.getOrderItems().size() > 0);

        OrderItemModel item = fetchedOrder.getOrderItems().get(0);
        assertNotNull(item.getItemsCost());
        assertEquals(3, item.getItemsCost().size());

        // Verify cost types
        List<String> costTypes = item.getItemsCost().stream()
                .map(OrderItemCostModel::getType)
                .toList();
        assertTrue(costTypes.contains("Material"));
        assertTrue(costTypes.contains("Labor"));
        assertTrue(costTypes.contains("Buttons"));
    }

    @Test
    public void testUpdateOrderWithItems() {
        // Create initial order
        testCreateOrderWithItemsAndCosts();

        // Get the created order
        List<OrderModel> orders = ordersService.getOrders();
        OrderModel existingOrder = orders.stream()
                .filter(o -> testMobileNo.equals(o.getMobileNo()))
                .findFirst()
                .orElse(null);

        assertNotNull(existingOrder);

        // Prepare updated item costs
        List<OrderItemCostModel> updatedCosts = new ArrayList<>();
        updatedCosts.add(OrderItemCostModel.builder()
                .cost(400.0)
                .type("Material")
                .mobileNo(testMobileNo)
                .build());
        updatedCosts.add(OrderItemCostModel.builder()
                .cost(200.0)
                .type("Labor")
                .mobileNo(testMobileNo)
                .build());
        updatedCosts.add(OrderItemCostModel.builder()
                .cost(100.0)
                .type("Thread")
                .mobileNo(testMobileNo)
                .build());

        // Prepare updated order items
        List<OrderItemModel> updatedItems = new ArrayList<>();
        updatedItems.add(OrderItemModel.builder()
                .measurementId(testMeasurementId)
                .mobileNo(testMobileNo)
                .quantity(3)
                .costPerQuantity(700.0)
                .status(OrderStatus.in_progress)
                .remarks("Updated item")
                .itemsCost(updatedCosts)
                .build());

        // Update the order
        existingOrder.setStatus(OrderStatus.in_progress);
        existingOrder.setTotal(2100.0);
        existingOrder.setOrderItems(updatedItems);

        OrderModel updatedOrder = ordersService.updateOrder(existingOrder);

        // Assertions
        assertNotNull(updatedOrder);
        assertEquals(OrderStatus.in_progress, updatedOrder.getStatus());
        assertEquals(2100.0, updatedOrder.getTotal());

        // Verify updated items
        assertNotNull(updatedOrder.getOrderItems());
        assertEquals(1, updatedOrder.getOrderItems().size());

        OrderItemModel updatedItem = updatedOrder.getOrderItems().get(0);
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(700.0, updatedItem.getCostPerQuantity());

        // Verify updated costs
        assertNotNull(updatedItem.getItemsCost());
        assertEquals(3, updatedItem.getItemsCost().size());

        double totalCost = updatedItem.getItemsCost().stream()
                .mapToDouble(OrderItemCostModel::getCost)
                .sum();
        assertEquals(700.0, totalCost, 0.01); // 400 + 200 + 100
    }

    @Test
    public void testCreateOrderWithMultipleItems() {
        // Prepare first item costs
        List<OrderItemCostModel> item1Costs = new ArrayList<>();
        item1Costs.add(OrderItemCostModel.builder()
                .cost(300.0)
                .type("Material")
                .mobileNo(testMobileNo)
                .build());

        // Prepare second item costs
        List<OrderItemCostModel> item2Costs = new ArrayList<>();
        item2Costs.add(OrderItemCostModel.builder()
                .cost(500.0)
                .type("Material")
                .mobileNo(testMobileNo)
                .build());
        item2Costs.add(OrderItemCostModel.builder()
                .cost(200.0)
                .type("Labor")
                .mobileNo(testMobileNo)
                .build());

        // Prepare order items
        List<OrderItemModel> orderItems = new ArrayList<>();
        orderItems.add(OrderItemModel.builder()
                .measurementId(testMeasurementId)
                .mobileNo(testMobileNo)
                .quantity(1)
                .costPerQuantity(300.0)
                .status(OrderStatus.fresh)
                .itemsCost(item1Costs)
                .build());
        orderItems.add(OrderItemModel.builder()
                .measurementId(testMeasurementId)
                .mobileNo(testMobileNo)
                .quantity(2)
                .costPerQuantity(700.0)
                .status(OrderStatus.fresh)
                .itemsCost(item2Costs)
                .build());

        // Prepare order
        OrderModel order = OrderModel.builder()
                .mobileNo(testMobileNo)
                .status(OrderStatus.fresh)
                .totalItems(2)
                .total(1700.0) // 300 + 1400
                .advance(500.0)
                .balance(1200.0)
                .receivedDate(OffsetDateTime.now())
                .orderItems(orderItems)
                .build();

        // Create the order
        ordersService.createOrder(order);

        // Get all orders to verify
        List<OrderModel> allOrders = ordersService.getOrders();
        OrderModel createdOrder = allOrders.stream()
                .filter(o -> testMobileNo.equals(o.getMobileNo()))
                .findFirst()
                .orElse(null);

        // Assertions
        assertNotNull(createdOrder);
        assertEquals(2, createdOrder.getTotalItems());
        assertNotNull(createdOrder.getOrderItems());
        assertEquals(2, createdOrder.getOrderItems().size());

        // Verify first item
        OrderItemModel item1 = createdOrder.getOrderItems().get(0);
        assertEquals(1, item1.getQuantity());
        assertEquals(1, item1.getItemsCost().size());

        // Verify second item
        OrderItemModel item2 = createdOrder.getOrderItems().get(1);
        assertEquals(2, item2.getQuantity());
        assertEquals(2, item2.getItemsCost().size());
    }
}
