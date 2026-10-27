package com.shopvi.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái đơn hàng và các bước chuyển hợp lệ (state machine).
 * <pre>
 * PENDING ──► CONFIRMED ──► SHIPPING ──► DELIVERED ──► COMPLETED
 *    │            │                          │
 *    └──► CANCELLED ◄┘                        └──► RETURNED
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    RETURNED;

    public Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(SHIPPING, CANCELLED);
            case SHIPPING -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(COMPLETED, RETURNED);
            case COMPLETED, CANCELLED, RETURNED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return nextStatuses().contains(next);
    }

    /** Người mua chỉ tự hủy được khi shop chưa giao cho vận chuyển. */
    public boolean isCancellableByBuyer() {
        return canTransitionTo(CANCELLED);
    }
}
