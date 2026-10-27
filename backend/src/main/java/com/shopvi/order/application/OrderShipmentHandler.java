package com.shopvi.order.application;

import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.shipping.ShipmentProgressed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Đồng bộ trạng thái đơn theo hãng vận chuyển (webhook): đã lấy/đang giao → SHIPPING, giao thành công → DELIVERED.
 * Bỏ qua bước không hợp lệ (vd. seller đã tự cập nhật trước) để webhook không lỗi và không bị gửi lại mãi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderShipmentHandler {

    private final OrderRepository orderRepository;
    private final OrderEvents orderEvents;

    @EventListener
    public void on(ShipmentProgressed event) {
        orderRepository.findByCodeForUpdate(event.orderCode()).ifPresent(order -> {
            switch (event.status()) {
                case PICKED_UP, IN_TRANSIT -> advance(order, OrderStatus.SHIPPING, event.description());
                case DELIVERED -> {
                    advance(order, OrderStatus.SHIPPING, null);
                    advance(order, OrderStatus.DELIVERED, event.description());
                }
                default -> log.info("Shipment of order {} is now {}", order.getCode(), event.status());
            }
        });
    }

    private void advance(Order order, OrderStatus next, String note) {
        if (order.getStatus().canTransitionTo(next)) {
            orderEvents.transition(order, next, null, note);
        }
    }
}
