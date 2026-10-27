package com.shopvi.order.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "order_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;

    @Column(updatable = false)
    private String note;

    /** Id user thực hiện; null = hệ thống. */
    @Column(name = "changed_by", updatable = false)
    private Long changedBy;

    static OrderStatusHistory of(Order order, OrderStatus from, OrderStatus to, Long changedBy, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.order = order;
        h.fromStatus = from;
        h.toStatus = to;
        h.changedBy = changedBy;
        h.note = note;
        return h;
    }
}
