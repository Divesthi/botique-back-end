package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom/orders", produces = "application/json")
@CrossOrigin(origins="*")
public class OrdersController {
    @Autowired
    private OrdersService ordersService;

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<OrderModel>> getOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        boolean hasDateFilter = fromDate != null && toDate != null;
        boolean hasSearch = search != null && !search.isEmpty();

        if (hasDateFilter) {
            OffsetDateTime fromDateTime = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime toDateTime = toDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

            if (hasSearch) {
                return new ResponseEntity<>(ordersService.searchOrdersWithDateRange(search, fromDateTime, toDateTime), HttpStatus.OK);
            }
            return new ResponseEntity<>(ordersService.getOrdersByDateRange(fromDateTime, toDateTime), HttpStatus.OK);
        }

        if (hasSearch) {
            return new ResponseEntity<>(ordersService.searchOrders(search), HttpStatus.OK);
        }
        return new ResponseEntity<>(ordersService.getOrders(), HttpStatus.OK);
    }

    @PostMapping("")
    @ResponseBody
    public ResponseEntity createOrder(@RequestBody OrderModel orderModel){
        ordersService.createOrder(orderModel);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping("")
    @ResponseBody
    public ResponseEntity<OrderModel> updateOrder(@RequestBody OrderModel orderModel){
        try {
            OrderModel updatedOrder = ordersService.updateOrder(orderModel);
            return new ResponseEntity<>(updatedOrder, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
