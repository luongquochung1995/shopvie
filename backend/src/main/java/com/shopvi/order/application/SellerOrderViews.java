package com.shopvi.order.application;

import com.shopvi.order.application.OrderViews.HistoryView;
import com.shopvi.order.application.OrderViews.ItemView;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.order.domain.PaymentStatus;
import com.shopvi.order.domain.SellerAction;
import com.shopvi.order.domain.ShippingAddress;
import com.shopvi.shipping.ShipmentView;
import java.time.Instant;
import java.util.List;

/** Dữ liệu đọc của đơn hàng cho seller (đơn của shop mình). */
public final class SellerOrderViews {

    private SellerOrderViews() {
    }

    /**
     * @param actions         thao tác seller làm được ở trạng thái hiện tại
     * @param awaitingPayment đơn online người mua chưa thanh toán (chưa xác nhận được)
     */
    public record SellerOrderSummary(String code, OrderStatus status, List<ItemView> items, long subtotal,
                                     long shippingFee, long discount, long total, PaymentMethod paymentMethod,
                                     PaymentStatus paymentStatus, boolean awaitingPayment, String receiverName,
                                     String note, List<SellerAction> actions, Instant createdAt) {
        static SellerOrderSummary of(Order o) {
            return new SellerOrderSummary(o.getCode(), o.getStatus(), o.getItems().stream().map(ItemView::of).toList(),
                    o.getSubtotal(), o.getShippingFee(), o.getDiscount(), o.getTotal(), o.getPaymentMethod(),
                    o.getPaymentStatus(), o.isAwaitingPayment(), o.getShippingAddress().receiverName(), o.getNote(),
                    SellerAction.availableFor(o), o.getCreatedAt());
        }
    }

    /** @param shipment vận đơn (null khi chưa xác nhận đơn) */
    public record SellerOrderDetail(String code, OrderStatus status, List<ItemView> items, long subtotal,
                                    long shippingFee, long discount, long total, PaymentMethod paymentMethod,
                                    PaymentStatus paymentStatus, boolean awaitingPayment,
                                    ShippingAddress shippingAddress, String note, List<HistoryView> history,
                                    List<SellerAction> actions, ShipmentView shipment, Instant createdAt,
                                    Instant updatedAt) {
        static SellerOrderDetail of(Order o, ShipmentView shipment) {
            return new SellerOrderDetail(o.getCode(), o.getStatus(), o.getItems().stream().map(ItemView::of).toList(),
                    o.getSubtotal(), o.getShippingFee(), o.getDiscount(), o.getTotal(), o.getPaymentMethod(),
                    o.getPaymentStatus(), o.isAwaitingPayment(), o.getShippingAddress(), o.getNote(),
                    o.getHistory().stream().map(HistoryView::of).toList(), SellerAction.availableFor(o), shipment,
                    o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
