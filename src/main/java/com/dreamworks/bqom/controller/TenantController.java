package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.TenantModel;
import com.dreamworks.bqom.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/v1/bqom/tenants", produces = "application/json")
@CrossOrigin(origins = "*")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @GetMapping("")
    public ResponseEntity<List<TenantModel>> getTenants() {
        return new ResponseEntity<>(tenantService.getTenants(), HttpStatus.OK);
    }

    @GetMapping("/{code}")
    public ResponseEntity<TenantModel> getTenant(@PathVariable String code) {
        return new ResponseEntity<>(tenantService.getTenant(code), HttpStatus.OK);
    }

    @PostMapping("")
    public ResponseEntity<TenantModel> createTenant(@RequestBody TenantModel model) {
        return new ResponseEntity<>(tenantService.createTenant(model), HttpStatus.CREATED);
    }

    @PutMapping("")
    public ResponseEntity<TenantModel> updateTenant(@RequestBody TenantModel model) {
        return new ResponseEntity<>(tenantService.updateTenant(model), HttpStatus.OK);
    }
}
