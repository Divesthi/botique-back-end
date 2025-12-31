package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import com.dreamworks.bqom.model.customer.CustomerMeasurementModel;
import com.dreamworks.bqom.model.customer.MeasurementRequestBody;
import com.dreamworks.bqom.service.CustomersService;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom/customers", produces = "application/json")
@CrossOrigin(origins="*")
public class CustomersController {

    @Autowired
    private CustomersService customersService;

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<CustomerDetailsModel>> getCustomers(@RequestParam(required = false) String search) {
        if (search != null && !search.isEmpty()) {
            return new ResponseEntity<>(customersService.searchCustomers(search), HttpStatus.OK);
        }
        return new ResponseEntity<>(customersService.getCustomers(), HttpStatus.OK);
    }

    @GetMapping("/{contactNo}")
    @ResponseBody
    public ResponseEntity<List<CustomerDetailsModel>> getCustomer(@PathVariable String contactNo) {
        return new ResponseEntity<>(customersService.getCustomer(contactNo), HttpStatus.OK);
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

    @PutMapping("")
    @ResponseBody
    public ResponseEntity<CustomerDetailsModel> updateCustomer(@RequestBody CustomerDetailsModel customerDetailsModel) {
        try {
            customerDetailsModel = customersService.updateCustomer(customerDetailsModel);
            return new ResponseEntity<>(customerDetailsModel, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @GetMapping("/measurements")
    @ResponseBody
    public ResponseEntity<List<CustomerMeasurementModel>> getMeasurements(@RequestParam(required = false) String search) {
        try {
            if (search != null && !search.isEmpty()) {
                return new ResponseEntity<>(customersService.searchMeasurements(search), HttpStatus.OK);
            }
            return new ResponseEntity<>(customersService.getMeasurements(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/measurements/{contactNo}")
    @ResponseBody
    public ResponseEntity<List<CustomerMeasurementModel>> getCustomerMeasurements(@NonNull @PathVariable String contactNo) {
        try {
            return new ResponseEntity<>(customersService.getCustomerMeasurements(contactNo), HttpStatus.OK);
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
