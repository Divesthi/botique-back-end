package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.repository.entity.CustomerDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom/orders", produces = "application/json")
@CrossOrigin(origins="*")
public class OrdersController {

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<CustomerDetails>> getOrders() {
        return new ResponseEntity<>(null, HttpStatus.OK);
    }
}
