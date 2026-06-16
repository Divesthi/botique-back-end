package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import com.dreamworks.bqom.model.customer.CustomerMeasurementModel;
import com.dreamworks.bqom.model.customer.MeasurementRequestBody;
import com.dreamworks.bqom.model.whatsapp.MeasurementShareRequest;
import com.dreamworks.bqom.repository.CustomerMeasurementRepository;
import com.dreamworks.bqom.repository.CustomersRepository;
import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.repository.entity.CustomerMeasurementDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
public class CustomersService {

    @Autowired
    private CustomersRepository customersRepository;
    @Autowired
    private CustomerMeasurementRepository customerMeasurementRepository;
    @Autowired
    private WhatsAppNotificationService whatsAppNotificationService;

    public List<CustomerDetailsModel> getCustomers(String tenantCode) {
        List<CustomerDetails> customers = customersRepository.findAllByTenantCode(tenantCode);
        return customers.stream().map((customer) -> CustomerDetailsModel.builder()
                .address(customer.getAddress())
                .id(customer.getId())
                .name(customer.getName())
                .alternateContactNo(customer.getAlternateContactNo())
                .mobileNo(customer.getMobileNo())
                .tenantCode(customer.getTenantCode())
                .creationDate(customer.getCreationDate())
                .updatedDate(customer.getUpdatedDate())
                .build()).toList();
    }

    public List<CustomerDetailsModel> searchCustomers(String searchTerm, String tenantCode) {
        if (StringUtils.isBlank(searchTerm)) {
            return getCustomers(tenantCode);
        }
        List<CustomerDetails> customers = customersRepository.searchCustomers(searchTerm, tenantCode);
        return customers.stream().map((customer) -> CustomerDetailsModel.builder()
                .address(customer.getAddress())
                .id(customer.getId())
                .name(customer.getName())
                .alternateContactNo(customer.getAlternateContactNo())
                .mobileNo(customer.getMobileNo())
                .tenantCode(customer.getTenantCode())
                .creationDate(customer.getCreationDate())
                .updatedDate(customer.getUpdatedDate())
                .build()).toList();
    }

    public List<CustomerDetailsModel> getCustomer(String contactNumber, String tenantCode) {
        CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(contactNumber, tenantCode);
        if (customerDetails != null) {
            List<CustomerDetailsModel> customers = new ArrayList<>(1);
            customers.add(customerDetails.toModel());
            return customers;
        } else {
            String message = String.format("Customer with the contact number - %s doesn't exists in our system",
                    contactNumber);
            log.error(message);
            return Collections.emptyList();
        }
    }

    public CustomerDetailsModel createCustomer(CustomerDetailsModel customerDetailsModel, String tenantCode) {
        try {
            customerDetailsModel.setTenantCode(tenantCode);
            CustomerDetails customerDetails = CustomerDetails.toEntity(customerDetailsModel);
            customerDetails.setCreationDate(OffsetDateTime.now());
            customerDetails.setUpdatedDate(OffsetDateTime.now());
            CustomerDetails customerDetail = customersRepository.save(customerDetails);
            customerDetailsModel.setId(customerDetail.getId());
            customerDetailsModel.setCreationDate(customerDetail.getCreationDate());
        } catch (Exception e) {
            log.error("Error while persisting the customer information - {}", customerDetailsModel.getMobileNo(), e);
            throw e;
        }
        return customerDetailsModel;
    }

    public CustomerDetailsModel updateCustomer(CustomerDetailsModel customerDetailsModel, String tenantCode) {
        try {
            Optional<CustomerDetails> customerDetailOpt = customersRepository.findById(customerDetailsModel.getId());
            if (customerDetailOpt.isPresent()) {
                CustomerDetails customerDetails = customerDetailOpt.get();
                customerDetails.setName(customerDetailsModel.getName());
                customerDetails.setAddress(customerDetailsModel.getAddress());
                customerDetails.setAlternateContactNo(customerDetailsModel.getAlternateContactNo());
                customerDetails.setUpdatedDate(OffsetDateTime.now());
                customersRepository.save(customerDetails);
            } else {
                log.error("Customer with the given contact number - {} doesn't exists", customerDetailsModel.getMobileNo());
            }
        } catch (Exception e) {
            log.error("Error while persisting the customer information - {}", customerDetailsModel.getMobileNo(), e);
            throw e;
        }
        return customerDetailsModel;
    }

