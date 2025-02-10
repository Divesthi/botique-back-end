package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.CustomerMeasurementModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Entity
@Table(name = "customer_measurement_details")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerMeasurementDetails implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "dress_type")
    private String dressType;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "name")
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "measurement")
    private Map<String, Object> measurement;

    @Column(name = "creation_date")
    private OffsetDateTime creationDate;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL)
    @JoinColumn(name = "mobile_no", referencedColumnName = "mobile_no")
    private CustomerDetails customerDetails;

    public static CustomerMeasurementDetails toEntity(CustomerMeasurementModel customerMeasurementModel,
                                                      CustomerDetails customerDetails) {
        return CustomerMeasurementDetails.builder()
                .measurement(customerMeasurementModel.getMeasurement())
                .dressType(customerMeasurementModel.getDressType())
                .name(customerMeasurementModel.getName())
                .remarks(customerMeasurementModel.getRemarks())
                .customerDetails(customerDetails)
                .build();
    }

    public CustomerMeasurementModel toModel() {
        return CustomerMeasurementModel.builder()
                .name(name)
                .measurement(measurement)
                .remarks(remarks)
                .dressType(dressType)
                .mobileNo(customerDetails.getMobileNo())
                .creationDate(creationDate)
                .id(id)
                .build();
    }
}
