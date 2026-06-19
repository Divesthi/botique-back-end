package com.dreamworks.bqom.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity for the {@code tenant} table.
 *
 * <h2>preferences field design</h2>
 * <p>{@code preferences} is a PostgreSQL {@code JSONB} column mapped as
 * {@code Map<String, Object>} using Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)}.
 * This is identical to the pattern already used in {@link CustomerMeasurementDetails}
 * for the {@code measurement} column.
 *
 * <p>Why {@code Map<String, Object>} over a typed POJO?
 * <ul>
 *   <li>Preferences are additive — future keys must not break existing code.</li>
 *   <li>Deep-merge at the service layer works directly on the map without
 *       needing to deserialise into a versioned schema.</li>
 *   <li>Hibernate handles PostgreSQL JSONB serialisation transparently.</li>
 * </ul>
 *
 * <p>The column default {@code '{}'} (set in migration 08) ensures existing
 * rows always have a non-null map after the ALTER TABLE runs.
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "address")
    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "started_date")
    private LocalDate startedDate;

    @Column(name = "churned_date")
    private LocalDate churnedDate;

    @Column(name = "active")
    private Boolean active;

    /**
     * Flexible JSONB preferences store.
     *
     * <p>Structure (current):
     * <pre>
     * {
     *   "notifications": {
     *     "channel": "telegram"   // or "whatsapp"
     *   }
     * }
     * </pre>
     *
     * <p>Initialised to an empty map ({@code HashMap}) so callers never
     * receive {@code null} — they receive an empty map that can be merged into.
     * The DB default ({@code '{}'}) guarantees the same for legacy rows.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> preferences = new HashMap<>();
}
