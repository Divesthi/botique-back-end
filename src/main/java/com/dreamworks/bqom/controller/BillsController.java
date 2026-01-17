package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.bill.BillModel;
import com.dreamworks.bqom.model.order.OrderModel;
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
@RequestMapping(path = "/v1/bqom/bills", produces = "application/json")
@CrossOrigin(origins="*")
public class BillsController {

    @Autowired
    private BillsService billsService;

    @GetMapping("")
    @ResponseBody
    public ResponseEntity<List<BillModel>> getBills(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        boolean hasDateFilter = fromDate != null && toDate != null;
        boolean hasSearch = search != null && !search.isEmpty();

        if (hasDateFilter) {
            OffsetDateTime fromDateTime = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime toDateTime = toDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

            if (hasSearch) {
                return new ResponseEntity<>(billsService.searchBillsWithDateRange(search, fromDateTime, toDateTime), HttpStatus.OK);
            }
            return new ResponseEntity<>(billsService.getBillsByDateRange(fromDateTime, toDateTime), HttpStatus.OK);
        }

        if (hasSearch) {
            return new ResponseEntity<>(billsService.searchBills(search), HttpStatus.OK);
        }
        return new ResponseEntity<>(billsService.getBills(), HttpStatus.OK);
    }

    @PostMapping("")
    @ResponseBody
    public ResponseEntity createBill(@RequestBody BillModel billModel) {
        billsService.createBill(billModel);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping("")
    @ResponseBody
    public ResponseEntity<BillModel> updateBill(@RequestBody BillModel billModel) {
        return new ResponseEntity<>(billsService.updateBill(billModel), HttpStatus.OK);
    }

}
