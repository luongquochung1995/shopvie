package com.shopvi.order.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Thao tác seller được làm với đơn của shop và trạng thái đích tương ứng.
 * Thao tác khả dụng = trạng thái đích nằm trong các bước chuyển hợp lệ của {@link OrderStatus};
 * đơn online chưa thanh toán chưa xác nhận được.
 */
public enum SellerAction {
    CONFIRM(OrderStatus.CONFIRMED),
    SHIP(OrderStatus.SHIPPING),
    DELIVER(OrderStatus.DELIVERED),
    CANCEL(OrderStatus.CANCELLED);

    private final OrderStatus target;

    SellerAction(OrderStatus target) {
        this.target = target;
    }

    public OrderStatus target() {
        return target;
    }

    public static List<SellerAction> availableFor(OrderStatus status) {
        return availableFor(status, false);
    }

    public static List<SellerAction> availableFor(OrderStatus status, boolean awaitingPayment) {
        return Arrays.stream(values())
                .filter(a -> status.canTransitionTo(a.target))
                .filter(a -> !(awaitingPayment && a == CONFIRM))
                .toList();
    }

    public static List<SellerAction> availableFor(Order order) {
        return availableFor(order.getStatus(), order.isAwaitingPayment());
    }
}
