package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.TenantModel;
import com.dreamworks.bqom.repository.TenantRepository;
import com.dreamworks.bqom.repository.entity.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class TenantService {

    @Autowired
    private TenantRepository tenantRepository;

    public List<TenantModel> getTenants() {
        return tenantRepository.findAll().stream().map(this::toModel).toList();
    }

    public TenantModel getTenant(String code) {
        return tenantRepository.findByCode(code)
                .map(this::toModel)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + code));
    }

    public TenantModel createTenant(TenantModel model) {
        tenantRepository.findByCode(model.getCode()).ifPresent(t -> {
            throw new RuntimeException("Tenant with code " + model.getCode() + " already exists");
        });
        Tenant tenant = toEntity(model);
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);
        log.info("Tenant created: {}", tenant.getCode());
        return toModel(tenant);
    }

    public TenantModel updateTenant(TenantModel model) {
        Tenant tenant = tenantRepository.findByCode(model.getCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + model.getCode()));
        if (model.getName() != null) tenant.setName(model.getName());
        if (model.getAddress() != null) tenant.setAddress(model.getAddress());
        if (model.getPhoneNumber() != null) tenant.setPhoneNumber(model.getPhoneNumber());
        if (model.getStartedDate() != null) tenant.setStartedDate(model.getStartedDate());
        if (model.getChurnedDate() != null) tenant.setChurnedDate(model.getChurnedDate());
        if (model.getActive() != null) tenant.setActive(model.getActive());
        tenant = tenantRepository.save(tenant);
        log.info("Tenant updated: {}", tenant.getCode());
        return toModel(tenant);
    }

    private TenantModel toModel(Tenant t) {
        return TenantModel.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .address(t.getAddress())
                .phoneNumber(t.getPhoneNumber())
                .startedDate(t.getStartedDate())
                .churnedDate(t.getChurnedDate())
                .active(t.getActive())
                .build();
    }

    private Tenant toEntity(TenantModel model) {
        return Tenant.builder()
                .name(model.getName())
                .code(model.getCode())
                .address(model.getAddress())
                .phoneNumber(model.getPhoneNumber())
                .startedDate(model.getStartedDate())
                .churnedDate(model.getChurnedDate())
                .active(model.getActive())
                .build();
    }
}
