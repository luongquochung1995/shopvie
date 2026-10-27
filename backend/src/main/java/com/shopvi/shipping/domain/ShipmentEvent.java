package com.shopvi.shipping.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.shipping.ShipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một bước trong hành trình vận chuyển. */
@Getter
@Entity
@Table(name = "shipment_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ShipmentStatus status;

    @Column(name = "raw_status", updatable = false)
    private String rawStatus;

    @Column(updatable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", updatable = false)
    private Map<String, Object> rawPayload;

    static ShipmentEvent of(Shipment shipment, ShipmentStatus status, String rawStatus, String description,
                            Instant occurredAt, Map<String, Object> raw) {
        ShipmentEvent e = new ShipmentEvent();
        e.shipment = shipment;
        e.status = status;
        e.rawStatus = rawStatus;
        e.description = description == null ? null : description.substring(0, Math.min(255, description.length()));
        e.occurredAt = occurredAt;
        e.rawPayload = raw;
        return e;
    }
}
