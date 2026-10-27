package com.shopvi.order.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Đơn hàng của một shop. Mọi thay đổi trạng thái đi qua {@link #transitionTo} để kiểm tra state machine
 * và ghi {@code order_status_history}.
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private long subtotal;

    @Column(name = "shipping_fee", nullable = false, updatable = false)
    private long shippingFee;

    @Column(nullable = false, updatable = false)
    private long discount;

    @Column(nullable = false, updatable = false)
    private long total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, updatable = false)
    private ShippingAddress shippingAddress;

    @Column(updatable = false)
    private String note;

    @Column(name = "checkout_id", nullable = false, updatable = false)
    private UUID checkoutId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<OrderVoucher> vouchers = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderStatusHistory> history = new ArrayList<>();

    /** Tạo đơn PENDING (chưa thanh toán) và ghi lịch sử khởi tạo. */
    public static Order place(String code, UUID checkoutId, Long userId, Long shopId, PaymentMethod paymentMethod,
                              ShippingAddress address, String note, long shippingFee, List<OrderItem.Snapshot> lines) {
        return place(code, checkoutId, userId, shopId, paymentMethod, address, note, shippingFee, List.of(), lines);
    }

    /** Như trên, kèm voucher đã áp (tổng giảm không vượt quá tiền hàng + phí ship). */
    public static Order place(String code, UUID checkoutId, Long userId, Long shopId, PaymentMethod paymentMethod,
                              ShippingAddress address, String note, long shippingFee, List<OrderVoucher> vouchers,
                              List<OrderItem.Snapshot> lines) {
        Order order = new Order();
        order.code = code;
        order.checkoutId = checkoutId;
        order.userId = userId;
        order.shopId = shopId;
        order.paymentMethod = paymentMethod;
        order.paymentStatus = PaymentStatus.UNPAID;
        order.shippingAddress = address;
        order.note = note;
        lines.forEach(l -> order.items.add(OrderItem.of(order, l)));
        order.subtotal = order.items.stream().mapToLong(OrderItem::lineTotal).sum();
        order.shippingFee = shippingFee;
        order.vouchers = new ArrayList<>(vouchers);
        order.discount = Math.min(vouchers.stream().mapToLong(OrderVoucher::amount).sum(),
                order.subtotal + order.shippingFee);
        order.total = order.subtotal + order.shippingFee - order.discount;
        order.status = OrderStatus.PENDING;
        order.history.add(OrderStatusHistory.of(order, null, OrderStatus.PENDING, userId, null));
        return order;
    }

    public void transitionTo(OrderStatus next, Long changedBy, String note) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Cannot change order status from %s to %s".formatted(status, next));
        }
        // Đơn thanh toán online chỉ được xác nhận sau khi cổng báo đã thanh toán
        if (next == OrderStatus.CONFIRMED && isAwaitingPayment()) {
            throw new BusinessException(ErrorCode.ORDER_AWAITING_PAYMENT);
        }
        history.add(OrderStatusHistory.of(this, status, next, changedBy, note));
        status = next;
        // COD: người mua trả tiền cho shipper khi nhận hàng
        if (next == OrderStatus.DELIVERED && paymentMethod == PaymentMethod.COD) {
            paymentStatus = PaymentStatus.PAID;
        }
        // Đơn đã thanh toán online bị hủy → hoàn tiền (đối soát với cổng thanh toán, xem Backlog)
        if (next == OrderStatus.CANCELLED && paymentMethod != PaymentMethod.COD && paymentStatus == PaymentStatus.PAID) {
            paymentStatus = PaymentStatus.REFUNDED;
        }
    }

    /** Cổng thanh toán báo checkout của đơn đã được thanh toán. */
    public void markPaid() {
        if (paymentStatus == PaymentStatus.UNPAID) {
            paymentStatus = PaymentStatus.PAID;
        }
    }

    /** Đơn thanh toán online đang chờ người mua thanh toán (chưa xác nhận được). */
    public boolean isAwaitingPayment() {
        return status == OrderStatus.PENDING && paymentMethod != PaymentMethod.COD
                && paymentStatus == PaymentStatus.UNPAID;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<OrderStatusHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
