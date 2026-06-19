package com.dreamworks.bqom.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for {@code tenant_whatsapp_config}.
 *
 * <h2>Field classification</h2>
 * <table border="1">
 *   <tr><th>Field</th><th>Sensitivity</th><th>Storage</th></tr>
 *   <tr><td>phoneNumberId</td><td>Resource ID — not a secret</td><td>Plaintext</td></tr>
 *   <tr><td>wabaId</td><td>Account ID — not a secret</td><td>Plaintext</td></tr>
 *   <tr><td>businessPhoneNumber</td><td>PII — not a credential</td><td>Plaintext</td></tr>
 *   <tr><td>accessToken</td><td>Bearer token — secret</td><td>AES-256-GCM encrypted</td></tr>
 * </table>
 *
 * <h2>Encryption boundary</h2>
 * <p>Encryption and decryption happen exclusively in
 * {@link com.dreamworks.bqom.service.WhatsAppConfigService} — the entity is
 * intentionally unaware of encryption. This keeps the crypto boundary explicit,
 * independently testable, and consistent with {@link TenantTelegramConfig}.
 *
 * <h2>Relationship</h2>
 * <p>One-to-one with {@code tenant} via {@code tenant_code} FK.
 * Not modelled as a {@code @ManyToOne} association to avoid lazy-loading
 * surprises in async notification paths.
 */
@Entity
@Table(
        name = "tenant_whatsapp_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_whatsapp_cfg_tenant",
                columnNames = "tenant_code"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantWhatsAppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * FK to {@code tenant(code)}.
     * Stored as a plain VARCHAR — not a {@code @ManyToOne} — to keep
     * async notification reads free of Hibernate session requirements.
     */
    @Column(name = "tenant_code", nullable = false, updatable = false)
    private String tenantCode;

    /**
     * Meta WhatsApp Phone Number ID.
     * Identifies the specific phone number registered in the WABA.
     * Plaintext — a numeric resource identifier, not a credential.
     */
    @Column(name = "phone_number_id", nullable = false, length = 100)
    private String phoneNumberId;

    /**
     * WhatsApp Business Account (WABA) ID.
     * Plaintext — numeric account identifier, not a credential.
     */
    @Column(name = "waba_id", nullable = false, length = 100)
    private String wabaId;

    /**
     * E.164-formatted business phone number (e.g., {@code +15556580205}).
     * PII but not a credential — stored plaintext.
     * Encrypt if your data classification policy requires it.
     */
    @Column(name = "business_phone_number", nullable = false, length = 25)
    private String businessPhoneNumber;

    /**
     * AES-256-GCM encrypted Meta System User access token.
     * Never log or return this field in plaintext.
     * Decrypted at the service layer only when needed for an API call.
     */
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;  // stored encrypted

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}