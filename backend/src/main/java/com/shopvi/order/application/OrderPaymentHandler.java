package com.shopvi.order.application;

import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.payment.PaymentApi;
import com.shopvi.payment.PaymentSucceeded;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Đơn hàng theo kết quả thanh toán online: đánh dấu đã thanh toán khi cổng báo thành công (cùng transaction xác nhận
 * IPN), hủy + hoàn kho các đơn quá hạn thanh toán (P4-3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentHandler {

    static final String EXPIRED_NOTE = "Quá hạn thanh toán";
    private static final int BATCH = 100;

    private final OrderRepository orderRepository;
    private final OrderStockReleaser stockReleaser;
    private final PaymentApi paymentApi;
    private final TransactionTemplate tx;
    private final Clock clock;

    @EventListener
    public void on(PaymentSucceeded event) {
        for (Order order : orderRepository.findByCheckoutIdForUpdate(event.checkoutId())) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // Tiền về sau khi đơn đã bị hủy (hiếm: cổng có hạn thanh toán) → cần hoàn tiền cho người mua
                log.error("Payment {} received for cancelled order {} — refund required", event.providerTxnId(),
                        order.getCode());
            }
            order.markPaid();
        }
    }

    /**
     * Hủy các đơn online chưa thanh toán quá {@code paymentTtl}: mỗi checkout một transaction, đơn đang bị khóa
     * (vd. IPN đang xác nhận) được bỏ qua tới lượt sau.
     *
     * @return số đơn đã hủy
     */
    public int expireOverdue() {
        Instant cutoff = clock.instant().minus(paymentApi.paymentTtl());
        int cancelled = 0;
        for (UUID checkoutId : orderRepository.findOverdueUnpaidCheckouts(cutoff, BATCH)) {
            Integer n = tx.execute(status -> expireCheckout(checkoutId, cutoff));
            cancelled += n == null ? 0 : n;
        }
        if (cancelled > 0) {
            log.info("Cancelled {} unpaid orders past the payment deadline", cancelled);
        }
        return cancelled;
    }

    private int expireCheckout(UUID checkoutId, Instant cutoff) {
        List<Order> orders = orderRepository.findByCheckoutIdForUpdateSkipLocked(checkoutId);
        int cancelled = 0;
        for (Order order : orders) {
            if (order.isAwaitingPayment() && order.getCreatedAt().isBefore(cutoff)) {
                order.transitionTo(OrderStatus.CANCELLED, null, EXPIRED_NOTE);
                stockReleaser.release(order);
                cancelled++;
            }
        }
        if (cancelled > 0) {
            paymentApi.expire(checkoutId);
        }
        return cancelled;
    }
}
