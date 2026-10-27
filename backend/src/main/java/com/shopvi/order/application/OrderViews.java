package com.shopvi.order.application;

import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderItem;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.domain.OrderStatusHistory;
import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.order.domain.PaymentStatus;
import com.shopvi.order.domain.ShippingAddress;
import com.shopvi.shipping.ShipmentView;
import com.shopvi.shop.ShopSummary;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Dữ liệu đọc của đơn hàng cho người mua. */
public final class OrderViews {

    private OrderViews() {
    }

    public record ItemView(Long variantId, Long productId, String productName, String productSlug, String variantName,
                           String imageUrl, long unitPrice, int quantity, long lineTotal) {
        static ItemView of(OrderItem i) {
            return new ItemView(i.getVariantId(), i.getProductId(), i.getProductName(), i.getProductSlug(),
                    i.getVariantName(), i.getImageUrl(), i.getUnitPrice(), i.getQuantity(), i.lineTotal());
        }
    }

    /**
     * @param awaitingPayment  đơn online chưa thanh toán (người mua có thể thanh toán lại tới {@code paymentExpiresAt})
     * @param paymentExpiresAt hạn thanh toán; null nếu không chờ thanh toán
     */
    public record OrderSummary(String code, UUID checkoutId, OrderStatus status, ShopSummary shop, List<ItemView> items,
                               long subtotal, long shippingFee, long discount, long total, PaymentMethod paymentMethod,
                               PaymentStatus paymentStatus, boolean cancellable, boolean awaitingPayment,
                               Instant paymentExpiresAt, Instant createdAt) {
        static OrderSummary of(Order o, ShopSummary shop, Duration paymentTtl) {
            return new OrderSummary(o.getCode(), o.getCheckoutId(), o.getStatus(), shop,
                    o.getItems().stream().map(ItemView::of).toList(), o.getSubtotal(), o.getShippingFee(),
                    o.getDiscount(), o.getTotal(), o.getPaymentMethod(), o.getPaymentStatus(),
                    o.getStatus().isCancellableByBuyer(), o.isAwaitingPayment(), expiryOf(o, paymentTtl),
                    o.getCreatedAt());
        }
    }

    public record HistoryView(OrderStatus fromStatus, OrderStatus toStatus, String note, Instant createdAt) {
        static HistoryView of(OrderStatusHistory h) {
            return new HistoryView(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getCreatedAt());
        }
    }

    /** @param shipment vận đơn và hành trình (null khi shop chưa xác nhận đơn) */
    public record OrderDetail(String code, UUID checkoutId, OrderStatus status, ShopSummary shop, List<ItemView> items,
                              long subtotal, long shippingFee, long discount, long total, PaymentMethod paymentMethod,
                              PaymentStatus paymentStatus, boolean cancellable, boolean awaitingPayment,
                              Instant paymentExpiresAt, ShippingAddress shippingAddress, String note,
                              List<HistoryView> history, ShipmentView shipment, Instant createdAt, Instant updatedAt) {
        static OrderDetail of(Order o, ShopSummary shop, Duration paymentTtl, ShipmentView shipment) {
            return new OrderDetail(o.getCode(), o.getCheckoutId(), o.getStatus(), shop,
                    o.getItems().stream().map(ItemView::of).toList(), o.getSubtotal(), o.getShippingFee(),
                    o.getDiscount(), o.getTotal(), o.getPaymentMethod(), o.getPaymentStatus(),
                    o.getStatus().isCancellableByBuyer(), o.isAwaitingPayment(), expiryOf(o, paymentTtl),
                    o.getShippingAddress(), o.getNote(), o.getHistory().stream().map(HistoryView::of).toList(),
                    shipment, o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    static Instant expiryOf(Order o, Duration paymentTtl) {
        return o.isAwaitingPayment() && o.getCreatedAt() != null
                ? o.getCreatedAt().truncatedTo(ChronoUnit.MICROS).plus(paymentTtl) : null;
    }
}
