package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import com.dreamworks.bqom.model.customer.CustomerMeasurementModel;
import com.dreamworks.bqom.model.notification.MeasurementShareRequest;
import com.dreamworks.bqom.service.CustomersService;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/v1/bqom/tenants/{tenantCode}/customers", produces = "application/json")
@CrossOrigin(origins="*")
public class CustomersController {

    @Autowired
    private CustomersService customersService;

    @GetMapping("")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<List<CustomerDetailsModel>> getCustomers(
            @PathVariable("tenantCode") String tenantCode,
            @RequestParam(name = "search", required = false) String search) {
        if (search != null && !search.isEmpty()) {
            return new ResponseEntity<>(customersService.searchCustomers(search, tenantCode), HttpStatus.OK);
        }
        return new ResponseEntity<>(customersService.getCustomers(tenantCode), HttpStatus.OK);
    }

    @GetMapping("/{contactNo}")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<List<CustomerDetailsModel>> getCustomer(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable("contactNo") String contactNo) {
        return new ResponseEntity<>(customersService.getCustomer(contactNo, tenantCode), HttpStatus.OK);
    }

    @PostMapping("")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<CustomerDetailsModel> createCustomer(
            @PathVariable("tenantCode") String tenantCode,
            @RequestBody CustomerDetailsModel customerDetailsModel) {
        try {
            customerDetailsModel = customersService.createCustomer(customerDetailsModel, tenantCode);
            return new ResponseEntity<>(customerDetailsModel, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<CustomerDetailsModel> updateCustomer(
            @PathVariable("tenantCode") String tenantCode,
            @RequestBody CustomerDetailsModel customerDetailsModel) {
        try {
            customerDetailsModel = customersService.updateCustomer(customerDetailsModel, tenantCode);
            return new ResponseEntity<>(customerDetailsModel, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/measurements")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<List<CustomerMeasurementModel>> getMeasurements(
            @PathVariable("tenantCode") String tenantCode,
            @RequestParam(name = "search", required = false) String search) {
        try {
            if (search != null && !search.isEmpty()) {
                return new ResponseEntity<>(customersService.searchMeasurements(search, tenantCode), HttpStatus.OK);
            }
            return new ResponseEntity<>(customersService.getMeasurements(tenantCode), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/measurements/{contactNo}")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<List<CustomerMeasurementModel>> getCustomerMeasurements(
            @PathVariable("tenantCode") String tenantCode,
            @NonNull @PathVariable("contactNo") String contactNo) {
        try {
            return new ResponseEntity<>(customersService.getCustomerMeasurements(contactNo, tenantCode), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/measurements")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<CustomerMeasurementModel> CreateCustomerMeasurement(
            @PathVariable("tenantCode") String tenantCode,
            @RequestBody CustomerMeasurementModel customerMeasurementModel) {
        try {
            return new ResponseEntity<>(customersService.createCustomerMeasurement(customerMeasurementModel, tenantCode), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{customerId}")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<?> deleteCustomer(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable("customerId") Long customerId) {
        try {
            customersService.deleteCustomer(customerId, tenantCode);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/measurements/{measurementId}")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<?> deleteMeasurement(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable("measurementId") Long measurementId) {
        try {
            customersService.deleteMeasurement(measurementId, tenantCode);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/measurements")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<CustomerMeasurementModel> updateCustomerMeasurement(
            @PathVariable("tenantCode") String tenantCode,
            @RequestBody CustomerMeasurementModel customerMeasurementModel) {
        try {
            return new ResponseEntity<>(customersService.updateCustomerMeasurement(customerMeasurementModel, tenantCode), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/measurements/{measurementId}/share")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<CustomerMeasurementModel> shareMeasurement(
            @PathVariable("tenantCode") String tenantCode,
            @PathVariable("measurementId") Long measurementId,
            @RequestBody MeasurementShareRequest measurementShareRequest) {
        try {
            return new ResponseEntity<>(customersService.shareMeasurement(tenantCode, measurementId, measurementShareRequest), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
