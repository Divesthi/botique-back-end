package com.dreamworks.bqom.repository.entity;

import com.dreamworks.bqom.service.instagram.InstagramOAuthService;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for {@code tenant_instagram_config}.
 *
 * <h2>Field classification</h2>
 * <table border="1">
 *   <tr><th>Field</th><th>Sensitivity</th><th>Storage</th></tr>
 *   <tr><td>igUserId</td><td>Resource ID — not a secret</td><td>Plaintext</td></tr>
 *   <tr><td>igUsername</td><td>Public @handle — not a secret</td><td>Plaintext</td></tr>
 *   <tr><td>accessToken</td><td>Bearer token — secret</td><td>AES-256-GCM encrypted</td></tr>
 *   <tr><td>tokenExpiry</td><td>Metadata — not a secret</td><td>Plaintext</td></tr>
 * </table>
 *
 * <h2>Encryption boundary</h2>
 * <p>Encryption and decryption happen exclusively in
 * {@link InstagramOAuthService} — the entity
 * is intentionally unaware of encryption, consistent with
 * {@link TenantWhatsAppConfig} and {@link TenantTelegramConfig}.
 *
 * <h2>Token lifecycle</h2>
 * <p>Meta long-lived tokens expire after 60 days. The
 * {@link com.dreamworks.bqom.scheduler.InstagramTokenRefreshScheduler}
 * runs weekly and refreshes any token where
 * {@code token_expiry < NOW() + threshold_days}.
 *
 * <h2>Relationship</h2>
 * <p>One-to-one with {@code tenant} via {@code tenant_code} FK.
 * Not modelled as a {@code @ManyToOne} association to avoid lazy-loading
 * surprises in async notification paths — consistent with other config tables.
 */
@Entity
@Table(
        name = "tenant_instagram_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ig_cfg_tenant",
                columnNames = "tenant_code"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInstagramConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * FK to {@code tenant(code)}.
     * Stored as a plain VARCHAR — not a {@code @ManyToOne} — to keep
     * async scheduler reads free of Hibernate session requirements.
     */
    @Column(name = "tenant_code", nullable = false, updatable = false)
    private String tenantCode;

    /**
     * Instagram Business Account ID (numeric string).
     * Used to make Graph API calls on behalf of this account.
     * Plaintext — a resource identifier, not a credential.
     */
    @Column(name = "ig_user_id", nullable = false, length = 100)
    private String igUserId;

    /**
     * Instagram @handle for display purposes only.
     * Shown in the frontend settings page so the admin can confirm
     * which account is connected.
     */
    @Column(name = "ig_username", length = 100)
    private String igUsername;

    /**
     * AES-256-GCM encrypted Meta long-lived access token.
     * Valid for 60 days from issuance. Refreshed proactively by the
     * weekly scheduler before expiry.
     * Never log or return this field in plaintext.
     */
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken; // stored encrypted

    /**
     * UTC timestamp when the current access token expires.
     * Meta long-lived tokens are valid for 60 days.
     * The refresh scheduler uses this to trigger proactive renewal
     * when expiry is within the configured threshold (default 10 days).
     */
    @Column(name = "token_expiry", nullable = false)
    private OffsetDateTime tokenExpiry;

    /**
     * Soft-delete flag. When {@code false}, the account is disconnected
     * and the dispatcher will not use this config.
     * Set to {@code false} by the disconnect endpoint; never hard-deleted
     * so the record is retained for audit purposes.
     */
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