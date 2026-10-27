package com.shopvi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderTest {

    private static final ShippingAddress ADDRESS = new ShippingAddress("A", "0901234567", "79", null, "26740", "1 Lê Lợi");

    private static Order newOrder() {
        return Order.place("code-1", UUID.randomUUID(), 7L, 3L, PaymentMethod.COD, ADDRESS, "giao giờ hành chính",
                30_000, List.of(
                        new OrderItem.Snapshot(1L, 10L, "Áo", "ao-1", "Đỏ, M", "img", 100_000, 2),
                        new OrderItem.Snapshot(2L, 11L, "Quần", "quan-2", null, null, 50_000, 1)));
    }

    @Test
    void placeComputesTotalsAndRecordsInitialHistory() {
        Order order = newOrder();

        assertThat(order.getSubtotal()).isEqualTo(250_000);
        assertThat(order.getTotal()).isEqualTo(280_000);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getHistory()).singleElement()
                .satisfies(h -> {
                    assertThat(h.getFromStatus()).isNull();
                    assertThat(h.getToStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(h.getChangedBy()).isEqualTo(7L);
                });
    }

    @Test
    void validTransitionAppendsHistory() {
        Order order = newOrder();

        order.transitionTo(OrderStatus.CONFIRMED, 99L, null);
        order.transitionTo(OrderStatus.CANCELLED, 7L, "đổi ý");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getHistory()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(order.getHistory().get(2).getNote()).isEqualTo("đổi ý");
    }

    @Test
    void invalidTransitionIsRejectedWithoutHistory() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED, 1L, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getHistory()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, CONFIRMED, true", "PENDING, CANCELLED, true", "PENDING, SHIPPING, false",
            "CONFIRMED, SHIPPING, true", "CONFIRMED, CANCELLED, true", "SHIPPING, CANCELLED, false",
            "SHIPPING, DELIVERED, true", "DELIVERED, COMPLETED, true", "DELIVERED, RETURNED, true",
            "COMPLETED, RETURNED, false", "CANCELLED, PENDING, false", "RETURNED, COMPLETED, false"})
    void stateMachine(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlineOrderCannotBeConfirmedBeforePaymentAndIsRefundedWhenCancelledAfterPaying() {
        Order order = Order.place("code-2", UUID.randomUUID(), 7L, 3L, PaymentMethod.VNPAY, ADDRESS, null, 30_000,
                List.of(new OrderItem.Snapshot(1L, 10L, "Áo", "ao-1", null, null, 100_000, 1)));

        assertThat(order.isAwaitingPayment()).isTrue();
        assertThat(SellerAction.availableFor(order)).containsExactly(SellerAction.CANCEL);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED, 9L, null))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_AWAITING_PAYMENT);

        order.markPaid();
        assertThat(order.isAwaitingPayment()).isFalse();
        order.transitionTo(OrderStatus.CONFIRMED, 9L, null);
        order.transitionTo(OrderStatus.CANCELLED, 7L, "đổi ý");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void buyerCanCancelOnlyBeforeShipping() {
        assertThat(OrderStatus.PENDING.isCancellableByBuyer()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isCancellableByBuyer()).isTrue();
        assertThat(OrderStatus.SHIPPING.isCancellableByBuyer()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellableByBuyer()).isFalse();
    }
}
