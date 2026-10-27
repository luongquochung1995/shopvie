package com.shopvi.shipping.infra;

import com.shopvi.shipping.ShipmentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Đọc bản tin webhook của GHN: {@code { "OrderCode", "ClientOrderCode", "Status", "Time", "Description", "Reason" }}.
 */
@Component
public class GhnWebhookParser {

    /** Empty nếu thiếu mã vận đơn hoặc trạng thái không làm đổi hành trình. */
    public Optional<Update> parse(Map<String, Object> body, Instant receivedAt) {
        Object orderCode = body.get("OrderCode");
        Object rawStatus = body.get("Status");
        if (orderCode == null || rawStatus == null) {
            return Optional.empty();
        }
        return GhnShippingProvider.mapStatus(rawStatus.toString()).map(status -> new Update(orderCode.toString(), status,
                rawStatus.toString(), description(body), GhnShippingProvider.parseTime(body.get("Time")).orElse(receivedAt)));
    }

    private static String description(Map<String, Object> body) {
        Object reason = body.get("Reason");
        Object description = body.get("Description");
        if (reason != null && !reason.toString().isBlank()) {
            return reason.toString();
        }
        return description == null ? null : description.toString();
    }

    /** @param trackingCode mã vận đơn GHN */
    public record Update(String trackingCode, ShipmentStatus status, String rawStatus, String description,
                         Instant occurredAt) {
    }
}
