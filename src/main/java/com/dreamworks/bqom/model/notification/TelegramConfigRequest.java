package com.dreamworks.bqom.model.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for creating / updating a tenant's Telegram configuration.
 *
 * <p>Plaintext credentials are accepted here and encrypted at the service
 * layer before persistence — they are never returned in responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramConfigRequest {

    @NotBlank(message = "bot_token must not be blank")
    private String botToken;

    @NotBlank(message = "chat_id must not be blank")
    private String chatId;
}