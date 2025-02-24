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
    public ResponseEntity<List<OrderModel>> getOrders() {
        return new ResponseEntity<>(ordersService.getOrders(), HttpStatus.OK);
    }

    @PostMapping("")
    @ResponseBody
    public ResponseEntity createOrder(@RequestBody OrderModel orderModel){
        ordersService.createOrder(orderModel);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

}
