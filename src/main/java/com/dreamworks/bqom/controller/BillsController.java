package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.bill.BillModel;
import com.dreamworks.bqom.service.BillsService;
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
@RequestMapping(path = "/v1/bqom/tenants/{tenantCode}/bills", produces = "application/json")
@CrossOrigin(origins="*")
public class BillsController {

    @Autowired
    private BillsService billsService;

    @GetMapping("")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<List<BillModel>> getBills(
            @PathVariable String tenantCode,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        boolean hasDateFilter = fromDate != null && toDate != null;
        boolean hasSearch = search != null && !search.isEmpty();

        if (hasDateFilter) {
            OffsetDateTime fromDateTime = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime toDateTime = toDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

            if (hasSearch) {
                return new ResponseEntity<>(billsService.searchBillsWithDateRange(search, fromDateTime, toDateTime, tenantCode), HttpStatus.OK);
            }
            return new ResponseEntity<>(billsService.getBillsByDateRange(fromDateTime, toDateTime, tenantCode), HttpStatus.OK);
        }

        if (hasSearch) {
            return new ResponseEntity<>(billsService.searchBills(search, tenantCode), HttpStatus.OK);
        }
        return new ResponseEntity<>(billsService.getBills(tenantCode), HttpStatus.OK);
    }

    @PostMapping("")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity createBill(
            @PathVariable String tenantCode,
            @RequestBody BillModel billModel) {
        billsService.createBill(billModel, tenantCode);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping("")
    @ResponseBody
    @CrossOrigin
    public ResponseEntity<BillModel> updateBill(
            @PathVariable String tenantCode,
            @RequestBody BillModel billModel) {
        return new ResponseEntity<>(billsService.updateBill(billModel, tenantCode), HttpStatus.OK);
    }
}
