package com.dreamworks.bqom.controller;

import com.dreamworks.bqom.model.TenantModel;
import com.dreamworks.bqom.repository.enums.UserRole;
import com.dreamworks.bqom.security.RequireRole;
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
    @CrossOrigin
    public ResponseEntity<List<TenantModel>> getTenants() {
        return new ResponseEntity<>(tenantService.getTenants(), HttpStatus.OK);
    }

    @GetMapping("/{code}")
    @CrossOrigin
    public ResponseEntity<TenantModel> getTenant(@PathVariable("code") String code) {
        return new ResponseEntity<>(tenantService.getTenant(code), HttpStatus.OK);
    }

    @PostMapping("")
    @CrossOrigin
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantModel> createTenant(@RequestBody TenantModel model) {
        return new ResponseEntity<>(tenantService.createTenant(model), HttpStatus.CREATED);
    }

    @PutMapping("")
    @CrossOrigin
    @RequireRole(UserRole.TENANT_ADMIN)
    public ResponseEntity<TenantModel> updateTenant(@RequestBody TenantModel model) {
        return new ResponseEntity<>(tenantService.updateTenant(model), HttpStatus.OK);
    }
}
