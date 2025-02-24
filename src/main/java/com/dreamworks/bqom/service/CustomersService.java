package com.dreamworks.bqom.service;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import com.dreamworks.bqom.model.customer.CustomerMeasurementModel;
import com.dreamworks.bqom.model.customer.MeasurementRequestBody;
import com.dreamworks.bqom.repository.CustomerMeasurementRepository;
import com.dreamworks.bqom.repository.CustomersRepository;
import com.dreamworks.bqom.repository.entity.CustomerDetails;
import com.dreamworks.bqom.repository.entity.CustomerMeasurementDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CustomersService {

    @Autowired
    private CustomersRepository customersRepository;
    @Autowired
    private CustomerMeasurementRepository customerMeasurementRepository;

    public List<CustomerDetailsModel> getCustomers() {
        List<CustomerDetails> customers = customersRepository.findAll();
        return customers.stream().map((customer) -> CustomerDetailsModel.builder()
                .address(customer.getAddress())
                .id(customer.getId())
                .name(customer.getName())
                .alternateContactNo(customer.getAlternateContactNo())
                .mobileNo(customer.getMobileNo())
                .tenantId(customer.getTenantId())
                .creationDate(customer.getCreationDate())
                .build()).toList();
    }

    public CustomerDetailsModel createCustomer(CustomerDetailsModel customerDetailsModel) {
        try {
            CustomerDetails customerDetails = CustomerDetails.toEntity(customerDetailsModel);
            customerDetails.setCreationDate(OffsetDateTime.now());
            CustomerDetails customerDetail = customersRepository.save(customerDetails);
            customerDetailsModel.setId(customerDetail.getId());
            customerDetailsModel.setCreationDate(customerDetail.getCreationDate());
        } catch (Exception e) {
            log.error("Error while persisting the customer information - {}", customerDetailsModel.getMobileNo(),
                    e);
            throw e;
        }
        return customerDetailsModel;
    }

    public List<CustomerMeasurementModel> getCustomerMeasurements(MeasurementRequestBody measurementRequestBody) {
        List<CustomerMeasurementModel> customerMeasurementModels = null;
        try {
            List<CustomerMeasurementDetails> customerMeasurements =
                    customerMeasurementRepository.getMeasurementByMobileNo(measurementRequestBody.getMobileNo());
            customerMeasurementModels =
                    customerMeasurements.stream()
                            .map((customerMeasurement) -> CustomerMeasurementModel.builder()
                                    .measurement(customerMeasurement.getMeasurement())
                                    .name(customerMeasurement.getName())
                                    .dressType(customerMeasurement.getDressType())
                                    .remarks(customerMeasurement.getRemarks())
                                    .mobileNo(customerMeasurement.getCustomerDetails().getMobileNo())
                                    .creationDate(customerMeasurement.getCreationDate())
                                    .id(customerMeasurement.getId())
                                    .build()).toList();
        } catch (Exception e) {
            log.error("Error while obtaining the measurement for the customer - {}", measurementRequestBody.getMobileNo(), e);
            throw e;
        }
        return customerMeasurementModels;
    }

    public CustomerMeasurementModel createCustomerMeasurement(CustomerMeasurementModel measurementModel) {
        try {
            List<CustomerMeasurementDetails> measurementDetails = customerMeasurementRepository.getMeasurementByMobileNameDress(measurementModel.getMobileNo(),
                    measurementModel.getName(), measurementModel.getDressType());
            if (!measurementDetails.isEmpty()) {
                String mesg = String.format("Measurement for the dress type - %s already exists for the Customer - %s",
                measurementModel.getDressType(), measurementModel.getName());
                log.error(mesg);
                throw new RuntimeException(mesg);
            }
            CustomerDetails customerDetails = customersRepository.getCustomerDetailsByMobileNumber(measurementModel.getMobileNo());
            CustomerMeasurementDetails customerMeasurementDetails = CustomerMeasurementDetails.toEntity(measurementModel, customerDetails);
            customerMeasurementDetails.setCreationDate(OffsetDateTime.now());
            customerMeasurementDetails = customerMeasurementRepository.save(customerMeasurementDetails);
            measurementModel = customerMeasurementDetails.toModel();
        } catch (Exception e) {
            log.error("Error while creating the measurement for the customer - {}", measurementModel.getMobileNo(), e);
            throw e;
        }
        return measurementModel;
    }

    public CustomerMeasurementModel updateCustomerMeasurement(CustomerMeasurementModel measurementModel) {
        try {
            Optional<CustomerMeasurementDetails> customerMeasurementDetails = customerMeasurementRepository.findById(measurementModel.getId());
            if (customerMeasurementDetails.isPresent()) {
                CustomerMeasurementDetails details = customerMeasurementDetails.get();
                details.setName(measurementModel.getName());
                details.setMeasurement(measurementModel.getMeasurement());
                details.setRemarks(measurementModel.getRemarks());
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
}