    public List<CustomerMeasurementModel> getCustomerMeasurements(String mobileNo, String tenantCode) {
        List<CustomerMeasurementModel> customerMeasurementModels = null;
        try {
            List<CustomerMeasurementDetails> customerMeasurements =
                        customerMeasurementRepository.getMeasurementByMobileNo(mobileNo, tenantCode);
            customerMeasurementModels =
                        customerMeasurements.stream()
                                .map((customerMeasurement) -> CustomerMeasurementModel.builder()
                                        .measurement(customerMeasurement.getMeasurement())
                                        .name(customerMeasurement.getName())
                                        .dressType(customerMeasurement.getDressType())
                                        .remarks(customerMeasurement.getRemarks())
                                        .mobileNo(customerMeasurement.getCustomerDetails().getMobileNo())
                                        .tenantCode(customerMeasurement.getCustomerDetails().getTenantCode())
                                        .creationDate(customerMeasurement.getCreationDate())
                                        .updatedDate(customerMeasurement.getUpdatedDate())
                                        .id(customerMeasurement.getId())
                                        .build()).toList();
        } catch (Exception e) {
            log.error("Error while obtaining the measurement for the customer - {}", mobileNo, e);
            throw e;
        }
        return customerMeasurementModels;
    }

    public List<CustomerMeasurementModel> getMeasurements(String tenantCode) {
        List<CustomerMeasurementModel> customerMeasurementModels = null;
        try {
            List<CustomerMeasurementDetails> customerMeasurements =
                    customerMeasurementRepository.findAllByTenantCode(tenantCode);
            customerMeasurementModels =
                    customerMeasurements.stream()
                            .map((customerMeasurement) -> CustomerMeasurementModel.builder()
                                    .measurement(customerMeasurement.getMeasurement())
                                    .name(customerMeasurement.getName())
                                    .dressType(customerMeasurement.getDressType())
                                    .remarks(customerMeasurement.getRemarks())
                                    .mobileNo(customerMeasurement.getCustomerDetails().getMobileNo())
                                    .tenantCode(customerMeasurement.getCustomerDetails().getTenantCode())
                                    .creationDate(customerMeasurement.getCreationDate())
                                    .id(customerMeasurement.getId())
                                    .build()).toList();
        } catch (Exception e) {
            log.error("Error while obtaining the measurements", e);
            throw e;
        }
        return customerMeasurementModels;
    }

    public List<CustomerMeasurementModel> searchMeasurements(String searchTerm, String tenantCode) {
        if (StringUtils.isBlank(searchTerm)) {
            return getMeasurements(tenantCode);
        }
        List<CustomerMeasurementModel> customerMeasurementModels = null;
        try {
            List<CustomerMeasurementDetails> customerMeasurements =
                    customerMeasurementRepository.searchMeasurements(searchTerm, tenantCode);
            customerMeasurementModels =
                    customerMeasurements.stream()
                            .map((customerMeasurement) -> CustomerMeasurementModel.builder()
                                    .measurement(customerMeasurement.getMeasurement())
                                    .name(customerMeasurement.getName())
                                    .dressType(customerMeasurement.getDressType())
                                    .remarks(customerMeasurement.getRemarks())
                                    .mobileNo(customerMeasurement.getCustomerDetails().getMobileNo())
                                    .tenantCode(customerMeasurement.getCustomerDetails().getTenantCode())
                                    .creationDate(customerMeasurement.getCreationDate())
                                    .id(customerMeasurement.getId())
                                    .build()).toList();
        } catch (Exception e) {
            log.error("Error while searching measurements", e);
            throw e;
        }
        return customerMeasurementModels;
    }

