package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.order.OrderModel;
import com.dreamworks.bqom.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom/orders", produces = "application/json")
@CrossOrigin(origins="*")
public class OrdersController {
    @Autowired
    private OrdersService ordersService;

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<OrderModel>> getOrders(@RequestParam(required = false) String search) {
        if (search != null && !search.isEmpty()) {
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
