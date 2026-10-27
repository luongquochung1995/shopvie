package com.shopvi.shipping.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.shipping.ShipmentStatus;
import com.shopvi.shipping.ShipmentView;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/** Vận đơn của một đơn hàng và hành trình vận chuyển. */
@Getter
@Entity
@Table(name = "shipments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment extends BaseEntity {

    public enum Provider { GHN, INTERNAL }

    @Column(name = "order_code", nullable = false, unique = true, updatable = false)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Provider provider;

    @Column(name = "tracking_code", nullable = false, unique = true, updatable = false)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private Long fee;

    @Column(name = "expected_delivery_at")
    private Instant expectedDeliveryAt;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC, id ASC")
    private List<ShipmentEvent> events = new ArrayList<>();

    public static Shipment create(String orderCode, Provider provider, String trackingCode, Long fee,
                                  Instant expectedDeliveryAt, String description, Instant now) {
        Shipment s = new Shipment();
        s.orderCode = orderCode;
        s.provider = provider;
        s.trackingCode = trackingCode;
        s.fee = fee;
        s.expectedDeliveryAt = expectedDeliveryAt;
        s.status = ShipmentStatus.CREATED;
        s.events.add(ShipmentEvent.of(s, ShipmentStatus.CREATED, null, description, now, null));
        return s;
    }

    /**
     * Ghi một bước hành trình. Bỏ qua bản tin trùng (cùng trạng thái gốc + thời điểm — webhook gửi lặp).
     *
     * @return true nếu trạng thái chuẩn hóa của vận đơn thay đổi
     */
    public boolean record(ShipmentStatus newStatus, String rawStatus, String description, Instant occurredAt,
                          Map<String, Object> raw) {
        boolean duplicate = events.stream().anyMatch(e -> e.getStatus() == newStatus
                && Objects.equals(e.getRawStatus(), rawStatus) && e.getOccurredAt().equals(occurredAt));
        if (duplicate || isFinal()) {
            return false;
        }
        events.add(ShipmentEvent.of(this, newStatus, rawStatus, description, occurredAt, raw));
        if (newStatus == status) {
            return false;
        }
        status = newStatus;
        return true;
    }

    public boolean isFinal() {
        return status == ShipmentStatus.DELIVERED || status == ShipmentStatus.RETURNED
                || status == ShipmentStatus.CANCELLED;
    }

    public ShipmentView toView() {
        return new ShipmentView(provider.name(), trackingCode, status, fee, expectedDeliveryAt, events.stream()
                .sorted(Comparator.comparing(ShipmentEvent::getOccurredAt).thenComparing(ShipmentEvent::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(e -> new ShipmentView.Event(e.getStatus(), e.getDescription(), e.getOccurredAt()))
                .toList());
    }
}
