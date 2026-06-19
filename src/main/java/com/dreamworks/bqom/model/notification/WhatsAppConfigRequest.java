package com.dreamworks.bqom.model.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for creating or updating a tenant's WhatsApp Business config.
 *
 * <h2>Field validation</h2>
 * <ul>
 *   <li>{@code phoneNumberId} — must be a non-blank numeric string (Meta resource ID).</li>
 *   <li>{@code wabaId} — must be a non-blank numeric string (WABA account ID).</li>
 *   <li>{@code accessToken} — must be non-blank; minimum 20 chars to guard against
 *       accidentally submitting a truncated token. No maximum enforced here because
 *       Meta token lengths vary; the DB column is TEXT.</li>
 *   <li>{@code businessPhoneNumber} — validated as E.164 format ({@code +} followed
 *       by 7–15 digits) per ITU-T E.164 standard.</li>
 * </ul>
 *
 * <h2>isActive</h2>
 * <p>The {@code isActive} field from the Postman payload is intentionally
 * ignored. Active state is always set to {@code true} on upsert and is
 * managed exclusively via {@code DELETE /{code}/whatsapp-config}.
 * This is consistent with the Telegram config pattern and prevents callers
 * from accidentally creating a permanently inactive config.
 *
 * <h2>Security</h2>
 * <p>Plaintext credentials are accepted here and encrypted at the service
 * layer ({@link com.dreamworks.bqom.service.WhatsAppConfigService}) before
 * persistence. Only {@code accessToken} is encrypted — the other fields are
 * non-secret resource identifiers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppConfigRequest {

    /**
     * Meta WhatsApp Phone Number ID — numeric string identifying the
     * specific phone number registered under the WABA.
     * Example: {@code "1178994555292278"}
     */
    @NotBlank(message = "phoneNumberId must not be blank")
    @Pattern(regexp = "\\d+", message = "phoneNumberId must be a numeric string")
    @Size(max = 100, message = "phoneNumberId must not exceed 100 characters")
    @JsonProperty("phoneNumberId")
    private String phoneNumberId;

    /**
     * WhatsApp Business Account (WABA) ID.
     * Example: {@code "1026858233135309"}
     */
    @NotBlank(message = "wabaId must not be blank")
    @Pattern(regexp = "\\d+", message = "wabaId must be a numeric string")
    @Size(max = 100, message = "wabaId must not exceed 100 characters")
    @JsonProperty("wabaId")
    private String wabaId;

    /**
     * Meta System User access token — bearer token for the WhatsApp Cloud API.
     * This is the only field encrypted before persistence.
     * Minimum length of 20 guards against accidentally submitting a truncated value.
     */
    @NotBlank(message = "accessToken must not be blank")
    @Size(min = 20, message = "accessToken appears too short — minimum 20 characters")
    @JsonProperty("accessToken")
    private String accessToken;

    /**
     * E.164-formatted business phone number.
     * Example: {@code "+15556580205"}
     */
    @NotBlank(message = "businessPhoneNumber must not be blank")
    @Pattern(
            regexp = "\\+[1-9]\\d{6,14}",
            message = "businessPhoneNumber must be in E.164 format (e.g. +15556580205)"
    )
    @JsonProperty("businessPhoneNumber")
    private String businessPhoneNumber;

    // isActive is intentionally omitted — upsert always sets active = true.
    // Deactivation is handled via DELETE /{code}/whatsapp-config.
}