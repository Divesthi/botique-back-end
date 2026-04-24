package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.model.customer.CustomerMeasurementModel;
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

    @Column(name = "updated_date")
    private OffsetDateTime updatedDate;

    @Column(name = "tenant_code", insertable = false, updatable = false)
    private String tenantCode;

    @OneToOne(
            fetch = FetchType.EAGER,
            cascade = CascadeType.REMOVE) // Added cascade type remove
    
    ...
}
