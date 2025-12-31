package com.dreamworks.bqom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multi-order bill functionality
 * Tests verify that bills can be created with multiple orders associated
 */
@SpringBootTest
@DisplayName("Multi-Order Bill Integration Tests")
class MultiOrderBillTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should verify bill with 2 orders exists in database")
    void testBillWithTwoOrdersExists() {
        // Query bill 13 which should have 2 orders (Rajesh Kumar)
        String query = "SELECT b.id, b.mobile_no, COUNT(ba.order_id) as order_count " +
                      "FROM bill_details b " +
                      "JOIN bill_orders_association ba ON b.id = ba.bill_id " +
                      "WHERE b.id = 13 " +
                      "GROUP BY b.id, b.mobile_no";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(query);

        assertFalse(results.isEmpty(), "Bill with ID 13 should exist");
        Map<String, Object> bill = results.get(0);
        assertEquals(13L, ((Number) bill.get("id")).longValue());
        assertEquals(2L, ((Number) bill.get("order_count")).longValue(),
                    "Bill 13 should have 2 orders");
    }

    @Test
    @DisplayName("Should verify bill with 3 orders exists in database")
    void testBillWithThreeOrdersExists() {
        // Query bill 15 which should have 3 orders (Vikram Singh)
        String query = "SELECT b.id, b.mobile_no, b.total_amount, COUNT(ba.order_id) as order_count " +
                      "FROM bill_details b " +
                      "JOIN bill_orders_association ba ON b.id = ba.bill_id " +
                      "WHERE b.id = 15 " +
                      "GROUP BY b.id, b.mobile_no, b.total_amount";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(query);

        assertFalse(results.isEmpty(), "Bill with ID 15 should exist");
        Map<String, Object> bill = results.get(0);
        assertEquals(15L, ((Number) bill.get("id")).longValue());
        assertEquals(3L, ((Number) bill.get("order_count")).longValue(),
                    "Bill 15 should have 3 orders");
        assertEquals(6700.0, ((Number) bill.get("total_amount")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Should verify all multi-order bills in database")
    void testAllMultiOrderBills() {
        String query = "SELECT b.id, c.name as customer_name, COUNT(ba.order_id) as order_count, " +
                      "GROUP_CONCAT(ba.order_id ORDER BY ba.order_id) as order_ids " +
                      "FROM bill_details b " +
                      "JOIN customer_details c ON b.mobile_no = c.mobile_no " +
                      "JOIN bill_orders_association ba ON b.id = ba.bill_id " +
                      "GROUP BY b.id, c.name " +
                      "HAVING COUNT(ba.order_id) > 1 " +
                      "ORDER BY COUNT(ba.order_id) DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(query);

        assertFalse(results.isEmpty(), "Should have at least one multi-order bill");
        assertTrue(results.size() >= 4, "Should have at least 4 multi-order bills");

        // Verify that we have a bill with 3 orders (Vikram Singh)
        boolean hasThreeOrderBill = results.stream()
            .anyMatch(bill -> ((Number) bill.get("order_count")).longValue() == 3);
        assertTrue(hasThreeOrderBill, "Should have at least one bill with 3 orders");
    }

    @Test
    @DisplayName("Should verify bill-order associations are unique")
    void testUniqueBillOrderAssociations() {
        String query = "SELECT order_id, bill_id, COUNT(*) as count " +
                      "FROM bill_orders_association " +
                      "GROUP BY order_id, bill_id " +
                      "HAVING COUNT(*) > 1";

        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(query);

        assertTrue(duplicates.isEmpty(),
                  "Each order should be associated with only one bill");
    }

    @Test
    @DisplayName("Should verify orders are linked to correct customer in bill")
    void testOrderCustomerConsistency() {
        String query = "SELECT ba.id, b.mobile_no as bill_mobile, o.mobile_no as order_mobile " +
                      "FROM bill_orders_association ba " +
                      "JOIN bill_details b ON ba.bill_id = b.id " +
                      "JOIN order_details o ON ba.order_id = o.id " +
                      "WHERE b.mobile_no != o.mobile_no";

        List<Map<String, Object>> inconsistencies = jdbcTemplate.queryForList(query);

        assertTrue(inconsistencies.isEmpty(),
                  "All orders in a bill should belong to the same customer");
    }

    @Test
    @DisplayName("Should retrieve bill details with order information")
    void testRetrieveBillWithOrderDetails() {
        // Get bill 14 (Priya Sharma) which has 2 orders
        String billQuery = "SELECT * FROM bill_details WHERE id = 14";
        List<Map<String, Object>> billResults = jdbcTemplate.queryForList(billQuery);

        assertFalse(billResults.isEmpty(), "Bill 14 should exist");

        // Get associated orders
        String ordersQuery = "SELECT o.id, o.remarks, o.total, o.status " +
                           "FROM order_details o " +
                           "JOIN bill_orders_association ba ON o.id = ba.order_id " +
                           "WHERE ba.bill_id = 14 " +
                           "ORDER BY o.id";
        List<Map<String, Object>> orderResults = jdbcTemplate.queryForList(ordersQuery);

        assertEquals(2, orderResults.size(), "Bill 14 should have 2 orders");
    }

    @Test
    @DisplayName("Should calculate correct total from multiple orders")
    void testBillTotalMatchesOrderSum() {
        String query = "SELECT b.id, b.total_amount, " +
                      "SUM(o.total) as orders_sum, " +
                      "COUNT(ba.order_id) as order_count " +
                      "FROM bill_details b " +
                      "JOIN bill_orders_association ba ON b.id = ba.bill_id " +
                      "JOIN order_details o ON ba.order_id = o.id " +
                      "WHERE b.id = 13 " +
                      "GROUP BY b.id, b.total_amount";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(query);

        assertFalse(results.isEmpty(), "Bill 13 should exist with orders");
        Map<String, Object> bill = results.get(0);

        double billTotal = ((Number) bill.get("total_amount")).doubleValue();
        double ordersSum = ((Number) bill.get("orders_sum")).doubleValue();

        // Bill total should equal sum of order totals (allowing for rounding)
        assertEquals(ordersSum, billTotal, 0.01,
                    "Bill total should match sum of associated orders");
    }

    @Test
    @DisplayName("Should verify all test bills have correct structure")
    void testBillsDataIntegrity() {
        // Verify bills table structure
        String query = "SELECT COUNT(*) as bill_count FROM bill_details";
        Integer billCount = jdbcTemplate.queryForObject(query, Integer.class);
        assertTrue(billCount >= 10, "Should have at least 10 bills");

        // Verify associations table structure
        query = "SELECT COUNT(*) as assoc_count FROM bill_orders_association";
        Integer assocCount = jdbcTemplate.queryForObject(query, Integer.class);
        assertTrue(assocCount >= 12, "Should have at least 12 bill-order associations");

        // Verify no orphaned associations
        query = "SELECT COUNT(*) FROM bill_orders_association ba " +
               "LEFT JOIN bill_details b ON ba.bill_id = b.id " +
               "WHERE b.id IS NULL";
        Integer orphanedBills = jdbcTemplate.queryForObject(query, Integer.class);
        assertEquals(0, orphanedBills, "No orphaned bill associations");

        query = "SELECT COUNT(*) FROM bill_orders_association ba " +
               "LEFT JOIN order_details o ON ba.order_id = o.id " +
               "WHERE o.id IS NULL";
        Integer orphanedOrders = jdbcTemplate.queryForObject(query, Integer.class);
        assertEquals(0, orphanedOrders, "No orphaned order associations");
    }
}
