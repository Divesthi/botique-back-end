package com.dreamworks.bqom.model.notification;

import com.dreamworks.bqom.model.customer.CustomerDetailsModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Immutable notification payload passed between the dispatcher and strategies.
 *
 * <p>Keeping the payload as a dedicated value object (rather than raw strings)
 * makes it easy to add metadata (e.g., priority, template ID) in the future
 * without changing any strategy signature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /** The tenant this notification belongs to. */
    private String tenantCode;
    // Parameters using which the message will be sent based on the channel
    Map<String, String> parameters;
    private String toPhoneNumber;
}