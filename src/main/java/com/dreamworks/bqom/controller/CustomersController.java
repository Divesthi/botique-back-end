package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.CustomerDetailsModel;
import com.dreamworks.bqom.model.CustomerMeasurementModel;
import com.dreamworks.bqom.model.MeasurementRequestBody;
import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.service.CustomersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom/customers", produces = "application/json")
@CrossOrigin(origins="*")
public class CustomersController {

    @Autowired
    private CustomersService customersService;

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<CustomerDetailsModel>> getCustomers() {
        return new ResponseEntity<>(customersService.getCustomers(), HttpStatus.OK);
    }

    @PostMapping("")
    @ResponseBody
    public ResponseEntity<CustomerDetailsModel> createCustomer(@RequestBody CustomerDetailsModel customerDetailsModel) {
        try {
            customerDetailsModel = customersService.createCustomer(customerDetailsModel);
            return new ResponseEntity<>(customerDetailsModel, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/measurements")
    @ResponseBody
    public ResponseEntity<List<CustomerMeasurementModel>> getCustomerMeasurements(@RequestBody MeasurementRequestBody measurementRequestBody) {
        try {
            return new ResponseEntity<>(customersService.getCustomerMeasurements(measurementRequestBody), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/measurements")
    @ResponseBody
    public ResponseEntity<CustomerMeasurementModel> CreateCustomerMeasurement(@RequestBody CustomerMeasurementModel customerMeasurementModel) {
        try {
            return new ResponseEntity<>(customersService.createCustomerMeasurement(customerMeasurementModel), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/measurements")
    @ResponseBody
    public ResponseEntity<CustomerMeasurementModel> updateCustomerMeasurement(@RequestBody CustomerMeasurementModel customerMeasurementModel) {
        try {
            return new ResponseEntity<>(customersService.updateCustomerMeasurement(customerMeasurementModel), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }
}