    public CustomerMeasurementModel createCustomerMeasurement(CustomerMeasurementModel measurementModel, String tenantCode) {
        try {
            List<CustomerMeasurementDetails> measurementDetails = customerMeasurementRepository
                    .getMeasurementByMobileNameDress(measurementModel.getMobileNo(),
                            measurementModel.getName(), measurementModel.getDressType(), tenantCode);
            if (!measurementDetails.isEmpty()) {
                String mesg = String.format("Measurement for the dress type - %s already exists for the Customer - %s",
                measurementModel.getDressType(), measurementModel.getName());
                log.error(mesg);
                throw new RuntimeException(mesg);
            }
            CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(
                    measurementModel.getMobileNo(), tenantCode);
            CustomerMeasurementDetails customerMeasurementDetails = CustomerMeasurementDetails.toEntity(measurementModel, customerDetails);
            customerMeasurementDetails.setCreationDate(OffsetDateTime.now());
            customerMeasurementDetails.setUpdatedDate(OffsetDateTime.now());
            customerMeasurementDetails = customerMeasurementRepository.save(customerMeasurementDetails);
            measurementModel = customerMeasurementDetails.toModel();
        } catch (Exception e) {
            log.error("Error while creating the measurement for the customer - {}", measurementModel.getMobileNo(), e);
            throw e;
        }
        return measurementModel;
    }

    public void deleteCustomer(Long customerId, String tenantCode) {
        Optional<CustomerDetails> customerOpt = customersRepository.findById(customerId);
        if (customerOpt.isEmpty() || !customerOpt.get().getTenantCode().equals(tenantCode)) {
            throw new RuntimeException("Customer not found");
        }
        try {
            customersRepository.deleteById(customerId);
        } catch (Exception e) {
            log.error("Cannot delete customer {} - likely has linked orders or measurements", customerId, e);
            throw new RuntimeException("Cannot delete customer with existing orders, measurements, or bills");
        }
    }

    public void deleteMeasurement(Long measurementId, String tenantCode) {
        Optional<CustomerMeasurementDetails> measurementOpt = customerMeasurementRepository.findById(measurementId);
        if (measurementOpt.isEmpty() || !measurementOpt.get().getCustomerDetails().getTenantCode().equals(tenantCode)) {
            throw new RuntimeException("Measurement not found");
        }
        customerMeasurementRepository.deleteById(measurementId);
    }

    public CustomerMeasurementModel updateCustomerMeasurement(CustomerMeasurementModel measurementModel, String tenantCode) {
        try {
            Optional<CustomerMeasurementDetails> customerMeasurementDetails = customerMeasurementRepository.findById(measurementModel.getId());
            if (customerMeasurementDetails.isPresent()) {
                CustomerMeasurementDetails details = customerMeasurementDetails.get();
                details.setName(measurementModel.getName());
                details.setMeasurement(measurementModel.getMeasurement());
                details.setRemarks(measurementModel.getRemarks());
                details.setUpdatedDate(OffsetDateTime.now());
                // NOTE: Dress Type is not allowed for changing
                //details.setDressType(measurementModel.getDressType());
                details = customerMeasurementRepository.save(details);
                return details.toModel();
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("Error while updating the measurement for the customer - {}", measurementModel.getMobileNo(), e);
            throw e;
        }
    }

    public CustomerMeasurementModel shareMeasurement(Long measurementId, String tenantCode,
                                                     MeasurementShareRequest measurementShareRequest) {
        Optional<CustomerMeasurementDetails> measurementOpt = customerMeasurementRepository.findById(measurementId);
        if (measurementOpt.isEmpty() || !measurementOpt.get().getCustomerDetails().getTenantCode().equals(tenantCode)) {
            throw new RuntimeException("Measurement not found");
        }
        CustomerMeasurementModel measurement = measurementOpt.get().toModel();
        whatsAppNotificationService.sendMeasurement(tenantCode, measurement.getName(),
                measurement.getDressType(), measurement.getMeasurement(), measurementShareRequest.getToPhoneNumber());
        return measurement;
    }
}
