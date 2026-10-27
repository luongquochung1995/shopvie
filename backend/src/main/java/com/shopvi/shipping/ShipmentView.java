package com.shopvi.shipping;

import java.time.Instant;
import java.util.List;

/**
 * Vận đơn và hành trình (mới nhất trước).
 *
 * @param provider GHN | INTERNAL
 */
public record ShipmentView(String provider, String trackingCode, ShipmentStatus status, Long fee,
                           Instant expectedDeliveryAt, List<Event> events) {

    public record Event(ShipmentStatus status, String description, Instant occurredAt) {
    }
}
