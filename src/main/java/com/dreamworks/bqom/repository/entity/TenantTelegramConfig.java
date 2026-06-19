package com.dreamworks.bqom.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * JPA entity for {@code tenant_telegram_config}.
 *
 * <p><b>Credential fields:</b><br>
 * {@code botToken} and {@code chatId} are stored AES-256-GCM encrypted.
 * The entity intentionally does NOT perform encryption itself — that
 * responsibility lives in {@code TelegramConfigService} so the encryption
 * boundary is explicit and testable without a JPA context.
 *
 * <p><b>Relationship:</b><br>
 * One-to-one with {@code tenant} via {@code tenant_code} FK — consistent with
 * every other config table in the schema.
 */
@Entity
@Table(
    name = "tenant_telegram_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_telegram_cfg_tenant",
        columnNames = "tenant_code"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantTelegramConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * FK to {@code tenant(code)}.  Stored as a plain VARCHAR — not modelled
     * as a {@code @ManyToOne} to avoid lazy-loading surprises in async paths.
     */
    @Column(name = "tenant_code", nullable = false, updatable = false)
    private String tenantCode;

    /**
     * AES-256-GCM encrypted Telegram Bot Token.
     * Never log or expose this field in plaintext.
     */
    @Column(name = "bot_token", nullable = false, columnDefinition = "TEXT")
    private String botToken;           // stored encrypted

    /**
     * AES-256-GCM encrypted Telegram Chat ID (channel or group ID).
     * Never log or expose this field in plaintext.
     */
    @Column(name = "chat_id", nullable = false)
    private String chatId;             // stored encrypted

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
