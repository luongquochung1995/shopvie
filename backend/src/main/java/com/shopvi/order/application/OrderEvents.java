package com.shopvi.order.application;

import com.shopvi.order.OrderPaid;
import com.shopvi.order.OrderPlaced;
import com.shopvi.order.OrderStatusChanged;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Đổi trạng thái đơn + phát sự kiện tương ứng — mọi chỗ đổi trạng thái đi qua đây để không sót thông báo. */
@Component
@RequiredArgsConstructor
class OrderEvents {

    private final ApplicationEventPublisher events;

    void transition(Order order, OrderStatus next, Long changedBy, String note) {
        OrderStatus from = order.getStatus();
        order.transitionTo(next, changedBy, note);
        events.publishEvent(new OrderStatusChanged(order.getCode(), order.getUserId(), order.getShopId(), from.name(),
                next.name(), changedBy, note));
    }

    void placed(Order order) {
        events.publishEvent(new OrderPlaced(order.getCode(), order.getUserId(), order.getShopId(), order.getTotal(),
                order.getPaymentMethod().name()));
    }

    void paid(Order order) {
        events.publishEvent(new OrderPaid(order.getCode(), order.getUserId(), order.getShopId(), order.getTotal()));
    }
}
